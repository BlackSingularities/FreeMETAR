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
    // Dane dla wyświetlaczy PFD/MFD (nastawy, radionawigacja, GPS, tryby FMA). Brak pola = źródło ich nie podaje.
    public AvionicsData? Avionics { get; set; }
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
    // Dźwignie (silnik 1; THROTTLE_SET/MIXTURE_SET/PROPELLER_SET ustawiają wszystkie silniki), 0–100 %
    public double ThrottlePct { get; set; }
    public double MixturePct { get; set; }
    public double PropPct { get; set; }
    // Kamera MSFS (CAMERA STATE): 2 kabina, 3 zewnętrzna, 4 dron, inne = pozostałe widoki
    public int CameraState { get; set; }
    // Kąty kamery odczytane z MSFS (stopnie; pochylenie + w górę, obrót + w prawo) — do sprawdzenia, czy rozglądanie działa
    public double CameraPitchDeg { get; set; }
    public double CameraYawDeg { get; set; }
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
    // Dźwignia mocy tego silnika, 0–100 % (THROTTLEn_SET)
    public double ThrottlePct { get; set; }
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

public sealed class AvionicsData
{
    // Wysokościomierz: nastawa (hPa) i tryb STD
    public double BaroHpa { get; set; }
    public bool BaroStd { get; set; }
    public double PressureAltFt { get; set; }
    public double RadioAltFt { get; set; }
    public double DecisionHeightFt { get; set; }
    // Przyrządy: kulka (−1 lewo … 1 prawo), zakręt (°/s, + w prawo), przeciążenie, kąt natarcia
    public double SlipBall { get; set; }
    public double TurnRateDps { get; set; }
    public double GForce { get; set; }
    public double AoaDeg { get; set; }
    // Wskazania przyrządów żyroskopowych (przy awariach odbiegają od rzeczywistego położenia)
    public double AttIndPitchDeg { get; set; }
    public double AttIndBankDeg { get; set; }
    public double HdgIndDeg { get; set; }
    public double SuctionInHg { get; set; }
    // Deklinacja magnetyczna, E +
    public double MagVarDeg { get; set; }
    public double TatC { get; set; }
    public SpeedLimits Speeds { get; set; } = new();
    public bool StallWarning { get; set; }
    public bool OverspeedWarning { get; set; }
    // none / outer / middle / inner
    public string Marker { get; set; } = "none";
    public NavReceiver Nav1 { get; set; } = new();
    public NavReceiver Nav2 { get; set; } = new();
    public AdfReceiver Adf { get; set; } = new();
    public GpsData Gps { get; set; } = new();
    public FlightDirector Fd { get; set; } = new();
    public ApModesExtra Ap { get; set; } = new();
    // Hamulce kół, 0–100 %
    public double BrakeLeftPct { get; set; }
    public double BrakeRightPct { get; set; }
}

// Prędkości charakterystyczne samolotu, kt (0 = brak danych)
public sealed class SpeedLimits
{
    public double Vs0Kt { get; set; }
    public double Vs1Kt { get; set; }
    public double VcKt { get; set; }
    public double VmoKt { get; set; }
}

public sealed class NavReceiver
{
    public bool HasNav { get; set; }
    public bool HasLoc { get; set; }
    public bool HasGs { get; set; }
    public bool HasDme { get; set; }
    public string Ident { get; set; } = "";
    // Kurs nastawiony (OBS), kurs lokalizera i radial, stopnie magnetyczne
    public double ObsDeg { get; set; }
    public double LocCourseDeg { get; set; }
    public double RadialDeg { get; set; }
    // Odchylenia w kropkach −2,5…2,5: CDI + = kurs w prawo, GS + = ścieżka powyżej
    public double CdiDots { get; set; }
    public double GsDots { get; set; }
    public double DmeNm { get; set; }
    // to / from / off
    public string ToFrom { get; set; } = "off";
}

public sealed class AdfReceiver
{
    public bool HasSignal { get; set; }
    // Namiar względny na NDB (0 = przed samolotem)
    public double RelBearingDeg { get; set; }
}

public sealed class GpsData
{
    public bool ActiveFlightPlan { get; set; }
    public bool ApproachActive { get; set; }
    public bool DrivesNav1 { get; set; }
    public string NextId { get; set; } = "";
    public string PrevId { get; set; } = "";
    public double NextDistNm { get; set; }
    public double NextBearingMagDeg { get; set; }
    public double DesiredTrackMagDeg { get; set; }
    public double CrossTrackNm { get; set; }
    public double NextEteSec { get; set; }
    public double DestEteSec { get; set; }
    // Pozycje punktów bieżącego odcinka (0 = brak) i miejsce w planie lotu symulatora
    public double NextLat { get; set; }
    public double NextLon { get; set; }
    public double PrevLat { get; set; }
    public double PrevLon { get; set; }
    public int WpIndex { get; set; }
    public int WpCount { get; set; }
}

public sealed class FlightDirector
{
    public bool Active { get; set; }
    // Polecenia dyrektora lotu: pochylenie + nos w górę, przechylenie + w prawo
    public double PitchDeg { get; set; }
    public double BankDeg { get; set; }
}

public sealed class ApModesExtra
{
    public bool AltArmed { get; set; }
    public bool GsArmed { get; set; }
    public bool GsActive { get; set; }
    public bool BackCourse { get; set; }
    public bool WingLeveler { get; set; }
    public bool PitchHold { get; set; }
    public bool AirspeedHold { get; set; }
    public bool MachHold { get; set; }
    public bool Toga { get; set; }
    public bool YawDamper { get; set; }
    public double MachVar { get; set; }
}
