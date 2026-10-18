using System.Collections.Concurrent;
using System.Text.Json;
using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Core.NavData;

// Sparsowane i zwalidowane `navdata_request`.
public sealed record NavdataQuery(string Kind, string? Icao = null, string? ProcType = null, string? Name = null,
    string? Runway = null, string? Transition = null, string? Ident = null, double? NearLat = null, double? NearLon = null,
    double? RadiusNm = null)
{
    public static NavdataQuery? Parse(IncomingMessage msg, out string? error)
    {
        error = null;
        var kind = msg.GetString("kind");
        switch (kind)
        {
            case NavdataKinds.Airport:
            case NavdataKinds.Procedures:
            {
                var icao = Code(msg, "icao", 2, 8, ref error);
                return icao == null ? null : new NavdataQuery(kind, Icao: icao);
            }

            case NavdataKinds.ProcedureLegs:
            {
                var icao = Code(msg, "icao", 2, 8, ref error);
                if (icao == null) return null;
                var procType = msg.GetString("procType");
                if (procType is not (ProcTypes.Departure or ProcTypes.Arrival or ProcTypes.Approach))
                {
                    error = "procType musi być departure, arrival albo approach";
                    return null;
                }
                var name = Text(msg, "name", required: true, ref error);
                var runway = Text(msg, "runway", required: false, ref error);
                var transition = Text(msg, "transition", required: false, ref error);
                if (error != null) return null;
                return new NavdataQuery(kind, Icao: icao, ProcType: procType, Name: name!.ToUpperInvariant(),
                    Runway: runway == null ? null : NavMath.NormalizeRunway(runway),
                    Transition: transition?.ToUpperInvariant());
            }

            case NavdataKinds.Fix:
            {
                var ident = Code(msg, "ident", 1, 8, ref error);
                if (ident == null) return null;
                var hasLat = msg.Has("nearLat");
                var hasLon = msg.Has("nearLon");
                if (hasLat != hasLon)
                {
                    error = "nearLat i nearLon podaje się razem";
                    return null;
                }
                double? lat = null, lon = null;
                if (hasLat)
                {
                    lat = Number(msg, "nearLat", -90, 90, ref error);
                    lon = Number(msg, "nearLon", -180, 180, ref error);
                    if (error != null) return null;
                }
                return new NavdataQuery(kind, Ident: ident, NearLat: lat, NearLon: lon);
            }

            case NavdataKinds.Nearby:
            {
                var lat = Number(msg, "lat", -90, 90, ref error);
                var lon = Number(msg, "lon", -180, 180, ref error);
                var radius = msg.Has("radiusNm") ? Number(msg, "radiusNm", 1, 250, ref error) : 60;
                if (error != null) return null;
                return new NavdataQuery(kind, NearLat: lat, NearLon: lon, RadiusNm: radius);
            }

            default:
                error = "kind musi być airport, procedures, procedure_legs, fix albo nearby";
                return null;
        }
    }

    // Klucz pamięci podręcznej (bez współrzędnych — sortowanie po odległości robi się po odczycie).
    public string CacheKey(string sourceKind) => Kind switch
    {
        NavdataKinds.Airport => $"{sourceKind}|airport|{Icao}",
        NavdataKinds.Procedures or NavdataKinds.ProcedureLegs => $"{sourceKind}|procedures|{Icao}",
        // Pozycja zaokrąglona do 0,2° (~12 NM): kolejne zapytania z lecącego samolotu trafiają w tę samą pozycję
        NavdataKinds.Nearby => $"{sourceKind}|nearby|{Math.Round(NearLat!.Value * 5)}|{Math.Round(NearLon!.Value * 5)}|{RadiusNm}",
        _ => $"{sourceKind}|fix|{Ident}",
    };

    private static string? Code(IncomingMessage msg, string field, int min, int max, ref string? error)
    {
        var s = msg.GetString(field)?.Trim().ToUpperInvariant();
        if (s == null || s.Length < min || s.Length > max || !s.All(char.IsAsciiLetterOrDigit))
        {
            error ??= $"{field}: {min}–{max} liter lub cyfr";
            return null;
        }
        return s;
    }

    private static string? Text(IncomingMessage msg, string field, bool required, ref string? error)
    {
        if (!msg.Has(field))
        {
            if (required) error ??= $"Brak pola {field}";
            return null;
        }
        var s = msg.GetString(field)?.Trim();
        if (s == null || s.Length == 0 || s.Length > 16 || s.Any(char.IsControl))
        {
            error ??= $"{field}: tekst 1–16 znaków";
            return null;
        }
        return s;
    }

    private static double? Number(IncomingMessage msg, string field, double min, double max, ref string? error)
    {
        if (msg.Root.TryGetProperty(field, out var el) && el.ValueKind == JsonValueKind.Number
            && el.TryGetDouble(out var d) && double.IsFinite(d) && d >= min && d <= max)
            return d;
        error ??= $"{field}: liczba {min}…{max}";
        return null;
    }
}

// Wykonuje zapytania na źródle danych z pamięcią podręczną (TTL 10 min). Równoległe zapytania
// o to samo dzielą jedno żądanie do MSFS. Błędy nie są zapamiętywane.
public sealed class NavDataService
{
    public static readonly TimeSpan CacheTtl = TimeSpan.FromMinutes(10);

    private readonly ConcurrentDictionary<string, Entry> _cache = new();
    private readonly Func<DateTime> _now;

    public NavDataService(Func<DateTime>? clock = null)
    {
        _now = clock ?? (() => DateTime.UtcNow);
    }

    public int CachedCount => _cache.Count;

    public async Task<object> ExecuteAsync(NavdataQuery q, INavDataProvider provider, string sourceKind, CancellationToken ct)
    {
        switch (q.Kind)
        {
            case NavdataKinds.Airport:
                return await GetAsync(q.CacheKey(sourceKind), () => provider.GetAirportAsync(q.Icao!, CancellationToken.None));

            case NavdataKinds.Procedures:
                return (await GetAsync(q.CacheKey(sourceKind), () => provider.GetProceduresAsync(q.Icao!, CancellationToken.None))).ToSummary();

            case NavdataKinds.ProcedureLegs:
                return (await GetAsync(q.CacheKey(sourceKind), () => provider.GetProceduresAsync(q.Icao!, CancellationToken.None))).GetLegs(q);

            case NavdataKinds.Nearby:
            {
                var lat = q.NearLat!.Value; var lon = q.NearLon!.Value; var radius = q.RadiusNm ?? 60;
                var all = await GetAsync(q.CacheKey(sourceKind), () => provider.NearbyAsync(lat, lon, radius, CancellationToken.None));
                // Limit na typ, żeby gęste rejony (tysiące punktów trasy) nie zalały telefonu
                var limits = new Dictionary<string, int> { [FixTypes.Airport] = 40, [FixTypes.Vor] = 30, [FixTypes.Ndb] = 30, [FixTypes.Waypoint] = 120 };
                var results = all
                    .Select(f => (f, d: NavMath.DistanceNm(lat, lon, f.Lat, f.Lon)))
                    .Where(x => x.d <= radius)
                    .GroupBy(x => x.f.Type)
                    .SelectMany(g => g.OrderBy(x => x.d).Take(limits.GetValueOrDefault(g.Key, 30)))
                    .OrderBy(x => x.d)
                    .Select(x => x.f)
                    .ToList();
                return new NearbyInfo { Results = results };
            }

            default:
            {
                var found = await GetAsync(q.CacheKey(sourceKind), () => provider.FindFixAsync(q.Ident!, CancellationToken.None));
                if (found.Count == 0) throw new NavDataException(ErrorCodes.NotFound, $"Nie znaleziono punktu {q.Ident}");
                IEnumerable<FixResult> sorted = q.NearLat.HasValue
                    ? found.OrderBy(f => NavMath.DistanceNm(q.NearLat.Value, q.NearLon!.Value, f.Lat, f.Lon))
                    : found.OrderBy(f => f.Type).ThenBy(f => f.Region);
                return new FixSearchInfo { Results = sorted.ToList() };
            }
        }
    }

    private async Task<T> GetAsync<T>(string key, Func<Task<T>> load) where T : class
    {
        var now = _now();
        while (true)
        {
            if (_cache.TryGetValue(key, out var existing) && existing.Expires > now)
            {
                try
                {
                    return (T)await existing.Task;
                }
                catch
                {
                    _cache.TryRemove(new KeyValuePair<string, Entry>(key, existing));
                    throw;
                }
            }

            var entry = new Entry(now + CacheTtl, LoadAsObject(load));
            if (existing != null) _cache.TryRemove(new KeyValuePair<string, Entry>(key, existing));
            if (_cache.TryAdd(key, entry)) continue; // odczyt w następnej pętli (ten sam kod obsługi błędu)
        }
    }

    private static async Task<object> LoadAsObject<T>(Func<Task<T>> load) where T : class => await load();

    private sealed record Entry(DateTime Expires, Task<object> Task);
}

// Kubełek żetonów: `capacity` żądań od razu, potem `perSecond` na sekundę.
public sealed class TokenBucket
{
    private readonly object _lock = new();
    private readonly double _capacity;
    private readonly double _perSecond;
    private readonly Func<long> _nowMs;
    private double _tokens;
    private long _lastMs;

    public TokenBucket(double perSecond, double? capacity = null, Func<long>? clockMs = null)
    {
        _perSecond = perSecond;
        _capacity = capacity ?? perSecond;
        _nowMs = clockMs ?? (() => Environment.TickCount64);
        _tokens = _capacity;
        _lastMs = _nowMs();
    }

    public bool TryTake()
    {
        lock (_lock)
        {
            var now = _nowMs();
            _tokens = Math.Min(_capacity, _tokens + (now - _lastMs) * _perSecond / 1000.0);
            _lastMs = now;
            if (_tokens < 1) return false;
            _tokens -= 1;
            return true;
        }
    }
}
