using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Core.NavData;

// Dane nawigacyjne trybu symulacji: deterministyczne próbki dla EPWA i EPGD.
// Współrzędne progów zgodne z lotem testowym (FlightSimulation), reszta wiarygodna, ale PRZYKŁADOWA —
// nie do prawdziwej nawigacji. Nazwy procedur i punktów są wymyślone.
public sealed class SimulationNavData : INavDataProvider
{
    private static readonly Dictionary<string, (double Lat, double Lon, string Type, double? Mhz, string? Region)> Fixes = new()
    {
        // EPWA — odloty
        ["WA531"] = (52.3000, 20.9000, FixTypes.Waypoint, null, "EP"),
        ["SOXER"] = (52.6200, 20.5500, FixTypes.Waypoint, null, "EP"),
        ["WA542"] = (52.1000, 21.1500, FixTypes.Waypoint, null, "EP"),
        ["LOGDA"] = (51.8500, 21.4500, FixTypes.Waypoint, null, "EP"),
        ["NEPOX"] = (51.5500, 21.9000, FixTypes.Waypoint, null, "EP"),
        // EPWA — przyloty i podejścia
        ["AGAVA"] = (52.7500, 21.4000, FixTypes.Waypoint, null, "EP"),
        ["WA411"] = (52.4500, 21.1500, FixTypes.Waypoint, null, "EP"),
        ["WA412"] = (52.3000, 21.2000, FixTypes.Waypoint, null, "EP"),
        ["BAMSO"] = (51.6000, 20.4000, FixTypes.Waypoint, null, "EP"),
        ["WA421"] = (51.9000, 20.6000, FixTypes.Waypoint, null, "EP"),
        ["WA331"] = (52.0092, 21.1057, FixTypes.Waypoint, null, "EP"),  // 10 NM przed progiem 33
        ["FI33"] = (52.0675, 21.0530, FixTypes.Waypoint, null, "EP"),   // FAF ILS 33, 6 NM
        ["WA291"] = (52.0824, 21.2347, FixTypes.Waypoint, null, "EP"),  // IF RNAV 29, 10 NM przed progiem
        ["FA29"] = (52.1190, 21.1127, FixTypes.Waypoint, null, "EP"),   // FAF RNAV 29, 5 NM
        ["WA510"] = (52.2500, 20.8500, FixTypes.Waypoint, null, "EP"),
        // EPGD
        ["GD291"] = (54.3166, 18.7533, FixTypes.Waypoint, null, "EP"),  // 10 NM przed progiem 29
        ["FI29"] = (54.3415, 18.6472, FixTypes.Waypoint, null, "EP"),   // FAF ILS 29, 6 NM
        ["GD112"] = (54.4508, 18.1827, FixTypes.Waypoint, null, "EP"),  // IF RNAV 11, 10 NM
        ["FA11"] = (54.4196, 18.3154, FixTypes.Waypoint, null, "EP"),   // FAF RNAV 11, 5 NM
        ["GD510"] = (54.4500, 18.3000, FixTypes.Waypoint, null, "EP"),
        ["GD610"] = (54.3000, 18.7000, FixTypes.Waypoint, null, "EP"),
        ["TOXAR"] = (53.9000, 19.3500, FixTypes.Waypoint, null, "EP"),
        ["GD401"] = (54.1500, 19.0000, FixTypes.Waypoint, null, "EP"),
        ["LUXAR"] = (54.5500, 17.6000, FixTypes.Waypoint, null, "EP"),
        ["GD402"] = (54.5000, 17.9500, FixTypes.Waypoint, null, "EP"),
        ["OKUPA"] = (54.6500, 18.0000, FixTypes.Waypoint, null, "EP"),
        ["GD701"] = (54.4500, 18.3500, FixTypes.Waypoint, null, "EP"),
        ["NIKOP"] = (54.2000, 19.0500, FixTypes.Waypoint, null, "EP"),
        ["GD702"] = (54.3300, 18.6500, FixTypes.Waypoint, null, "EP"),
        // Radiolatarnie
        ["WAR"] = (52.1880, 20.9620, FixTypes.Vor, 113.450, "EP"),
        ["GDA"] = (54.3700, 18.4000, FixTypes.Vor, 115.500, "EP"),
        ["WA"] = (52.1300, 21.0100, FixTypes.Ndb, 0.389, "EP"),
        ["GD"] = (54.3600, 18.5500, FixTypes.Ndb, 0.418, "EP"),
        // Punkt o tej samej nazwie w innym regionie (test sortowania po odległości)
        ["SOXER@ED"] = (50.1000, 8.6000, FixTypes.Waypoint, null, "ED"),
    };

    private static readonly Dictionary<string, AirportInfo> Airports = new()
    {
        ["EPWA"] = new AirportInfo
        {
            Icao = "EPWA", Name = "Warsaw Chopin", Lat = 52.1657, Lon = 20.9671, ElevFt = 362,
            Runways =
            {
                Rwy("11", 116, 3690, 60, 52.1700, 20.9420, 354, new IlsInfo { Ident = "IWA", FreqMhz = 109.900, LocCourseDeg = 110, GsAngleDeg = 3.0 }),
                Rwy("29", 296, 3690, 60, 52.1555, 20.9906, 350, null),
                Rwy("15", 151, 2800, 50, 52.1770, 20.9541, 341, null),
                Rwy("33", 331, 2800, 50, 52.1550, 20.9740, 362, new IlsInfo { Ident = "IWS", FreqMhz = 110.300, LocCourseDeg = 325, GsAngleDeg = 3.0 }),
            },
            Frequencies =
            {
                new FrequencyInfo { Type = FrequencyTypes.Atis, Name = "Okecie Information", Mhz = 120.455 },
                new FrequencyInfo { Type = FrequencyTypes.Clearance, Name = "Okecie Delivery", Mhz = 121.605 },
                new FrequencyInfo { Type = FrequencyTypes.Ground, Name = "Okecie Ground", Mhz = 121.900 },
                new FrequencyInfo { Type = FrequencyTypes.Tower, Name = "Okecie Tower", Mhz = 118.300 },
                new FrequencyInfo { Type = FrequencyTypes.Approach, Name = "Warszawa Approach", Mhz = 128.800 },
                new FrequencyInfo { Type = FrequencyTypes.Departure, Name = "Warszawa Departure", Mhz = 128.800 },
            },
        },
        ["EPGD"] = new AirportInfo
        {
            Icao = "EPGD", Name = "Gdansk Lech Walesa", Lat = 54.3776, Lon = 18.4662, ElevFt = 489,
            Runways =
            {
                Rwy("11", 112, 2800, 45, 54.3884, 18.4480, 470, new IlsInfo { Ident = "IGDB", FreqMhz = 110.900, LocCourseDeg = 106, GsAngleDeg = 3.0 }),
                Rwy("29", 292, 2800, 45, 54.3790, 18.4880, 489, new IlsInfo { Ident = "IGDA", FreqMhz = 109.300, LocCourseDeg = 286, GsAngleDeg = 3.0 }),
            },
            Frequencies =
            {
                new FrequencyInfo { Type = FrequencyTypes.Atis, Name = "Rebiechowo Information", Mhz = 126.775 },
                new FrequencyInfo { Type = FrequencyTypes.Ground, Name = "Rebiechowo Ground", Mhz = 121.750 },
                new FrequencyInfo { Type = FrequencyTypes.Tower, Name = "Rebiechowo Tower", Mhz = 118.100 },
                new FrequencyInfo { Type = FrequencyTypes.Approach, Name = "Gdansk Approach", Mhz = 128.075 },
            },
        },
    };

    public Task<AirportInfo> GetAirportAsync(string icao, CancellationToken ct) =>
        Airports.TryGetValue(icao, out var a)
            ? Task.FromResult(a)
            : Task.FromException<AirportInfo>(NotFound(icao));

    public Task<AirportProcedures> GetProceduresAsync(string icao, CancellationToken ct) => icao switch
    {
        "EPWA" => Task.FromResult(Epwa(new Legs("EPWA"))),
        "EPGD" => Task.FromResult(Epgd(new Legs("EPGD"))),
        _ => Task.FromException<AirportProcedures>(NotFound(icao)),
    };

    public Task<IReadOnlyList<FixResult>> FindFixAsync(string ident, CancellationToken ct)
    {
        var results = new List<FixResult>();
        foreach (var (key, f) in Fixes)
        {
            var name = key.Split('@')[0];
            if (name != ident) continue;
            results.Add(new FixResult { Ident = name, Type = f.Type, Lat = f.Lat, Lon = f.Lon, FreqMhz = f.Mhz, Region = f.Region });
        }
        if (Airports.TryGetValue(ident, out var a))
            results.Add(new FixResult { Ident = a.Icao, Type = FixTypes.Airport, Lat = a.Lat, Lon = a.Lon, Region = "EP" });
        return Task.FromResult<IReadOnlyList<FixResult>>(results);
    }

    private static AirportProcedures Epwa(Legs l) => new()
    {
        Icao = "EPWA",
        Departures =
        {
            new TerminalProcedure
            {
                Name = "SOXER1G",
                RunwayTransitions =
                {
                    new("29", new() { l.Va(296, 1000), l.Df("WA531", Above, 4000) }),
                    new("33", new() { l.Va(331, 1000), l.Df("WA531", Above, 4000) }),
                },
                CommonLegs = { l.Tf("SOXER", Above, 7000) },
            },
            new TerminalProcedure
            {
                Name = "LOGDA1K",
                RunwayTransitions =
                {
                    new("11", new() { l.Va(116, 1000), l.Df("WA542", Above, 3000, speed: 220) }),
                    new("15", new() { l.Va(151, 1000), l.Df("WA542", Above, 3000, speed: 220) }),
                },
                CommonLegs = { l.Tf("LOGDA", Above, 6000) },
                EnrouteTransitions = { new("NEPOX", new() { l.Tf("NEPOX", Above, 9000) }) },
            },
        },
        Arrivals =
        {
            new TerminalProcedure
            {
                Name = "AGAVA1W",
                CommonLegs = { l.If("AGAVA", Above, 9000), l.Tf("WA411", At, 6000, speed: 220) },
                RunwayTransitions =
                {
                    new("33", new() { l.Tf("WA412", At, 4000), l.Tf("WA331", At, 4000) }),
                    new("29", new() { l.Tf("WA412", At, 4000) }),
                },
            },
            new TerminalProcedure
            {
                Name = "BAMSO1S",
                CommonLegs = { l.If("BAMSO", Above, 8000), l.Tf("WA421", Between, 6000, 5000, speed: 230) },
                RunwayTransitions =
                {
                    new("29", new() { l.Tf("WA291", At, 4000) }),
                    new("33", new() { l.Tf("WA331", At, 4000) }),
                },
            },
        },
        Approaches =
        {
            new ApproachProcedure
            {
                Name = "ILS 33", Type = ApproachTypes.Ils, Runway = "33",
                Transitions = { new("WA412", new() { l.If("WA412", At, 4000), l.Tf("WA331", At, 4000) }) },
                FinalLegs = { l.If("WA331", At, 4000), l.Cf("FI33", 331, At, 2300, speed: 180), l.Cf("RW33", 331, At, 410, flyOver: true) },
                MissedLegs = { l.Ca(331, 1500), l.Df("WA510", At, 4000) },
            },
            new ApproachProcedure
            {
                Name = "RNAV Z 29", Type = ApproachTypes.Rnav, Runway = "29", Suffix = "Z",
                Transitions = { new("BAMSO", new() { l.If("BAMSO", Above, 8000), l.Tf("WA421", At, 5000), l.Tf("WA291", At, 4000) }) },
                FinalLegs = { l.If("WA291", At, 4000), l.Tf("FA29", At, 2000), l.Tf("RW29", At, 400, flyOver: true) },
                MissedLegs = { l.Ca(296, 1500), l.Df("WA531", At, 4000) },
            },
        },
    };

    private static AirportProcedures Epgd(Legs l) => new()
    {
        Icao = "EPGD",
        Departures =
        {
            new TerminalProcedure
            {
                Name = "OKUPA1A",
                RunwayTransitions = { new("29", new() { l.Va(292, 1000), l.Df("GD701", Above, 3000) }) },
                CommonLegs = { l.Tf("OKUPA", Above, 6000) },
            },
            new TerminalProcedure
            {
                Name = "NIKOP1B",
                RunwayTransitions = { new("11", new() { l.Va(112, 1000), l.Df("GD702", Above, 3000) }) },
                CommonLegs = { l.Tf("NIKOP", Above, 7000) },
            },
        },
        Arrivals =
        {
            new TerminalProcedure
            {
                Name = "TOXAR1R",
                CommonLegs = { l.If("TOXAR", Above, 9000), l.Tf("GD401", At, 6000, speed: 220) },
                RunwayTransitions =
                {
                    new("29", new() { l.Tf("GD291", At, 3500) }),
                    new("11", new() { l.Tf("GD510", At, 4000), l.Tf("GD112", At, 3500) }),
                },
            },
            new TerminalProcedure
            {
                Name = "LUXAR1L",
                CommonLegs = { l.If("LUXAR", Above, 8000), l.Tf("GD402", At, 5000, speed: 220) },
                RunwayTransitions =
                {
                    new("11", new() { l.Tf("GD112", At, 3500) }),
                    new("29", new() { l.Tf("GD610", At, 4000), l.Tf("GD291", At, 3500) }),
                },
            },
        },
        Approaches =
        {
            new ApproachProcedure
            {
                Name = "ILS 29", Type = ApproachTypes.Ils, Runway = "29",
                Transitions = { new("GD401", new() { l.If("GD401", At, 6000), l.Tf("GD291", At, 3500) }) },
                FinalLegs = { l.If("GD291", At, 3500), l.Cf("FI29", 286, At, 2400, speed: 180), l.Cf("RW29", 286, At, 540, flyOver: true) },
                MissedLegs = { l.Ca(292, 1500), l.Df("GD510", At, 4000) },
            },
            new ApproachProcedure
            {
                Name = "RNAV 11", Type = ApproachTypes.Rnav, Runway = "11",
                Transitions = { new("GD402", new() { l.If("GD402", At, 5000), l.Tf("GD112", At, 3500) }) },
                FinalLegs = { l.If("GD112", At, 3500), l.Tf("FA11", At, 2100), l.Tf("RW11", At, 520, flyOver: true) },
                MissedLegs = { l.Ca(112, 1500), l.Df("GD610", At, 4000) },
            },
        },
    };

    private const string At = AltDescs.At, Above = AltDescs.Above, Between = AltDescs.Between;

    private static RunwayInfo Rwy(string ident, double hdg, double lengthM, double widthM, double lat, double lon, double elevFt, IlsInfo? ils) => new()
    {
        Ident = ident, HeadingTrueDeg = hdg, LengthFt = Math.Round(lengthM * NavMath.FtPerM), WidthFt = Math.Round(widthM * NavMath.FtPerM),
        ThresholdLat = lat, ThresholdLon = lon, ElevFt = elevFt, Ils = ils,
    };

    // Budowanie odcinków z kontekstem lotniska (punkty RWxx to progi pasów tego lotniska).
    private sealed class Legs(string icao)
    {
        private (double? Lat, double? Lon) Pos(string fix)
        {
            if (Fixes.TryGetValue(fix, out var f)) return (f.Lat, f.Lon);
            if (fix.StartsWith("RW"))
            {
                var r = Airports[icao].Runways.FirstOrDefault(x => x.Ident == fix[2..]);
                if (r != null) return (r.ThresholdLat, r.ThresholdLon);
            }
            return (null, null);
        }

        private LegInfo Leg(string type, string fix, string? desc, double? alt1, double? alt2, double? speed, double? course, bool flyOver)
        {
            var (lat, lon) = fix.Length > 0 ? Pos(fix) : (null, null);
            return new LegInfo
            {
                LegType = type, Fix = fix, Lat = lat, Lon = lon, AltDesc = desc, Alt1Ft = alt1, Alt2Ft = alt2,
                SpeedKt = speed, CourseDeg = course, FlyOver = flyOver,
            };
        }

        public LegInfo If(string fix, string desc, double alt) => Leg("IF", fix, desc, alt, null, null, null, false);
        public LegInfo Tf(string fix, string desc, double alt, double? alt2 = null, double? speed = null, bool flyOver = false) =>
            Leg("TF", fix, desc, alt, alt2, speed, null, flyOver);
        public LegInfo Df(string fix, string desc, double alt, double? speed = null) => Leg("DF", fix, desc, alt, null, speed, null, false);
        public LegInfo Cf(string fix, double course, string desc, double alt, double? speed = null, bool flyOver = false) =>
            Leg("CF", fix, desc, alt, null, speed, course, flyOver);
        public LegInfo Va(double heading, double alt) => Leg("VA", "", Above, alt, null, null, heading, false);
        public LegInfo Ca(double course, double alt) => Leg("CA", "", Above, alt, null, null, course, false);
    }

    private static NavDataException NotFound(string icao) =>
        new(ErrorCodes.NotFound, $"{icao}: w trybie symulacji dostępne są tylko EPWA i EPGD");
}
