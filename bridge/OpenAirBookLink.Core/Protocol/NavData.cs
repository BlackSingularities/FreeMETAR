using System.Text.Json.Serialization;

namespace OpenAirBookLink.Core.Protocol;

// Odpowiedź na `navdata_request` (PC → telefon). `data` zależy od `kind`, kształty opisuje docs/link-protocol.md.
public sealed class NavdataMessage : LinkMessage
{
    public override string Type => "navdata";
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Id { get; init; }
    public required string Kind { get; init; }
    public required object Data { get; init; }
}

public static class NavdataKinds
{
    public const string Airport = "airport";
    public const string Procedures = "procedures";
    public const string ProcedureLegs = "procedure_legs";
    public const string Fix = "fix";
}

public static class ProcTypes
{
    public const string Departure = "departure";
    public const string Arrival = "arrival";
    public const string Approach = "approach";
}

// kind = airport
public sealed class AirportInfo
{
    public required string Icao { get; init; }
    public string Name { get; init; } = "";
    public double Lat { get; init; }
    public double Lon { get; init; }
    public double ElevFt { get; init; }
    public List<RunwayInfo> Runways { get; init; } = new();
    public List<FrequencyInfo> Frequencies { get; init; } = new();
}

// Jeden koniec pasa (MSFS opisuje pas jako parę końców — tu każdy koniec osobno).
public sealed class RunwayInfo
{
    public required string Ident { get; init; }
    public double HeadingTrueDeg { get; init; }
    public double LengthFt { get; init; }
    public double WidthFt { get; init; }
    public double ThresholdLat { get; init; }
    public double ThresholdLon { get; init; }
    public double ElevFt { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public IlsInfo? Ils { get; init; }
}

public sealed class IlsInfo
{
    public required string Ident { get; init; }
    public double FreqMhz { get; init; }
    public double LocCourseDeg { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? GsAngleDeg { get; init; }
}

public static class FrequencyTypes
{
    public const string Atis = "ATIS", Clearance = "CLR", Ground = "GND", Tower = "TWR", Approach = "APP",
        Departure = "DEP", Center = "CTR", Unicom = "UNICOM", Other = "OTHER";
}

public sealed class FrequencyInfo
{
    public required string Type { get; init; }
    public string Name { get; init; } = "";
    public double Mhz { get; init; }
}

// kind = procedures
public sealed class ProceduresInfo
{
    public required string Icao { get; init; }
    public List<ProcedureInfo> Departures { get; init; } = new();
    public List<ProcedureInfo> Arrivals { get; init; } = new();
    public List<ApproachInfo> Approaches { get; init; } = new();
}

public sealed class ProcedureInfo
{
    public required string Name { get; init; }
    public List<string> Runways { get; init; } = new();
    public List<string> Transitions { get; init; } = new();
}

public static class ApproachTypes
{
    public const string Ils = "ILS", Loc = "LOC", Rnav = "RNAV", Vor = "VOR", Ndb = "NDB", Gps = "GPS", Other = "OTHER";
}

public sealed class ApproachInfo
{
    public required string Name { get; init; }
    public required string Type { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Runway { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Suffix { get; init; }
    public List<string> Transitions { get; init; } = new();
}

// kind = procedure_legs
public sealed class ProcedureLegsInfo
{
    public required string Icao { get; init; }
    public required string ProcType { get; init; }
    public required string Name { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Runway { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Transition { get; init; }
    public List<LegInfo> Legs { get; init; } = new();
    public List<LegInfo> Missed { get; init; } = new();
}

public static class AltDescs
{
    public const string At = "AT", Above = "ABOVE", Below = "BELOW", Between = "BETWEEN";
}

// Odcinek procedury. Pola null zawsze są w JSON (stały kształt dla telefonu).
public sealed class LegInfo
{
    // Pusty, gdy odcinek nie kończy się na punkcie (np. VA — kurs do wysokości).
    public string Fix { get; init; } = "";
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? Lat { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? Lon { get; init; }
    public required string LegType { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? AltDesc { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? Alt1Ft { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? Alt2Ft { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? SpeedKt { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? CourseDeg { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? DistNm { get; init; }
    public bool FlyOver { get; init; }
}

// kind = fix
public sealed class FixSearchInfo
{
    public List<FixResult> Results { get; init; } = new();
}

public static class FixTypes
{
    public const string Waypoint = "waypoint", Vor = "vor", Ndb = "ndb", Airport = "airport";
}

public sealed class FixResult
{
    public required string Ident { get; init; }
    public required string Type { get; init; }
    public double Lat { get; init; }
    public double Lon { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public double? FreqMhz { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Region { get; init; }
}
