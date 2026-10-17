namespace OpenAirBookLink.Core.Protocol;

// Migawka stanu samolotu wysyłana w wiadomości `state` (pole `data`).
// Nazwy i jednostki opisuje docs/link-protocol.md.
public sealed class FlightState
{
    public string Source { get; set; } = "none";
    public AircraftInfo Aircraft { get; set; } = new();
    public DateTime? SimTimeUtc { get; set; }
    public PositionData Position { get; set; } = new();
    public SpeedData Speed { get; set; } = new();
    public AttitudeData Attitude { get; set; } = new();
    public AutopilotData Autopilot { get; set; } = new();
    public RadiosData Radios { get; set; } = new();
    public ControlsData Controls { get; set; } = new();
    public LightsData Lights { get; set; } = new();
    public FuelData Fuel { get; set; } = new();
    public List<EngineData> Engines { get; set; } = new();
    public EnvironmentData Environment { get; set; } = new();
    public AxesData Axes { get; set; } = new();
    // Ostatnie przyziemienie (zatrzaśnięte); brak pola, dopóki samolot nie wylądował od startu Link.
    public TouchdownData? Touchdown { get; set; }

    public FlightState Clone()
    {
        var json = System.Text.Json.JsonSerializer.Serialize(this, LinkJson.Options);
        return System.Text.Json.JsonSerializer.Deserialize<FlightState>(json, LinkJson.Options)!;
    }
}

public sealed class AircraftInfo
{
    public string Title { get; set; } = "";
    public bool OnGround { get; set; }
    public bool Paused { get; set; }
    // "piston" | "jet" | "turboprop" | "helicopter" | "none"
    public string EngineType { get; set; } = "none";
    public string AtcModel { get; set; } = "";
    public string AtcType { get; set; } = "";
    // "airliner" | "bizjet" | "turboprop" | "ga" | "helicopter" — heurystyka AircraftClassifier
    public string Category { get; set; } = "ga";
}

public sealed class TouchdownData
{
    // Rośnie przy każdym przyziemieniu (także odbiciu) — telefon wykrywa nowe przyziemienie po zmianie seq.
    public long Seq { get; set; }
    // Czas przyziemienia, ms od epoki Unix (UTC)
    public long T { get; set; }
    public double VsFpm { get; set; }
    public double GForce { get; set; }
    public double PitchDeg { get; set; }
    public double BankDeg { get; set; }
    public double HeadingTrueDeg { get; set; }
    public double Lat { get; set; }
    public double Lon { get; set; }
    public double IasKt { get; set; }
    public double GsKt { get; set; }
}

public sealed class PositionData
{
    public double Lat { get; set; }
    public double Lon { get; set; }
    public double AltMslFt { get; set; }
    public double AltAglFt { get; set; }
    public double AltIndFt { get; set; }
}

public sealed class SpeedData
{
    public double IasKt { get; set; }
    public double TasKt { get; set; }
    public double GsKt { get; set; }
    public double Mach { get; set; }
    public double VsFpm { get; set; }
}

public sealed class AttitudeData
{
    public double PitchDeg { get; set; }
    public double BankDeg { get; set; }
    public double HdgTrueDeg { get; set; }
    public double HdgMagDeg { get; set; }
    public double TrackTrueDeg { get; set; }
}

public sealed class AutopilotData
{
    public bool Master { get; set; }
    public bool Fd { get; set; }
    public bool Athr { get; set; }
    public double HdgDeg { get; set; }
    public double AltFt { get; set; }
    public double VsFpm { get; set; }
    public double SpdKt { get; set; }
    public AutopilotModes Modes { get; set; } = new();
}

public sealed class AutopilotModes
{
    public bool Hdg { get; set; }
    public bool Nav { get; set; }
    public bool Apr { get; set; }
    public bool Loc { get; set; }
    public bool Alt { get; set; }
    public bool Vs { get; set; }
    public bool Flc { get; set; }
}

public sealed class RadiosData
{
    public RadioPair Com1 { get; set; } = new();
    public RadioPair Com2 { get; set; } = new();
    public RadioPair Nav1 { get; set; } = new();
    public RadioPair Nav2 { get; set; } = new();
    public TransponderData Transponder { get; set; } = new();
}

public sealed class RadioPair
{
    public double ActiveMhz { get; set; }
    public double StandbyMhz { get; set; }
}

public sealed class TransponderData
{
    public string Code { get; set; } = "2000";
    public string Mode { get; set; } = "standby";
    public bool Ident { get; set; }

    public static readonly string[] Modes = { "off", "standby", "test", "on", "alt", "ground" };

    public static string ModeName(int state) =>
        state >= 0 && state < Modes.Length ? Modes[state] : "off";
}

public sealed class ControlsData
{
    public bool GearDown { get; set; }
    public double GearPct { get; set; }
    public int FlapsIndex { get; set; }
    public int FlapsPositions { get; set; }
    public double FlapsPct { get; set; }
    public double SpoilersPct { get; set; }
    public bool SpoilersArmed { get; set; }
    public bool ParkingBrake { get; set; }
    public double ElevatorTrimPct { get; set; }
}

public sealed class LightsData
{
    public bool Beacon { get; set; }
    public bool Strobe { get; set; }
    public bool Nav { get; set; }
    public bool Landing { get; set; }
    public bool Taxi { get; set; }
    public bool Logo { get; set; }
    public bool Wing { get; set; }
    public bool Panel { get; set; }
    public bool Cabin { get; set; }
}

public sealed class FuelData
{
    public double TotalKg { get; set; }
    public FuelTanks Tanks { get; set; } = new();
}

public sealed class FuelTanks
{
    public double LeftKg { get; set; }
    public double CenterKg { get; set; }
    public double RightKg { get; set; }
}

public sealed class EngineData
{
    public double N1Pct { get; set; }
    public double N2Pct { get; set; }
    public double Rpm { get; set; }
    public double EgtC { get; set; }
    public double FuelFlowKgH { get; set; }
}

public sealed class EnvironmentData
{
    public double WindDirDeg { get; set; }
    public double WindSpdKt { get; set; }
    public double OatC { get; set; }
    public double QnhHpa { get; set; }
}

public sealed class AxesData
{
    public bool Active { get; set; }
}
