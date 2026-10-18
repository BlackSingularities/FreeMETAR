using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Core.NavData;

// Błąd zapytania navdata, wysyłany telefonowi jako `error` z tym kodem.
public sealed class NavDataException : Exception
{
    public NavDataException(string code, string message) : base(message) => Code = code;
    public string Code { get; }
}

// Źródło danych nawigacyjnych: baza MSFS (Facilities API) albo dane przykładowe symulacji.
public interface INavDataProvider
{
    Task<AirportInfo> GetAirportAsync(string icao, CancellationToken ct);

    // Pełne drzewo procedur lotniska z odcinkami (z niego powstają `procedures` i `procedure_legs`).
    Task<AirportProcedures> GetProceduresAsync(string icao, CancellationToken ct);

    // Wszystkie punkty o danym identyfikatorze (kolejność dowolna — sortuje NavDataService).
    Task<IReadOnlyList<FixResult>> FindFixAsync(string ident, CancellationToken ct);

    // Obiekty w pobliżu (MSFS: z „bańki rzeczywistości” wokół samolotu). Kolejność i limit robi NavDataService.
    Task<IReadOnlyList<FixResult>> NearbyAsync(double lat, double lon, double radiusNm, CancellationToken ct) =>
        Task.FromResult<IReadOnlyList<FixResult>>(Array.Empty<FixResult>());
}

public sealed record NamedLegs(string Name, List<LegInfo> Legs);

// SID albo STAR. Odcinki: przejście pasowe + wspólne + przejście trasowe (dla STAR w odwrotnej kolejności).
public sealed class TerminalProcedure
{
    public required string Name { get; init; }
    public List<LegInfo> CommonLegs { get; init; } = new();
    // Name = oznaczenie pasa ("29", "11L")
    public List<NamedLegs> RunwayTransitions { get; init; } = new();
    public List<NamedLegs> EnrouteTransitions { get; init; } = new();
}

public sealed class ApproachProcedure
{
    public required string Name { get; init; }
    public required string Type { get; init; }
    public string? Runway { get; init; }
    public string? Suffix { get; init; }
    public List<NamedLegs> Transitions { get; init; } = new();
    public List<LegInfo> FinalLegs { get; init; } = new();
    public List<LegInfo> MissedLegs { get; init; } = new();
}

public sealed class AirportProcedures
{
    public required string Icao { get; init; }
    public List<TerminalProcedure> Departures { get; init; } = new();
    public List<TerminalProcedure> Arrivals { get; init; } = new();
    public List<ApproachProcedure> Approaches { get; init; } = new();

    public ProceduresInfo ToSummary() => new()
    {
        Icao = Icao,
        Departures = Departures.Select(Summary).ToList(),
        Arrivals = Arrivals.Select(Summary).ToList(),
        Approaches = Approaches.Select(a => new ApproachInfo
        {
            Name = a.Name, Type = a.Type, Runway = a.Runway, Suffix = a.Suffix,
            Transitions = a.Transitions.Select(t => t.Name).ToList(),
        }).ToList(),
    };

    private static ProcedureInfo Summary(TerminalProcedure p) => new()
    {
        Name = p.Name,
        Runways = p.RunwayTransitions.Select(t => t.Name).Distinct().ToList(),
        Transitions = p.EnrouteTransitions.Select(t => t.Name).Distinct().ToList(),
    };

    public ProcedureLegsInfo GetLegs(NavdataQuery q)
    {
        var name = q.Name!;
        if (q.ProcType == ProcTypes.Approach)
        {
            var a = Approaches.FirstOrDefault(x => Same(x.Name, name))
                    ?? throw NotFound($"Brak podejścia {name} w {Icao}");
            var legs = new List<LegInfo>();
            if (q.Transition != null)
            {
                var t = a.Transitions.FirstOrDefault(x => Same(x.Name, q.Transition))
                        ?? throw NotFound($"Brak przejścia {q.Transition} dla {a.Name}");
                legs.AddRange(t.Legs);
            }
            Append(legs, a.FinalLegs);
            return new ProcedureLegsInfo
            {
                Icao = Icao, ProcType = q.ProcType!, Name = a.Name, Runway = a.Runway, Transition = q.Transition,
                Legs = legs, Missed = a.MissedLegs.ToList(),
            };
        }

        var list = q.ProcType == ProcTypes.Departure ? Departures : Arrivals;
        var p = list.FirstOrDefault(x => Same(x.Name, name))
                ?? throw NotFound($"Brak procedury {name} w {Icao}");

        List<LegInfo> rwyLegs = new(), enrLegs = new();
        string? runway = null;
        if (q.Runway != null && p.RunwayTransitions.Count > 0)
        {
            var rt = p.RunwayTransitions.FirstOrDefault(x => Same(x.Name, q.Runway))
                     ?? throw NotFound($"{p.Name} nie ma przejścia dla pasa {q.Runway}");
            rwyLegs = rt.Legs;
            runway = rt.Name;
        }
        else if (q.Runway == null && p.RunwayTransitions.Count == 1)
        {
            rwyLegs = p.RunwayTransitions[0].Legs;
            runway = p.RunwayTransitions[0].Name;
        }
        else
        {
            runway = q.Runway;
        }

        if (q.Transition != null)
        {
            var et = p.EnrouteTransitions.FirstOrDefault(x => Same(x.Name, q.Transition))
                     ?? throw NotFound($"{p.Name} nie ma przejścia {q.Transition}");
            enrLegs = et.Legs;
        }

        var result = new List<LegInfo>();
        if (q.ProcType == ProcTypes.Departure)
        {
            Append(result, rwyLegs);
            Append(result, p.CommonLegs);
            Append(result, enrLegs);
        }
        else
        {
            Append(result, enrLegs);
            Append(result, p.CommonLegs);
            Append(result, rwyLegs);
        }

        return new ProcedureLegsInfo
        {
            Icao = Icao, ProcType = q.ProcType!, Name = p.Name, Runway = runway, Transition = q.Transition, Legs = result,
        };
    }

    // Sklejenie części procedury: punkt końcowy jednej części bywa punktem IF następnej — nie dubluj go.
    private static void Append(List<LegInfo> target, List<LegInfo> legs)
    {
        foreach (var leg in legs)
        {
            if (target.Count > 0 && leg.LegType == "IF" && leg.Fix.Length > 0 && target[^1].Fix == leg.Fix) continue;
            target.Add(leg);
        }
    }

    private static bool Same(string a, string b) => string.Equals(a, b, StringComparison.OrdinalIgnoreCase);

    private static NavDataException NotFound(string message) => new(ErrorCodes.NotFound, message);
}

// Pomocnicze konwersje wspólne dla MSFS i danych przykładowych.
public static class NavMath
{
    public const double FtPerM = 3.28083989501;
    public const double MPerNm = 1852.0;

    public static double Round(double v, int digits) => double.IsFinite(v) ? Math.Round(v, digits) : 0;

    public static double NormDeg(double deg) => ((deg % 360) + 360) % 360;

    // Punkt odległy o distM metrów w kierunku bearingDeg (przybliżenie płaskie — dokładne dla długości pasa/podejścia).
    public static (double Lat, double Lon) Offset(double lat, double lon, double bearingDeg, double distM)
    {
        var b = bearingDeg * Math.PI / 180;
        var dLat = distM * Math.Cos(b) / 111_320.0;
        var dLon = distM * Math.Sin(b) / (111_320.0 * Math.Cos(lat * Math.PI / 180));
        return (lat + dLat, lon + dLon);
    }

    public static double DistanceNm(double lat1, double lon1, double lat2, double lon2)
    {
        const double r = 3440.065;
        var dLat = (lat2 - lat1) * Math.PI / 180;
        var dLon = (lon2 - lon1) * Math.PI / 180;
        var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2)
                + Math.Cos(lat1 * Math.PI / 180) * Math.Cos(lat2 * Math.PI / 180) * Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        return 2 * r * Math.Asin(Math.Min(1, Math.Sqrt(a)));
    }

    // "RW29", "rwy 9", "29" → "29"; "11l" → "11L"
    public static string NormalizeRunway(string s)
    {
        var r = s.Trim().ToUpperInvariant();
        if (r.StartsWith("RWY")) r = r[3..];
        else if (r.StartsWith("RW")) r = r[2..];
        r = r.Trim();
        if (r.Length > 0 && char.IsDigit(r[0]) && (r.Length == 1 || !char.IsDigit(r[1]))) r = "0" + r;
        return r;
    }

    // Numer i oznaczenie końca pasa w kodowaniu MSFS (1–36, 37+ = kierunki świata; 1 L, 2 R, 3 C, 4 W, 5 A, 6 B).
    public static string RunwayIdent(int number, int designator)
    {
        string[] compass = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        var num = number switch
        {
            >= 1 and <= 36 => number.ToString("D2"),
            >= 37 and <= 44 => compass[number - 37],
            _ => "",
        };
        var des = designator switch { 1 => "L", 2 => "R", 3 => "C", 4 => "W", 5 => "A", 6 => "B", _ => "" };
        return num + des;
    }
}
