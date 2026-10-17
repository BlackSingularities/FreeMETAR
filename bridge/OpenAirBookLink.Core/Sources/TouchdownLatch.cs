using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Core.Sources;

// Jedna klatka danych do wykrywania przyziemienia. Td* = wartości z SimVarów PLANE TOUCHDOWN *
// (null, gdy źródło ich nie ma — wtedy używa się ostatniej klatki w powietrzu).
public readonly record struct TouchdownSample(
    long TimeMs, long UnixMs, bool OnGround,
    double VsFpm, double GForce, double PitchDeg, double BankDeg, double HeadingTrueDeg,
    double Lat, double Lon, double IasKt, double GsKt,
    double? TdVsFpm = null, double? TdPitchDeg = null, double? TdBankDeg = null, double? TdLat = null, double? TdLon = null);

// Zatrzaskuje dane każdego przyziemienia (przejście powietrze → ziemia po co najmniej MinAirborneMs w powietrzu,
// więc odbicia też się liczą). Przez PeakWindowMs po kontakcie zbiera szczyt przeciążenia i odświeża
// wartości PLANE TOUCHDOWN *, które MSFS aktualizuje z opóźnieniem klatki.
public sealed class TouchdownLatch
{
    public const long MinAirborneMs = 250;
    public const long PeakWindowMs = 600;

    private long? _airborneSince;
    private bool? _lastOnGround;
    private TouchdownSample _lastAirborne;
    private long _windowEndMs = long.MinValue;
    private long _seq;

    public TouchdownData? Current { get; private set; }

    public TouchdownData? Update(in TouchdownSample s)
    {
        if (!s.OnGround)
        {
            _airborneSince ??= s.TimeMs;
            _lastAirborne = s;
        }
        else if (_lastOnGround == false && _airborneSince is { } since && s.TimeMs - since >= MinAirborneMs)
        {
            var air = _lastAirborne;
            Current = new TouchdownData
            {
                Seq = ++_seq,
                T = s.UnixMs,
                VsFpm = R(Vs(Fresh(s.TdVsFpm, air.TdVsFpm)) ?? air.VsFpm, 0),
                GForce = R(Math.Max(air.GForce, s.GForce), 2),
                PitchDeg = R(Fresh(s.TdPitchDeg, air.TdPitchDeg) ?? air.PitchDeg, 1),
                BankDeg = R(Fresh(s.TdBankDeg, air.TdBankDeg) ?? air.BankDeg, 1),
                HeadingTrueDeg = R(s.HeadingTrueDeg, 1),
                Lat = s.Lat,
                Lon = s.Lon,
                IasKt = R(s.IasKt, 1),
                GsKt = R(s.GsKt, 1),
            };
            RefreshPosition(s);
            _windowEndMs = s.TimeMs + PeakWindowMs;
        }
        else if (s.OnGround && Current != null && s.TimeMs <= _windowEndMs)
        {
            var air = _lastAirborne;
            Current.GForce = R(Math.Max(Current.GForce, s.GForce), 2);
            if (Vs(Fresh(s.TdVsFpm, air.TdVsFpm)) is { } vs) Current.VsFpm = R(vs, 0);
            if (Fresh(s.TdPitchDeg, air.TdPitchDeg) is { } p) Current.PitchDeg = R(p, 1);
            if (Fresh(s.TdBankDeg, air.TdBankDeg) is { } b) Current.BankDeg = R(b, 1);
            RefreshPosition(s);
        }

        if (s.OnGround) _airborneSince = null;
        _lastOnGround = s.OnGround;
        return Current;
    }

    private void RefreshPosition(in TouchdownSample s)
    {
        var air = _lastAirborne;
        if (Fresh(s.TdLat, air.TdLat) is { } la && la != 0 && Fresh(s.TdLon, air.TdLon) is { } lo && lo != 0)
        {
            Current!.Lat = la;
            Current.Lon = lo;
        }
    }

    // Wartości PLANE TOUCHDOWN * trzymają poprzednie przyziemienie, dopóki MSFS ich nie nadpisze —
    // uznaj je za nowe tylko, gdy różnią się od widzianych jeszcze w powietrzu.
    private static double? Fresh(double? onGround, double? airborne) =>
        onGround is { } v && double.IsFinite(v) && (airborne is not { } a || a != v) ? v : null;

    // PLANE TOUCHDOWN NORMAL VELOCITY bywa dodatnia przy opadaniu — w protokole opadanie jest ujemne.
    private static double? Vs(double? v) => v is { } x && x != 0 && double.IsFinite(x) ? -Math.Abs(x) : null;

    private static double R(double v, int digits) => double.IsFinite(v) ? Math.Round(v, digits) : 0;
}

// Heurystyka kategorii samolotu dla profili paneli w telefonie.
public static class AircraftClassifier
{
    private static readonly string[] BizjetWords =
    {
        "CITATION", "LEARJET", "PHENOM", "GULFSTREAM", "CHALLENGER", "FALCON", "GLOBAL", "HONDAJET", "HONDA JET",
        "ECLIPSE", "VISION", "PRAETOR", "LEGACY", "PILATUS PC-24", "PC-24", "SF50", "CJ4",
    };

    private static readonly string[] AirlinerWords =
    {
        "A300", "A310", "A318", "A319", "A320", "A321", "A330", "A340", "A350", "A380", "BOEING 7", "B7", "737", "747",
        "757", "767", "777", "787", "CRJ", "E170", "E175", "E190", "E195", "ERJ", "EMBRAER E", "MD-11", "MD-80",
        "DC-10", "ATR", "DASH 8", "DHC-8", "Q400", "SAAB 340", "CONCORDE", "BAE 146", "AVRO",
    };

    // engineType: wartość SimVaru ENGINE TYPE (0 piston, 1 jet, 2 none, 3 helo turbine, 4 unsupported, 5 turboprop)
    public static string EngineTypeName(int engineType) => engineType switch
    {
        0 => "piston",
        1 => "jet",
        3 => "helicopter",
        5 => "turboprop",
        _ => "none",
    };

    public static string Classify(string engineType, int engineCount, double maxGrossWeightLbs, string title, string simCategory)
    {
        var text = $"{title} {simCategory}".ToUpperInvariant();
        if (engineType == "helicopter" || text.Contains("HELICOPTER")) return "helicopter";

        var airlinerName = AirlinerWords.Any(text.Contains);
        var bizName = BizjetWords.Any(text.Contains);

        switch (engineType)
        {
            case "jet":
                if (bizName && !airlinerName) return "bizjet";
                if (airlinerName || engineCount >= 3 || maxGrossWeightLbs >= 100_000) return "airliner";
                return "bizjet";
            case "turboprop":
                if (airlinerName || maxGrossWeightLbs >= 35_000) return "airliner";
                return "turboprop";
            default:
                return "ga";
        }
    }

    // MSFS zwraca ATC MODEL/ATC TYPE czasem jako klucz lokalizacji, np. "TT:ATCCOM.AC_MODEL_A320.0.text".
    public static string CleanAtcString(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "";
        var s = raw.Trim();
        if (!s.StartsWith("TT:", StringComparison.OrdinalIgnoreCase)) return s;
        var key = s[3..];
        if (key.EndsWith(".text", StringComparison.OrdinalIgnoreCase)) key = key[..^5];
        if (key.EndsWith(".0")) key = key[..^2];
        var dot = key.LastIndexOf('.');
        if (dot >= 0) key = key[(dot + 1)..];
        foreach (var prefix in new[] { "AC_MODEL_", "AC_MODEL ", "ATC_NAME_", "ATC_NAME " })
            if (key.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return key[prefix.Length..].Trim();
        return key.Trim();
    }
}
