using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using System.Text;
using OpenAirBookLink.Core.NavData;
using OpenAirBookLink.Core.Protocol;
using SimConnect.NET;
using SimConnect.NET.Events;

namespace OpenAirBookLink.Msfs;

// Dane nawigacyjne z bazy MSFS 2024 przez SimConnect Facilities API
// (SimConnect_AddToFacilityDefinition + SimConnect_RequestFacilityData[_EX1]).
// Odpowiedzi przychodzą jako surowe wiadomości FACILITY_DATA (każdy obiekt drzewa osobno, z ID rodzica),
// zakończone FACILITY_DATA_END. Pola w Data są upakowane w kolejności z definicji, bez wyrównania.
public sealed class MsfsNavData : INavDataProvider
{
    // Własne zakresy ID, żeby nie kolidować z definicjami SimVarów biblioteki.
    private const uint DefAirport = 0x4F41_0001;
    private const uint DefProcedures = 0x4F41_0002;
    private const uint DefVor = 0x4F41_0003;
    private const uint DefNdb = 0x4F41_0004;
    private const uint DefWaypoint = 0x4F41_0005;
    private const uint DefAirportPos = 0x4F41_0006;
    private const uint FirstRequestId = 0x4F42_0000;

    private const int FacilityDataHeader = 40; // SIMCONNECT_RECV (12) + 7 × DWORD
    private const int MinimalListHeader = 28;  // SIMCONNECT_RECV_LIST_TEMPLATE
    private static readonly TimeSpan RequestTimeout = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan LookupTimeout = TimeSpan.FromSeconds(4);

    private readonly object _nativeLock = new();
    private readonly ConcurrentDictionary<uint, Pending> _pending = new();
    private readonly ConcurrentDictionary<uint, uint> _packetToRequest = new();
    private readonly ConcurrentDictionary<uint, uint> _earlyExceptions = new();
    private readonly ConcurrentDictionary<uint, ListPending> _lists = new();
    private static readonly TimeSpan ListTimeout = TimeSpan.FromSeconds(5);
    private SimConnectClient? _client;
    private uint _nextRequest = FirstRequestId;

    public event Action<string>? Log;

    // Wywoływane po połączeniu z MSFS (na nowym kliencie definicje trzeba utworzyć od nowa).
    public void Attach(SimConnectClient client)
    {
        client.RawMessageReceived += OnRawMessage;
        lock (_nativeLock)
        {
            DefineAll(client.Handle);
            _client = client;
        }
    }

    public void Detach()
    {
        SimConnectClient? client;
        lock (_nativeLock)
        {
            client = _client;
            _client = null;
        }
        if (client != null) client.RawMessageReceived -= OnRawMessage;
        foreach (var p in _pending.Values) p.Fail(new NavDataException(ErrorCodes.SimUnavailable, "MSFS rozłączony"));
        _pending.Clear();
        _packetToRequest.Clear();
    }

    // ---------- INavDataProvider ----------

    public async Task<AirportInfo> GetAirportAsync(string icao, CancellationToken ct)
    {
        var reply = await RequestAsync(DefAirport, icao, "", null, RequestTimeout, ct);
        var root = reply.Root(SimConnectFacilityDataType.Airport) ?? throw NotFound($"Brak lotniska {icao} w bazie MSFS");

        var c = root.Cursor();
        var lat = c.F64();
        var lon = c.F64();
        var altM = c.F64();
        var name = c.Str(64);
        var ident = c.Str(8);

        var runways = new List<RunwayInfo>();
        var ilsRefs = new List<(int Index, string Icao, string Region)>();
        foreach (var rw in root.Children(SimConnectFacilityDataType.Runway))
        {
            var r = rw.Cursor();
            var cLat = r.F64();
            var cLon = r.F64();
            var cAltM = r.F64();
            var heading = r.F32();
            var lengthM = r.F32();
            var widthM = r.F32();
            var pIls = r.Str(8);
            var pIlsRegion = r.Str(8);
            var pNum = r.I32();
            var pDes = r.I32();
            var sIls = r.Str(8);
            var sIlsRegion = r.Str(8);
            var sNum = r.I32();
            var sDes = r.I32();

            // Próg = koniec pasa wyliczony ze środka, kursu i długości (bez przesuniętego progu).
            void AddEnd(int num, int des, double hdg, string ils, string ilsRegion)
            {
                var ident = NavMath.RunwayIdent(num, des);
                if (ident.Length == 0) return;
                var (tLat, tLon) = NavMath.Offset(cLat, cLon, hdg + 180, lengthM / 2);
                if (ils.Length > 0) ilsRefs.Add((runways.Count, ils, ilsRegion));
                runways.Add(new RunwayInfo
                {
                    Ident = ident, HeadingTrueDeg = NavMath.Round(NavMath.NormDeg(hdg), 1),
                    LengthFt = NavMath.Round(lengthM * NavMath.FtPerM, 0), WidthFt = NavMath.Round(widthM * NavMath.FtPerM, 0),
                    ThresholdLat = NavMath.Round(tLat, 6), ThresholdLon = NavMath.Round(tLon, 6),
                    ElevFt = NavMath.Round(cAltM * NavMath.FtPerM, 0),
                });
            }

            AddEnd(pNum, pDes, heading, pIls, pIlsRegion);
            AddEnd(sNum, sDes, heading + 180, sIls, sIlsRegion);
        }

        var frequencies = new List<FrequencyInfo>();
        foreach (var fr in root.Children(SimConnectFacilityDataType.Frequency))
        {
            var f = fr.Cursor();
            var type = f.I32();
            var hz = f.I32();
            var fname = f.Str(64);
            if (hz <= 0) continue;
            frequencies.Add(new FrequencyInfo { Type = FrequencyType(type), Name = fname, Mhz = NavMath.Round(hz / 1_000_000.0, 3) });
        }

        // ILS: obiekt VOR (lokalizer) wskazany przez PRIMARY/SECONDARY_ILS_ICAO końca pasa.
        var ilsTasks = ilsRefs.Select(async x => (x.Index, Ils: await TryGetIlsAsync(x.Icao, x.Region, ct))).ToList();
        foreach (var (index, ils) in await Task.WhenAll(ilsTasks))
        {
            if (ils == null) continue;
            var r = runways[index];
            runways[index] = new RunwayInfo
            {
                Ident = r.Ident, HeadingTrueDeg = r.HeadingTrueDeg, LengthFt = r.LengthFt, WidthFt = r.WidthFt,
                ThresholdLat = r.ThresholdLat, ThresholdLon = r.ThresholdLon, ElevFt = r.ElevFt, Ils = ils,
            };
        }

        return new AirportInfo
        {
            Icao = ident.Length > 0 ? ident : icao, Name = name,
            Lat = NavMath.Round(lat, 6), Lon = NavMath.Round(lon, 6), ElevFt = NavMath.Round(altM * NavMath.FtPerM, 0),
            Runways = runways, Frequencies = frequencies,
        };
    }

    public async Task<AirportProcedures> GetProceduresAsync(string icao, CancellationToken ct)
    {
        var reply = await RequestAsync(DefProcedures, icao, "", null, RequestTimeout, ct);
        var root = reply.Root(SimConnectFacilityDataType.Airport) ?? throw NotFound($"Brak lotniska {icao} w bazie MSFS");
        var resolver = new FixResolver(this, ct);

        var result = new AirportProcedures { Icao = root.Cursor().Str(8) is { Length: > 0 } id ? id : icao };

        foreach (var (kind, list) in new[] { (SimConnectFacilityDataType.Departure, result.Departures), (SimConnectFacilityDataType.Arrival, result.Arrivals) })
        {
            foreach (var p in root.Children(kind))
            {
                var proc = new TerminalProcedure { Name = p.Cursor().Str(8) };
                foreach (var rt in p.Children(SimConnectFacilityDataType.RunwayTransition))
                {
                    var c = rt.Cursor();
                    var ident = NavMath.RunwayIdent(c.I32(), c.I32());
                    proc.RunwayTransitions.Add(new NamedLegs(ident.Length > 0 ? ident : "ALL", await Legs(rt, resolver)));
                }
                foreach (var et in p.Children(SimConnectFacilityDataType.EnrouteTransition))
                    proc.EnrouteTransitions.Add(new NamedLegs(et.Cursor().Str(8), await Legs(et, resolver)));
                proc.CommonLegs.AddRange(await Legs(p, resolver));
                list.Add(proc);
            }
        }

        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var a in root.Children(SimConnectFacilityDataType.Approach))
        {
            var c = a.Cursor();
            var type = c.I32();
            var suffixCode = c.I32();
            var runway = NavMath.RunwayIdent(c.I32(), c.I32());
            var suffix = suffixCode is > 32 and < 127 ? ((char)suffixCode).ToString() : null;
            var typeName = ApproachType(type);
            var label = ApproachLabel(type);
            var name = $"{label}{(suffix != null ? " " + suffix : "")}{(runway.Length > 0 ? " " + runway : "")}";
            for (var n = 2; !names.Add(name); n++) name = $"{label}{(suffix != null ? " " + suffix : "")} {runway} ({n})".Replace("  ", " ");

            var approach = new ApproachProcedure { Name = name, Type = typeName, Runway = runway.Length > 0 ? runway : null, Suffix = suffix };
            foreach (var t in a.Children(SimConnectFacilityDataType.ApproachTransition))
            {
                var tc = t.Cursor();
                var tName = tc.Str(8);
                var iaf = tc.Str(8);
                approach.Transitions.Add(new NamedLegs(tName.Length > 0 ? tName : iaf, await Legs(t, resolver)));
            }
            approach.FinalLegs.AddRange(await Legs(a, resolver, SimConnectFacilityDataType.FinalApproachLeg));
            approach.MissedLegs.AddRange(await Legs(a, resolver, SimConnectFacilityDataType.MissedApproachLeg));
            result.Approaches.Add(approach);
        }

        return result;
    }

    public async Task<IReadOnlyList<FixResult>> FindFixAsync(string ident, CancellationToken ct)
    {
        var lookups = new[]
        {
            LookupAsync(ident, null, 'W', ct),
            LookupAsync(ident, null, 'V', ct),
            LookupAsync(ident, null, 'N', ct),
            LookupAirportAsync(ident, ct),
        };
        var all = (await Task.WhenAll(lookups)).SelectMany(x => x).ToList();
        // Ten sam obiekt bywa zwrócony przez dwa zapytania (np. VOR i waypoint w tym samym miejscu).
        return all.GroupBy(f => (f.Type, f.Region, Math.Round(f.Lat, 3), Math.Round(f.Lon, 3))).Select(g => g.First()).ToList();
    }

    // Lotniska, punkty trasy, NDB i VOR z „bańki rzeczywistości” MSFS wokół samolotu
    // (SimConnect_RequestFacilitiesList_EX1). Filtr promienia i limit robi NavDataService.
    public async Task<IReadOnlyList<FixResult>> NearbyAsync(double lat, double lon, double radiusNm, CancellationToken ct)
    {
        var kinds = new[] { (0, FixTypes.Airport), (1, FixTypes.Waypoint), (2, FixTypes.Ndb), (3, FixTypes.Vor) };
        var lists = await Task.WhenAll(kinds.Select(k => RequestListAsync(k.Item1, k.Item2, ct)));
        return lists.SelectMany(x => x).ToList();
    }

    private async Task<List<FixResult>> RequestListAsync(int listType, string fixType, CancellationToken ct)
    {
        var pending = new ListPending(fixType);
        uint requestId;
        lock (_nativeLock)
        {
            if (_client is not { IsConnected: true } client) throw new NavDataException(ErrorCodes.SimUnavailable, "Brak połączenia z MSFS");
            requestId = _nextRequest++;
            if (_nextRequest == 0) _nextRequest = FirstRequestId;
            _lists[requestId] = pending;
            var hr = Native.SimConnect_RequestFacilitiesList_EX1(client.Handle, listType, requestId);
            if (hr < 0)
            {
                _lists.TryRemove(requestId, out _);
                return new();
            }
        }
        try
        {
            return await pending.Task.WaitAsync(ListTimeout, ct);
        }
        catch (TimeoutException)
        {
            // Pusta bańka albo MSFS nie odpowiedział — zwróć to, co przyszło
            return pending.Snapshot();
        }
        finally
        {
            _lists.TryRemove(requestId, out _);
        }
    }

    // Wpis listy: Ident[n] + Region[3] + lat/lon/alt (3 × double), potem pola zależne od typu
    // (waypoint: magvar; NDB: + częstotliwość; VOR: + flagi, lokalizator, ścieżka). Długość identyfikatora
    // różni się między wersjami SDK, więc wyznaczamy ją z rozmiaru wpisu i znanego ogona danego typu.
    private static List<FixResult> ParseList(byte[] data, int count, string fixType)
    {
        var result = new List<FixResult>();
        if (count <= 0) return result;
        var entry = data.Length / count;
        var tail = fixType switch { FixTypes.Airport => 0, FixTypes.Waypoint => 4, FixTypes.Ndb => 8, _ => 44 };
        var identLen = entry - 3 - 24 - tail;
        if (identLen is < 4 or > 16) return result;
        for (var i = 0; i < count; i++)
        {
            var o = i * entry;
            var ident = Ascii(data, o, identLen);
            var region = Ascii(data, o + identLen, 3);
            var lat = BitConverter.ToDouble(data, o + identLen + 3);
            var lon = BitConverter.ToDouble(data, o + identLen + 11);
            double? mhz = null;
            if (fixType is FixTypes.Ndb or FixTypes.Vor) mhz = FreqMhz(BitConverter.ToUInt32(data, o + identLen + 3 + 24 + 4));
            if (ident.Length == 0 || (lat == 0 && lon == 0)) continue;
            result.Add(new FixResult
            {
                Ident = ident, Type = fixType, Lat = NavMath.Round(lat, 6), Lon = NavMath.Round(lon, 6),
                FreqMhz = mhz, Region = region.Length > 0 ? region : null,
            });
        }
        return result;
    }

    // ---------- zapytania pomocnicze ----------

    private async Task<IlsInfo?> TryGetIlsAsync(string icao, string region, CancellationToken ct)
    {
        try
        {
            var reply = await RequestAsync(DefVor, icao, region, 'V', LookupTimeout, ct);
            var vor = reply.Root(SimConnectFacilityDataType.Vor);
            if (vor == null) return null;
            var c = vor.Cursor();
            c.F64();
            c.F64();
            var freq = c.U32();
            var loc = c.F32();
            var gs = c.F32();
            var hasGs = c.I32() != 0;
            c.I32(); // IS_NAV
            return new IlsInfo
            {
                Ident = icao, FreqMhz = FreqMhz(freq) ?? 0, LocCourseDeg = NavMath.Round(NavMath.NormDeg(loc), 1),
                GsAngleDeg = hasGs && gs > 0 ? NavMath.Round(gs, 2) : null,
            };
        }
        catch (NavDataException)
        {
            return null;
        }
    }

    // Punkt danego typu ('W', 'V', 'N'); bez regionu MSFS może zwrócić listę kandydatów (MINIMAL_LIST).
    private async Task<List<FixResult>> LookupAsync(string ident, string? region, char type, CancellationToken ct)
    {
        var def = type switch { 'V' => DefVor, 'N' => DefNdb, _ => DefWaypoint };
        Reply reply;
        try
        {
            reply = await RequestAsync(def, ident, region ?? "", type, LookupTimeout, ct);
        }
        catch (NavDataException e) when (e.Code == ErrorCodes.NotFound)
        {
            return new();
        }

        if (reply.Candidates.Count > 0 && region == null)
        {
            var details = reply.Candidates
                .Where(x => x.Type == type || x.Type == '\0')
                .Take(8)
                .Select(async x =>
                {
                    var found = await LookupAsync(ident, x.Region, type, ct);
                    return found.Count > 0 ? found : new List<FixResult> { new() { Ident = ident, Type = FixTypeName(type), Lat = x.Lat, Lon = x.Lon, Region = x.Region } };
                });
            return (await Task.WhenAll(details)).SelectMany(x => x).ToList();
        }

        var results = new List<FixResult>();
        var rootType = type switch { 'V' => SimConnectFacilityDataType.Vor, 'N' => SimConnectFacilityDataType.Ndb, _ => SimConnectFacilityDataType.Waypoint };
        var root = reply.Root(rootType);
        if (root == null) return results;
        var c = root.Cursor();
        var lat = c.F64();
        var lon = c.F64();
        double? mhz = null;
        string? reg = region is { Length: > 0 } ? region : null;
        if (type == 'V')
        {
            mhz = FreqMhz(c.U32());
        }
        else if (type == 'N')
        {
            mhz = FreqMhz(c.U32());
        }
        else
        {
            c.Str(8); // ICAO
            var r = c.Str(8);
            if (r.Length > 0) reg = r;
        }
        if (lat == 0 && lon == 0) return results;
        results.Add(new FixResult { Ident = ident, Type = FixTypeName(type), Lat = NavMath.Round(lat, 6), Lon = NavMath.Round(lon, 6), FreqMhz = mhz, Region = reg });
        return results;
    }

    private async Task<List<FixResult>> LookupAirportAsync(string ident, CancellationToken ct)
    {
        if (ident.Length < 3) return new();
        try
        {
            var reply = await RequestAsync(DefAirportPos, ident, "", null, LookupTimeout, ct);
            var root = reply.Root(SimConnectFacilityDataType.Airport);
            if (root == null) return new();
            var c = root.Cursor();
            var lat = c.F64();
            var lon = c.F64();
            var icao = c.Str(8);
            var region = c.Str(8);
            return new() { new FixResult { Ident = icao.Length > 0 ? icao : ident, Type = FixTypes.Airport, Lat = NavMath.Round(lat, 6), Lon = NavMath.Round(lon, 6), Region = region.Length > 0 ? region : null } };
        }
        catch (NavDataException e) when (e.Code == ErrorCodes.NotFound)
        {
            return new();
        }
    }

    // Odcinki dziecka `parent` danego typu (domyślnie APPROACH_LEG), w kolejności z bazy.
    private static async Task<List<LegInfo>> Legs(Node parent, FixResolver resolver, SimConnectFacilityDataType type = SimConnectFacilityDataType.ApproachLeg)
    {
        var legs = new List<LegInfo>();
        foreach (var n in parent.Children(type))
        {
            var c = n.Cursor();
            var legType = c.I32();
            var fix = c.Str(8);
            var fixRegion = c.Str(8);
            var fixType = (char)c.I32();
            var lat = c.F64();
            var lon = c.F64();
            var flyOver = c.I32() != 0;
            var distanceInMinutes = c.I32() != 0;
            c.I32(); // TRUE_DEGREE — kurs zostawiamy tak, jak w bazie (zwykle magnetyczny)
            var course = c.F32();
            var routeDistance = c.F32();
            var altDesc = c.I32();
            var alt1 = c.F32();
            var alt2 = c.F32();
            var speed = c.F32();

            double? fLat = null, fLon = null;
            if (fix.Length > 0)
            {
                if (lat != 0 || lon != 0)
                {
                    fLat = NavMath.Round(lat, 6);
                    fLon = NavMath.Round(lon, 6);
                }
                else if (await resolver.ResolveAsync(fix, fixRegion, fixType) is { } pos)
                {
                    (fLat, fLon) = (NavMath.Round(pos.Lat, 6), NavMath.Round(pos.Lon, 6));
                }
            }

            var desc = altDesc switch { 1 => AltDescs.At, 2 => AltDescs.Above, 3 => AltDescs.Below, 4 => AltDescs.Between, _ => null };
            var type2 = LegTypeName(legType);
            legs.Add(new LegInfo
            {
                Fix = fix, Lat = fLat, Lon = fLon, LegType = type2, AltDesc = desc,
                Alt1Ft = desc != null ? NavMath.Round(alt1 * NavMath.FtPerM, 0) : null,
                Alt2Ft = desc == AltDescs.Between ? NavMath.Round(alt2 * NavMath.FtPerM, 0) : null,
                SpeedKt = speed > 0 ? NavMath.Round(speed, 0) : null,
                CourseDeg = HasCourse(type2) || course != 0 ? NavMath.Round(NavMath.NormDeg(course), 1) : null,
                DistNm = !distanceInMinutes && routeDistance > 0 ? NavMath.Round(routeDistance / NavMath.MPerNm, 1) : null,
                FlyOver = flyOver,
            });
        }
        return legs;
    }

    // Współrzędne punktu z obiektu WAYPOINT/VOR/NDB (gdy odcinek nie ma ich w FIX_LATITUDE/LONGITUDE).
    private sealed class FixResolver(MsfsNavData owner, CancellationToken ct)
    {
        private readonly Dictionary<string, (double Lat, double Lon)?> _cache = new();

        public async Task<(double Lat, double Lon)?> ResolveAsync(string ident, string region, char type)
        {
            var key = $"{type}|{ident}|{region}";
            if (_cache.TryGetValue(key, out var hit)) return hit;
            (double, double)? pos = null;
            if (type is 'W' or 'V' or 'N')
            {
                var found = await owner.LookupAsync(ident, region, type, ct);
                if (found.Count > 0) pos = (found[0].Lat, found[0].Lon);
            }
            _cache[key] = pos;
            return pos;
        }
    }

    // ---------- definicje ----------

    private static void DefineAll(IntPtr h)
    {
        Define(h, DefAirport,
            "OPEN AIRPORT", "LATITUDE", "LONGITUDE", "ALTITUDE", "NAME64", "ICAO",
            "OPEN RUNWAY", "LATITUDE", "LONGITUDE", "ALTITUDE", "HEADING", "LENGTH", "WIDTH",
            "PRIMARY_ILS_ICAO", "PRIMARY_ILS_REGION", "PRIMARY_NUMBER", "PRIMARY_DESIGNATOR",
            "SECONDARY_ILS_ICAO", "SECONDARY_ILS_REGION", "SECONDARY_NUMBER", "SECONDARY_DESIGNATOR", "CLOSE RUNWAY",
            "OPEN FREQUENCY", "TYPE", "FREQUENCY", "NAME", "CLOSE FREQUENCY",
            "CLOSE AIRPORT");

        var leg = new[]
        {
            "TYPE", "FIX_ICAO", "FIX_REGION", "FIX_TYPE", "FIX_LATITUDE", "FIX_LONGITUDE", "FLY_OVER", "DISTANCE_MINUTE",
            "TRUE_DEGREE", "COURSE", "ROUTE_DISTANCE", "APPROACH_ALT_DESC", "ALTITUDE1", "ALTITUDE2", "SPEED_LIMIT",
        };
        string[] Wrap(string name) => new[] { "OPEN " + name }.Concat(leg).Append("CLOSE " + name).ToArray();
        string[] Terminal(string name) => new[] { "OPEN " + name, "NAME" }
            .Append("OPEN RUNWAY_TRANSITION").Append("RUNWAY_NUMBER").Append("RUNWAY_DESIGNATOR").Concat(Wrap("APPROACH_LEG")).Append("CLOSE RUNWAY_TRANSITION")
            .Append("OPEN ENROUTE_TRANSITION").Append("NAME").Concat(Wrap("APPROACH_LEG")).Append("CLOSE ENROUTE_TRANSITION")
            .Concat(Wrap("APPROACH_LEG"))
            .Append("CLOSE " + name).ToArray();

        var procedures = new List<string> { "OPEN AIRPORT", "ICAO" };
        procedures.AddRange(Terminal("DEPARTURE"));
        procedures.AddRange(Terminal("ARRIVAL"));
        procedures.AddRange(new[] { "OPEN APPROACH", "TYPE", "SUFFIX", "RUNWAY_NUMBER", "RUNWAY_DESIGNATOR",
            "OPEN APPROACH_TRANSITION", "NAME", "IAF_ICAO" });
        procedures.AddRange(Wrap("APPROACH_LEG"));
        procedures.Add("CLOSE APPROACH_TRANSITION");
        procedures.AddRange(Wrap("FINAL_APPROACH_LEG"));
        procedures.AddRange(Wrap("MISSED_APPROACH_LEG"));
        procedures.Add("CLOSE APPROACH");
        procedures.Add("CLOSE AIRPORT");
        Define(h, DefProcedures, procedures.ToArray());

        Define(h, DefVor, "OPEN VOR", "VOR_LATITUDE", "VOR_LONGITUDE", "FREQUENCY", "LOCALIZER", "GLIDE_SLOPE", "HAS_GLIDE_SLOPE", "IS_NAV", "CLOSE VOR");
        Define(h, DefNdb, "OPEN NDB", "LATITUDE", "LONGITUDE", "FREQUENCY", "CLOSE NDB");
        Define(h, DefWaypoint, "OPEN WAYPOINT", "LATITUDE", "LONGITUDE", "ICAO", "REGION", "CLOSE WAYPOINT");
        Define(h, DefAirportPos, "OPEN AIRPORT", "LATITUDE", "LONGITUDE", "ICAO", "REGION", "CLOSE AIRPORT");
    }

    private static void Define(IntPtr h, uint def, params string[] fields)
    {
        foreach (var f in fields)
        {
            var hr = Native.SimConnect_AddToFacilityDefinition(h, def, f);
            if (hr < 0) throw new InvalidOperationException($"AddToFacilityDefinition({f}) = 0x{hr:X8}");
        }
    }

    // ---------- żądania i surowe wiadomości ----------

    private async Task<Reply> RequestAsync(uint def, string icao, string region, char? type, TimeSpan timeout, CancellationToken ct)
    {
        var pending = new Pending();
        uint requestId;
        lock (_nativeLock)
        {
            if (_client is not { IsConnected: true } client) throw new NavDataException(ErrorCodes.SimUnavailable, "Brak połączenia z MSFS");
            requestId = _nextRequest++;
            if (_nextRequest == 0) _nextRequest = FirstRequestId;
            _pending[requestId] = pending;
            var hr = type is { } t
                ? Native.SimConnect_RequestFacilityData_EX1(client.Handle, def, requestId, icao, region, (byte)t)
                : Native.SimConnect_RequestFacilityData(client.Handle, def, requestId, icao, region);
            if (hr < 0)
            {
                _pending.TryRemove(requestId, out _);
                throw new NavDataException(ErrorCodes.NotFound, $"MSFS odrzucił zapytanie o {icao} (0x{hr:X8})");
            }
            // Wyjątek SimConnect (np. nieznany identyfikator) wskazuje numer pakietu, nie żądania.
            if (Native.SimConnect_GetLastSentPacketID(client.Handle, out var packet) >= 0)
            {
                _packetToRequest[packet] = requestId;
                if (_earlyExceptions.TryRemove(packet, out var code)) pending.Fail(NotFound($"Brak {icao} w bazie MSFS (wyjątek {code})"));
            }
        }

        try
        {
            return await pending.Task.WaitAsync(timeout, ct);
        }
        catch (TimeoutException)
        {
            throw NotFound($"MSFS nie zwrócił danych dla {icao} w {timeout.TotalSeconds:0} s");
        }
        finally
        {
            _pending.TryRemove(requestId, out _);
        }
    }

    // Wątek biblioteki SimConnect.NET; pamięć wiadomości jest ważna tylko w tym wywołaniu — kopiujemy dane.
    private void OnRawMessage(object? sender, RawSimConnectMessageEventArgs e)
    {
        try
        {
            var ptr = e.DataPointer;
            var size = (int)e.DataSize;
            switch (e.MessageId)
            {
                case SimConnectRecvId.FacilityData when size >= FacilityDataHeader:
                {
                    var request = (uint)Marshal.ReadInt32(ptr, 12);
                    if (!_pending.TryGetValue(request, out var p)) return;
                    var node = new Node(
                        (SimConnectFacilityDataType)Marshal.ReadInt32(ptr, 24),
                        (uint)Marshal.ReadInt32(ptr, 16),
                        (uint)Marshal.ReadInt32(ptr, 20),
                        (uint)Marshal.ReadInt32(ptr, 32),
                        Copy(ptr, FacilityDataHeader, size - FacilityDataHeader));
                    p.Add(node);
                    break;
                }
                case SimConnectRecvId.FacilityDataEnd when size >= 16:
                {
                    var request = (uint)Marshal.ReadInt32(ptr, 12);
                    if (_pending.TryGetValue(request, out var p)) p.Complete();
                    break;
                }
                case SimConnectRecvId.FacilityMinimalList when size >= MinimalListHeader:
                {
                    var request = (uint)Marshal.ReadInt32(ptr, 12);
                    if (!_pending.TryGetValue(request, out var p)) return;
                    var count = Marshal.ReadInt32(ptr, 16);
                    var entry = Marshal.ReadInt32(ptr, 20);
                    var outOf = Marshal.ReadInt32(ptr, 24);
                    if (count > 0) p.AddCandidates(ParseMinimal(Copy(ptr, MinimalListHeader, size - MinimalListHeader), count));
                    if (entry + 1 >= outOf) p.Complete();
                    break;
                }
                case SimConnectRecvId.AirportList or SimConnectRecvId.WaypointList or SimConnectRecvId.NdbList or SimConnectRecvId.VorList
                    when size >= MinimalListHeader:
                {
                    var request = (uint)Marshal.ReadInt32(ptr, 12);
                    if (!_lists.TryGetValue(request, out var l)) return;
                    var count = Marshal.ReadInt32(ptr, 16);
                    var entry = Marshal.ReadInt32(ptr, 20);
                    var outOf = Marshal.ReadInt32(ptr, 24);
                    if (count > 0) l.Add(ParseList(Copy(ptr, MinimalListHeader, size - MinimalListHeader), count, l.FixType));
                    if (entry + 1 >= outOf) l.Complete();
                    break;
                }
                case SimConnectRecvId.Exception when size >= 24:
                {
                    var exception = (uint)Marshal.ReadInt32(ptr, 12);
                    var sendId = (uint)Marshal.ReadInt32(ptr, 16);
                    if (_packetToRequest.TryRemove(sendId, out var request))
                    {
                        if (_pending.TryGetValue(request, out var p)) p.Fail(NotFound($"Brak obiektu w bazie MSFS (wyjątek SimConnect {exception})"));
                    }
                    else if (_pending.Count > 0)
                    {
                        _earlyExceptions[sendId] = exception;
                        if (_earlyExceptions.Count > 64) _earlyExceptions.Clear();
                    }
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            Log?.Invoke($"Facilities: {ex.Message}");
        }
    }

    // SIMCONNECT_FACILITY_MINIMAL = SIMCONNECT_ICAO + SIMCONNECT_DATA_LATLONALT (3 × double), upakowane.
    // Rozmiar SIMCONNECT_ICAO różni się między wersjami SDK — wyznaczamy go z długości wpisu.
    private static List<Candidate> ParseMinimal(byte[] data, int count)
    {
        var result = new List<Candidate>();
        var entrySize = data.Length / count;
        var icaoSize = entrySize - 24;
        if (icaoSize < 10) return result;
        // Type (1) + Ident + Region (3) + Airport; Ident i Airport mają równą długość.
        var identLen = (icaoSize - 4) / 2;
        for (var i = 0; i < count; i++)
        {
            var o = i * entrySize;
            var type = (char)data[o];
            var ident = Ascii(data, o + 1, identLen);
            var region = Ascii(data, o + 1 + identLen, 3);
            var lat = BitConverter.ToDouble(data, o + icaoSize);
            var lon = BitConverter.ToDouble(data, o + icaoSize + 8);
            result.Add(new Candidate(type, ident, region, lat, lon));
        }
        return result;
    }

    private static byte[] Copy(IntPtr ptr, int offset, int length)
    {
        var bytes = new byte[Math.Max(0, length)];
        if (length > 0) Marshal.Copy(ptr + offset, bytes, 0, length);
        return bytes;
    }

    private static string Ascii(byte[] data, int offset, int length)
    {
        var end = offset;
        while (end < offset + length && end < data.Length && data[end] != 0) end++;
        return Encoding.ASCII.GetString(data, offset, end - offset).Trim();
    }

    // ---------- mapowanie wartości ----------

    private static string FrequencyType(int t) => t switch
    {
        1 => FrequencyTypes.Atis,
        7 or 14 or 15 => FrequencyTypes.Clearance,
        5 => FrequencyTypes.Ground,
        6 => FrequencyTypes.Tower,
        8 => FrequencyTypes.Approach,
        9 => FrequencyTypes.Departure,
        10 => FrequencyTypes.Center,
        2 or 3 or 4 => FrequencyTypes.Unicom,
        _ => FrequencyTypes.Other,
    };

    private static string ApproachType(int t) => t switch
    {
        1 => ApproachTypes.Gps,
        2 or 8 => ApproachTypes.Vor,
        3 or 9 => ApproachTypes.Ndb,
        4 => ApproachTypes.Ils,
        5 or 11 => ApproachTypes.Loc,
        10 => ApproachTypes.Rnav,
        _ => ApproachTypes.Other,
    };

    private static string ApproachLabel(int t) => t switch
    {
        1 => "GPS", 2 => "VOR", 3 => "NDB", 4 => "ILS", 5 => "LOC", 6 => "SDF", 7 => "LDA",
        8 => "VORDME", 9 => "NDBDME", 10 => "RNAV", 11 => "LOC BC", _ => "APP",
    };

    private static readonly string[] LegTypes =
    {
        "UNKNOWN", "AF", "CA", "CD", "CF", "CI", "CR", "DF", "FA", "FC", "FD", "FM", "HA", "HF", "HM", "IF", "PI", "RF", "TF",
        "VA", "VD", "VI", "VM", "VR",
    };

    private static string LegTypeName(int t) => t >= 0 && t < LegTypes.Length ? LegTypes[t] : "UNKNOWN";

    private static bool HasCourse(string legType) => legType is "CA" or "CD" or "CF" or "CI" or "CR" or "FA" or "FC" or "FD" or "FM"
        or "HA" or "HF" or "HM" or "PI" or "VA" or "VD" or "VI" or "VM" or "VR";

    private static string FixTypeName(char t) => t switch { 'V' => FixTypes.Vor, 'N' => FixTypes.Ndb, 'A' => FixTypes.Airport, _ => FixTypes.Waypoint };

    // VOR/ILS podaje Hz; NDB w zależności od wersji Hz albo kHz.
    private static double? FreqMhz(uint f) => f switch
    {
        0 => null,
        >= 100_000 => NavMath.Round(f / 1_000_000.0, 3),
        _ => NavMath.Round(f / 1000.0, 3),
    };

    private static NavDataException NotFound(string message) => new(ErrorCodes.NotFound, message);

    // ---------- struktury pomocnicze ----------

    private sealed record Candidate(char Type, string Ident, string Region, double Lat, double Lon);

    private sealed class Node(SimConnectFacilityDataType type, uint id, uint parent, uint index, byte[] data)
    {
        public SimConnectFacilityDataType Type { get; } = type;
        public uint Id { get; } = id;
        public uint Parent { get; } = parent;
        public uint Index { get; } = index;
        public List<Node> Kids { get; } = new();

        public Cursor Cursor() => new(data);

        public IEnumerable<Node> Children(SimConnectFacilityDataType t) => Kids.Where(k => k.Type == t).OrderBy(k => k.Index);
    }

    private sealed class Reply(List<Node> nodes, List<Candidate> candidates)
    {
        public List<Candidate> Candidates { get; } = candidates;

        public Node? Root(SimConnectFacilityDataType type)
        {
            var byId = new Dictionary<uint, Node>();
            foreach (var n in nodes) byId.TryAdd(n.Id, n);
            Node? root = null;
            foreach (var n in nodes)
            {
                if (n.Parent != 0 && byId.TryGetValue(n.Parent, out var parent)) parent.Kids.Add(n);
                else if (n.Type == type) root ??= n;
            }
            return root;
        }
    }

    private sealed class ListPending(string fixType)
    {
        private readonly List<FixResult> _items = new();
        private readonly TaskCompletionSource<List<FixResult>> _tcs = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public string FixType { get; } = fixType;
        public Task<List<FixResult>> Task => _tcs.Task;

        public void Add(IEnumerable<FixResult> items)
        {
            lock (_items) _items.AddRange(items);
        }

        public List<FixResult> Snapshot()
        {
            lock (_items) return _items.ToList();
        }

        public void Complete() => _tcs.TrySetResult(Snapshot());
    }

    private sealed class Pending
    {
        private readonly List<Node> _nodes = new();
        private readonly List<Candidate> _candidates = new();
        private readonly TaskCompletionSource<Reply> _tcs = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public Task<Reply> Task => _tcs.Task;

        public void Add(Node n)
        {
            lock (_nodes) _nodes.Add(n);
        }

        public void AddCandidates(IEnumerable<Candidate> c)
        {
            lock (_nodes) _candidates.AddRange(c);
        }

        public void Complete()
        {
            lock (_nodes) _tcs.TrySetResult(new Reply(_nodes.ToList(), _candidates.ToList()));
        }

        public void Fail(Exception e) => _tcs.TrySetException(e);
    }

    // Odczyt upakowanych pól w kolejności definicji.
    private sealed class Cursor(byte[] data)
    {
        private int _o;

        public double F64() => Read(8, () => BitConverter.ToDouble(data, _o));
        public float F32() => Read(4, () => BitConverter.ToSingle(data, _o));
        public int I32() => Read(4, () => BitConverter.ToInt32(data, _o));
        public uint U32() => Read(4, () => BitConverter.ToUInt32(data, _o));

        public string Str(int length)
        {
            var s = _o + length <= data.Length ? Ascii(data, _o, length) : "";
            _o += length;
            return s;
        }

        private T Read<T>(int size, Func<T> read) where T : struct
        {
            var v = _o + size <= data.Length ? read() : default;
            _o += size;
            return v;
        }
    }

    private static class Native
    {
        [DllImport("SimConnect.dll")]
        public static extern int SimConnect_AddToFacilityDefinition(IntPtr hSimConnect, uint defineId,
            [MarshalAs(UnmanagedType.LPStr)] string fieldName);

        [DllImport("SimConnect.dll")]
        public static extern int SimConnect_RequestFacilityData(IntPtr hSimConnect, uint defineId, uint requestId,
            [MarshalAs(UnmanagedType.LPStr)] string icao, [MarshalAs(UnmanagedType.LPStr)] string region);

        [DllImport("SimConnect.dll")]
        public static extern int SimConnect_RequestFacilityData_EX1(IntPtr hSimConnect, uint defineId, uint requestId,
            [MarshalAs(UnmanagedType.LPStr)] string icao, [MarshalAs(UnmanagedType.LPStr)] string region, byte type);

        // type: 0 lotniska, 1 punkty trasy, 2 NDB, 3 VOR (SIMCONNECT_FACILITY_LIST_TYPE)
        [DllImport("SimConnect.dll")]
        public static extern int SimConnect_RequestFacilitiesList_EX1(IntPtr hSimConnect, int type, uint requestId);

        [DllImport("SimConnect.dll")]
        public static extern int SimConnect_GetLastSentPacketID(IntPtr hSimConnect, out uint sendId);
    }
}
