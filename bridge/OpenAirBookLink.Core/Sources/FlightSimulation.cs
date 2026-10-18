using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Core.Sources;

public enum SimPhase { Parked, TakeoffRoll, Climb, Cruise, Descent, Approach, Rollout, Stopped }

// Uproszczony lot testowy EPWA RWY33 → EPGD RWY29 bez MSFS.
// Nie jest to model fizyczny — ma dawać wiarygodne, spójne liczby dla ekranów aplikacji
// i reagować na komendy. Krok symulacji wywołuje SimulationSource (albo test).
public sealed class FlightSimulation
{
    private const double MagVar = 6.0; // °E w Polsce
    private const double NmPerDegLat = 60.0;

    public static readonly (double Lat, double Lon, double ElevFt, double RwyTrue) Departure = (52.1550, 20.9740, 362, 331);
    public static readonly (double Lat, double Lon, double ElevFt, double RwyTrue) Arrival = (54.3790, 18.4880, 489, 292);

    private readonly FlightState _s = new() { Source = "simulation" };
    private double _phaseTime;
    private double _stoppedTime;
    private double _targetCruiseFt = 24000;
    private double _targetSpdKt = 290;
    private bool _manualSpeed;
    private bool _manualThrottle;
    private long _touchdownSeq;

    public FlightSimulation()
    {
        Reset();
    }

    public SimPhase Phase { get; private set; }
    public double DistanceToArrivalNm => DistanceNm(_s.Position.Lat, _s.Position.Lon, Arrival.Lat, Arrival.Lon);
    public FlightState State => _s;

    public void Reset()
    {
        Phase = SimPhase.Parked;
        _phaseTime = 0;
        _stoppedTime = 0;
        _targetCruiseFt = 24000;
        _targetSpdKt = 290;
        _manualSpeed = false;

        _s.Aircraft = new AircraftInfo
        {
            Title = "OpenAirBook Sim A320 (symulacja)", OnGround = true,
            EngineType = "jet", AtcModel = "A320", AtcType = "AIRBUS", Category = "airliner",
        };
        _s.Position = new PositionData { Lat = Departure.Lat, Lon = Departure.Lon, AltMslFt = Departure.ElevFt, AltIndFt = Departure.ElevFt };
        _s.Speed = new SpeedData();
        _s.Attitude = new AttitudeData { HdgTrueDeg = Departure.RwyTrue, HdgMagDeg = Norm(Departure.RwyTrue - MagVar), TrackTrueDeg = Departure.RwyTrue };
        _s.Autopilot = new AutopilotData { Fd = true, HdgDeg = Math.Round(Norm(Departure.RwyTrue - MagVar)), AltFt = _targetCruiseFt, SpdKt = 250, VsFpm = 0 };
        _s.Radios = new RadiosData
        {
            Com1 = new RadioPair { ActiveMhz = 121.900, StandbyMhz = 118.300 },  // EPWA GND / TWR
            Com2 = new RadioPair { ActiveMhz = 121.500, StandbyMhz = 122.800 },
            Nav1 = new RadioPair { ActiveMhz = 110.300, StandbyMhz = 113.450 },
            Nav2 = new RadioPair { ActiveMhz = 113.450, StandbyMhz = 110.300 },
            Transponder = new TransponderData { Code = "2000", Mode = "alt" },
        };
        _s.Controls = new ControlsData { GearDown = true, GearPct = 100, FlapsIndex = 1, FlapsPositions = 5, FlapsPct = 25, SpoilersArmed = true, MixturePct = 100, PropPct = 100, CameraState = 2 };
        _manualThrottle = false;
        _s.Lights = new LightsData { Beacon = true, Strobe = true, Nav = true, Landing = true, Taxi = false, Logo = true, Panel = true, Cabin = true };
        _s.Fuel = new FuelData { TotalKg = 6400, Tanks = new FuelTanks { LeftKg = 2700, CenterKg = 1000, RightKg = 2700 } };
        _s.Engines = new List<EngineData> { new(), new() };
        _s.Environment = new EnvironmentData { WindDirDeg = 270, WindSpdKt = 18, QnhHpa = 1013.2 };
        UpdateEngines(20, 0);
    }

    public void Step(double dt, AxisOutput axes, DateTime utcNow)
    {
        _phaseTime += dt;
        var p = _s.Position;
        var ap = _s.Autopilot;
        var dist = DistanceToArrivalNm;
        var brgToArr = BearingDeg(p.Lat, p.Lon, Arrival.Lat, Arrival.Lon);
        var manual = !axes.Final; // telefon steruje osiami

        double targetHdg = _s.Attitude.HdgTrueDeg;
        double targetVs = 0, targetIas = _s.Speed.IasKt, n1 = 20;

        switch (Phase)
        {
            case SimPhase.Parked:
                if (_phaseTime > 5 && !_s.Controls.ParkingBrake) Advance(SimPhase.TakeoffRoll);
                break;

            case SimPhase.TakeoffRoll:
                n1 = 92;
                targetHdg = Departure.RwyTrue;
                targetIas = 160;
                if (_s.Speed.IasKt >= 145) Advance(SimPhase.Climb);
                break;

            case SimPhase.Climb:
                n1 = 90;
                targetHdg = HeadingTarget(brgToArr);
                targetIas = p.AltMslFt < 10000 ? 250 : _targetSpdKt;
                targetVs = p.AltMslFt < 10000 ? 2500 : 1800;
                ap.Modes.Flc = true;
                if (_s.Position.AltAglFt > 400 && _s.Controls.GearDown) SetGear(false);
                if (_s.Position.AltAglFt > 3000 && _s.Controls.FlapsIndex > 0) SetFlaps(0);
                if (_s.Position.AltAglFt > 1000 && !ap.Master) { ap.Master = true; ap.Athr = true; ap.Modes.Nav = !ap.Modes.Hdg; }
                if (p.AltMslFt > 10000 && _s.Lights.Landing) _s.Lights.Landing = false;
                if (p.AltMslFt >= _targetCruiseFt - 50) Advance(SimPhase.Cruise);
                if (TopOfDescent(dist)) Advance(SimPhase.Descent);
                break;

            case SimPhase.Cruise:
                n1 = 82;
                targetHdg = HeadingTarget(brgToArr);
                targetIas = _targetSpdKt;
                targetVs = Math.Clamp((_targetCruiseFt - p.AltMslFt) * 2, -1500, 1500);
                ap.Modes.Flc = false;
                ap.Modes.Alt = Math.Abs(_targetCruiseFt - p.AltMslFt) < 100;
                ap.Modes.Vs = !ap.Modes.Alt;
                if (TopOfDescent(dist)) Advance(SimPhase.Descent);
                break;

            case SimPhase.Descent:
                n1 = 35;
                targetHdg = HeadingTarget(brgToArr);
                targetIas = p.AltMslFt < 10000 ? 220 : Math.Min(_targetSpdKt, 280);
                targetVs = -1800;
                ap.Modes.Alt = false;
                ap.Modes.Vs = true;
                ap.AltFt = 3000;
                if (p.AltMslFt < 10000 && !_s.Lights.Landing) _s.Lights.Landing = true;
                if (dist < 12 || p.AltMslFt < 3500) Advance(SimPhase.Approach);
                break;

            case SimPhase.Approach:
                n1 = 55;
                targetHdg = HeadingTarget(brgToArr);
                targetIas = dist > 6 ? 170 : 140;
                // ścieżka 3° (~318 ft/NM) nad progiem + 50 ft
                var pathAlt = Arrival.ElevFt + 50 + dist * 318;
                targetVs = Math.Clamp((pathAlt - p.AltMslFt) * 3 - _s.Speed.GsKt * 5.3, -1500, 0);
                ap.Modes.Vs = false;
                ap.Modes.Nav = false;
                ap.Modes.Apr = true;
                ap.Modes.Loc = true;
                if (dist < 8 && !_s.Controls.GearDown) SetGear(true);
                if (dist < 10 && _s.Controls.FlapsIndex < 2) SetFlaps(2);
                if (dist < 5 && _s.Controls.FlapsIndex < 4) SetFlaps(4);
                if (dist < 1 && ap.Master) { ap.Master = false; ap.Athr = false; }
                if (_s.Position.AltAglFt < 5 || dist < 0.2)
                {
                    Advance(SimPhase.Rollout);
                    LatchTouchdown(utcNow);
                }
                break;

            case SimPhase.Rollout:
                n1 = _phaseTime < 15 ? 70 : 20;
                targetHdg = _s.Attitude.HdgTrueDeg;
                targetIas = 0;
                if (_s.Speed.IasKt < 20) Advance(SimPhase.Stopped);
                break;

            case SimPhase.Stopped:
                n1 = 20;
                targetIas = 0;
                _stoppedTime += dt;
                if (_stoppedTime > 30) { Reset(); return; }
                break;
        }

        var airborne = Phase is SimPhase.Climb or SimPhase.Cruise or SimPhase.Descent or SimPhase.Approach;

        // Ręczne sterowanie osiami z telefonu przejmuje kurs i prędkość pionową.
        double targetBank;
        if (airborne && manual)
        {
            targetBank = axes.Aileron * 35;
            targetVs = _s.Speed.VsFpm + axes.Elevator * 3000 * dt;
            targetVs = Math.Clamp(targetVs, -4000, 4000);
        }
        else if (airborne)
        {
            var err = AngleDiff(targetHdg, _s.Attitude.HdgTrueDeg);
            targetBank = Math.Clamp(err * 2, -25, 25);
        }
        else
        {
            targetBank = 0;
            targetVs = 0;
        }

        // Dynamika: przechylenie, kurs, prędkość, wysokość
        var att = _s.Attitude;
        att.BankDeg = Approach(att.BankDeg, targetBank, 8 * dt);
        var tasMs = Math.Max(_s.Speed.TasKt, 1) * 0.5144;
        if (airborne)
        {
            var turnRate = 9.81 * Math.Tan(att.BankDeg * Math.PI / 180) / tasMs * 180 / Math.PI;
            att.HdgTrueDeg = Norm(att.HdgTrueDeg + turnRate * dt);
        }
        else if (Phase == SimPhase.TakeoffRoll)
        {
            att.HdgTrueDeg = Departure.RwyTrue;
        }
        att.HdgMagDeg = Norm(att.HdgTrueDeg - MagVar);

        var spd = _s.Speed;
        var accel = Phase switch
        {
            SimPhase.TakeoffRoll => 3.5,
            SimPhase.Rollout => 4.0 + (_s.Controls.SpoilersPct > 50 ? 1.0 : 0),
            _ => 1.5,
        };
        if (_s.Controls.ParkingBrake && !airborne) targetIas = 0;
        spd.IasKt = Math.Max(0, Approach(spd.IasKt, targetIas, accel * dt));
        spd.VsFpm = airborne ? Approach(spd.VsFpm, targetVs, 600 * dt) : 0;
        p.AltMslFt += spd.VsFpm / 60 * dt;
        if (Phase == SimPhase.Climb && p.AltMslFt > _targetCruiseFt) p.AltMslFt = _targetCruiseFt;

        var groundElev = GroundElevation(dist);
        if (!airborne || p.AltMslFt < groundElev) p.AltMslFt = Math.Max(p.AltMslFt, groundElev);
        if (!airborne) p.AltMslFt = groundElev;
        p.AltAglFt = Math.Max(0, p.AltMslFt - groundElev);
        p.AltIndFt = p.AltMslFt + (_s.Environment.QnhHpa - 1013.25) * 27;

        spd.TasKt = spd.IasKt * (1 + 0.02 * p.AltMslFt / 1000);
        var oatC = 15 - 1.98 * p.AltMslFt / 1000 + 3;
        _s.Environment.OatC = Math.Round(oatC, 1);
        spd.Mach = spd.TasKt / (661.47 * Math.Sqrt((oatC + 273.15) / 288.15));

        // Wiatr: wektor prędkości nad ziemią
        var windTo = (_s.Environment.WindDirDeg + 180) * Math.PI / 180;
        var windKt = airborne ? _s.Environment.WindSpdKt : 0;
        var hdgRad = att.HdgTrueDeg * Math.PI / 180;
        var vn = spd.TasKt * Math.Cos(hdgRad) + windKt * Math.Cos(windTo);
        var ve = spd.TasKt * Math.Sin(hdgRad) + windKt * Math.Sin(windTo);
        spd.GsKt = Math.Sqrt(vn * vn + ve * ve);
        att.TrackTrueDeg = spd.GsKt > 1 ? Norm(Math.Atan2(ve, vn) * 180 / Math.PI) : att.HdgTrueDeg;

        var distNm = spd.GsKt * dt / 3600;
        p.Lat += distNm * Math.Cos(att.TrackTrueDeg * Math.PI / 180) / NmPerDegLat;
        p.Lon += distNm * Math.Sin(att.TrackTrueDeg * Math.PI / 180) / (NmPerDegLat * Math.Cos(p.Lat * Math.PI / 180));

        var gamma = spd.TasKt > 30 ? Math.Atan(spd.VsFpm / (spd.TasKt * 101.27)) * 180 / Math.PI : 0;
        att.PitchDeg = airborne ? gamma + 2.5 : (Phase == SimPhase.TakeoffRoll && spd.IasKt > 140 ? 8 : 0);

        _s.Aircraft.OnGround = !airborne;
        _s.SimTimeUtc = new DateTime(utcNow.Ticks - utcNow.Ticks % TimeSpan.TicksPerSecond, DateTimeKind.Utc);
        _s.Axes.Active = manual;
        if (!_manualSpeed) ap.SpdKt = Math.Round(targetIas);
        ap.VsFpm = Math.Round(targetVs / 100) * 100;
        if (Phase is SimPhase.Climb or SimPhase.Cruise) ap.AltFt = _targetCruiseFt;
        if (Phase == SimPhase.Rollout && _s.Controls.SpoilersArmed) { _s.Controls.SpoilersPct = 100; _s.Controls.SpoilersArmed = false; }

        // Ręczna dźwignia mocy (gdy automat ciągu wyłączony) — tylko wskazania silników, tor lotu zostaje scenariuszem
        if (_manualThrottle && !_s.Autopilot.Athr) n1 = 20 + _s.Controls.ThrottlePct * 0.8;
        else _s.Controls.ThrottlePct = Math.Round(Math.Clamp((n1 - 20) / 0.8, 0, 100));
        UpdateEngines(_s.Controls.ParkingBrake && !airborne ? 20 : n1, dt);
    }

    public CommandResult Execute(ValidatedCommand cmd)
    {
        var ap = _s.Autopilot;
        var r = _s.Radios;
        var c = _s.Controls;
        var l = _s.Lights;
        var v = cmd.Value;

        switch (cmd.Spec.Name)
        {
            case "AP_MASTER": ap.Master = !ap.Master; break;
            case "AUTOPILOT_ON": ap.Master = true; break;
            case "AUTOPILOT_OFF": ap.Master = false; break;
            case "TOGGLE_FLIGHT_DIRECTOR": ap.Fd = !ap.Fd; break;
            case "AUTO_THROTTLE_ARM": ap.Athr = !ap.Athr; break;
            case "YAW_DAMPER_TOGGLE": break;
            case "AP_HDG_HOLD": ap.Modes.Hdg = !ap.Modes.Hdg; if (ap.Modes.Hdg) ap.Modes.Nav = false; break;
            case "AP_NAV1_HOLD": ap.Modes.Nav = !ap.Modes.Nav; if (ap.Modes.Nav) ap.Modes.Hdg = false; break;
            case "AP_APR_HOLD": ap.Modes.Apr = !ap.Modes.Apr; break;
            case "AP_LOC_HOLD": ap.Modes.Loc = !ap.Modes.Loc; break;
            case "AP_BC_HOLD": break;
            case "AP_ALT_HOLD": ap.Modes.Alt = !ap.Modes.Alt; break;
            case "AP_VS_HOLD": ap.Modes.Vs = !ap.Modes.Vs; break;
            case "FLIGHT_LEVEL_CHANGE": ap.Modes.Flc = !ap.Modes.Flc; break;
            case "AP_PANEL_SPEED_HOLD": _manualSpeed = !_manualSpeed; break;
            case "AP_AIRSPEED_HOLD": _manualSpeed = !_manualSpeed; break;
            case "AP_N1_HOLD": ap.Athr = !ap.Athr; break;
            case "AP_PANEL_MACH_HOLD": break;
            case "AP_MACH_VAR_SET": SetSpeed(MachToIas(v / 100.0)); break;
            case "AP_BANK_HOLD": break;
            case "AP_WING_LEVELER": ap.Modes.Hdg = false; ap.Modes.Nav = false; break;
            case "HEADING_BUG_SET": ap.HdgDeg = v; break;
            case "HEADING_BUG_INC": ap.HdgDeg = Norm(ap.HdgDeg + 1); break;
            case "HEADING_BUG_DEC": ap.HdgDeg = Norm(ap.HdgDeg - 1); break;
            case "AP_ALT_VAR_SET_ENGLISH": SetCruise(v); break;
            case "AP_ALT_VAR_INC": SetCruise(_targetCruiseFt + 100); break;
            case "AP_ALT_VAR_DEC": SetCruise(_targetCruiseFt - 100); break;
            case "AP_VS_VAR_SET_ENGLISH": ap.VsFpm = v; break;
            case "AP_VS_VAR_INC": ap.VsFpm += 100; break;
            case "AP_VS_VAR_DEC": ap.VsFpm -= 100; break;
            case "AP_SPD_VAR_SET": SetSpeed(v); break;
            case "AP_SPD_VAR_INC": SetSpeed(_targetSpdKt + 1); break;
            case "AP_SPD_VAR_DEC": SetSpeed(_targetSpdKt - 1); break;

            case "COM_RADIO_SET_HZ": r.Com1.ActiveMhz = Mhz(v); break;
            case "COM_STBY_RADIO_SET_HZ": r.Com1.StandbyMhz = Mhz(v); break;
            case "COM2_RADIO_SET_HZ": r.Com2.ActiveMhz = Mhz(v); break;
            case "COM2_STBY_RADIO_SET_HZ": r.Com2.StandbyMhz = Mhz(v); break;
            case "NAV1_RADIO_SET_HZ": r.Nav1.ActiveMhz = Mhz(v); break;
            case "NAV1_STBY_SET_HZ": r.Nav1.StandbyMhz = Mhz(v); break;
            case "NAV2_RADIO_SET_HZ": r.Nav2.ActiveMhz = Mhz(v); break;
            case "NAV2_STBY_SET_HZ": r.Nav2.StandbyMhz = Mhz(v); break;
            case "COM_STBY_RADIO_SWAP": Swap(r.Com1); break;
            case "COM2_RADIO_SWAP": Swap(r.Com2); break;
            case "NAV1_RADIO_SWAP": Swap(r.Nav1); break;
            case "NAV2_RADIO_SWAP": Swap(r.Nav2); break;

            case "XPNDR_SET": r.Transponder.Code = CommandAllowlist.DecodeSquawk(cmd.Data); break;
            case "XPNDR_IDENT_ON": r.Transponder.Ident = true; break;
            case "XPNDR_MODE": r.Transponder.Mode = TransponderData.ModeName((int)v); break;

            case "GEAR_UP": SetGear(false); break;
            case "GEAR_DOWN": SetGear(true); break;
            case "GEAR_TOGGLE": SetGear(!c.GearDown); break;
            case "FLAPS_UP": SetFlaps(0); break;
            case "FLAPS_DOWN": SetFlaps(c.FlapsPositions - 1); break;
            case "FLAPS_INCR": SetFlaps(c.FlapsIndex + 1); break;
            case "FLAPS_DECR": SetFlaps(c.FlapsIndex - 1); break;
            case "FLAPS_SET": SetFlaps((int)Math.Round(v / 16383.0 * (c.FlapsPositions - 1))); break;
            case "SPOILERS_ON": c.SpoilersPct = 100; break;
            case "SPOILERS_OFF": c.SpoilersPct = 0; break;
            case "SPOILERS_TOGGLE": c.SpoilersPct = c.SpoilersPct > 0 ? 0 : 100; break;
            case "SPOILERS_ARM_TOGGLE": c.SpoilersArmed = !c.SpoilersArmed; break;
            case "SPOILERS_SET": c.SpoilersPct = Math.Round(v / 16383.0 * 100); break;
            case "PARKING_BRAKES": c.ParkingBrake = !c.ParkingBrake; break;
            case "PARKING_BRAKE_SET": c.ParkingBrake = v == 1; break;

            case "TOGGLE_BEACON_LIGHTS": l.Beacon = !l.Beacon; break;
            case "STROBES_TOGGLE": l.Strobe = !l.Strobe; break;
            case "TOGGLE_NAV_LIGHTS": l.Nav = !l.Nav; break;
            case "LANDING_LIGHTS_TOGGLE": l.Landing = !l.Landing; break;
            case "TOGGLE_TAXI_LIGHTS": l.Taxi = !l.Taxi; break;
            case "TOGGLE_LOGO_LIGHTS": l.Logo = !l.Logo; break;
            case "TOGGLE_WING_LIGHTS": l.Wing = !l.Wing; break;
            case "PANEL_LIGHTS_TOGGLE": l.Panel = !l.Panel; break;
            case "TOGGLE_CABIN_LIGHTS": l.Cabin = !l.Cabin; break;

            case "ELEV_TRIM_UP": c.ElevatorTrimPct = Math.Clamp(c.ElevatorTrimPct + 1, -100, 100); break;
            case "ELEV_TRIM_DN": c.ElevatorTrimPct = Math.Clamp(c.ElevatorTrimPct - 1, -100, 100); break;
            case "ELEVATOR_TRIM_SET": c.ElevatorTrimPct = Math.Round(v / 16383.0 * 100, 1); break;
            case "AILERON_TRIM_LEFT":
            case "AILERON_TRIM_RIGHT":
            case "RUDDER_TRIM_LEFT":
            case "RUDDER_TRIM_RIGHT":
                break;

            case "THROTTLE_SET": c.ThrottlePct = Math.Round(v / 16383.0 * 100); _manualThrottle = true; break;
            case "THROTTLE_CUT": c.ThrottlePct = 0; _manualThrottle = true; break;
            case "THROTTLE_FULL": c.ThrottlePct = 100; _manualThrottle = true; break;
            case "THROTTLE_REVERSE_THRUST_TOGGLE": break;
            case "AUTO_THROTTLE_TO_GA": c.ThrottlePct = 100; _manualThrottle = false; break;
            case "MIXTURE_SET": c.MixturePct = Math.Round(v / 16383.0 * 100); break;
            case "PROPELLER_SET": c.PropPct = Math.Round(v / 16383.0 * 100); break;
            case "CAMERA_STATE": c.CameraState = (int)v; break;
            case "CAMERA_PITCH_SET": break;
            case "CAMERA_YAW_SET": break;

            default:
                return CommandResult.Fail(ErrorCodes.NotAllowed, $"Symulacja nie obsługuje {cmd.Spec.Name}");
        }
        return CommandResult.Success;
    }

    // Ustaw samolot na prostej do EPGD RWY 29 (ścieżka 3°, podwozie i klapy wypuszczone).
    public void PositionOnFinal(double distNm)
    {
        Reset();
        Phase = SimPhase.Approach;
        var back = Norm(Arrival.RwyTrue + 180);
        var p = _s.Position;
        p.Lat = Arrival.Lat + distNm * Math.Cos(back * Math.PI / 180) / NmPerDegLat;
        p.Lon = Arrival.Lon + distNm * Math.Sin(back * Math.PI / 180) / (NmPerDegLat * Math.Cos(p.Lat * Math.PI / 180));
        p.AltMslFt = Arrival.ElevFt + 50 + distNm * 318;
        p.AltAglFt = p.AltMslFt - Arrival.ElevFt;
        p.AltIndFt = p.AltMslFt;
        _s.Speed = new SpeedData { IasKt = 145, TasKt = 147, GsKt = 140, VsFpm = -750 };
        _s.Attitude = new AttitudeData { HdgTrueDeg = Arrival.RwyTrue, HdgMagDeg = Norm(Arrival.RwyTrue - MagVar), TrackTrueDeg = Arrival.RwyTrue, PitchDeg = 2.5 };
        _s.Autopilot.Master = true;
        _s.Autopilot.Athr = true;
        _s.Autopilot.AltFt = 3000;
        _s.Aircraft.OnGround = false;
        _s.Lights.Landing = true;
        _s.Radios.Nav1 = new RadioPair { ActiveMhz = 109.300, StandbyMhz = 110.900 };
        SetGear(true);
        SetFlaps(4);
        UpdateEngines(55, 0);
    }

    // Przyziemienie w symulacji: stałe, wiarygodne wartości (−180 ft/min, 1,2 g).
    private void LatchTouchdown(DateTime utcNow)
    {
        _s.Touchdown = new TouchdownData
        {
            Seq = ++_touchdownSeq,
            T = new DateTimeOffset(utcNow).ToUnixTimeMilliseconds(),
            VsFpm = -180,
            GForce = 1.2,
            PitchDeg = 3.5,
            BankDeg = -0.4,
            HeadingTrueDeg = Math.Round(_s.Attitude.HdgTrueDeg, 1),
            Lat = Math.Round(_s.Position.Lat, 6),
            Lon = Math.Round(_s.Position.Lon, 6),
            IasKt = Math.Round(_s.Speed.IasKt, 1),
            GsKt = Math.Round(_s.Speed.GsKt, 1),
        };
    }

    private double MachToIas(double mach)
    {
        var oatC = _s.Environment.OatC;
        var tas = mach * 661.47 * Math.Sqrt((oatC + 273.15) / 288.15);
        return tas / (1 + 0.02 * _s.Position.AltMslFt / 1000);
    }

    private void Advance(SimPhase next)
    {
        Phase = next;
        _phaseTime = 0;
    }

    private double HeadingTarget(double routeBearing) =>
        _s.Autopilot.Modes.Hdg ? Norm(_s.Autopilot.HdgDeg + MagVar) : routeBearing;

    private bool TopOfDescent(double distNm)
    {
        var needed = (_s.Position.AltMslFt - Arrival.ElevFt - 3000) / 1000 * 3.2 + 12;
        return distNm <= needed;
    }

    private double GroundElevation(double distToArrNm)
    {
        var total = DistanceNm(Departure.Lat, Departure.Lon, Arrival.Lat, Arrival.Lon);
        var f = Math.Clamp(1 - distToArrNm / total, 0, 1);
        return Departure.ElevFt + (Arrival.ElevFt - Departure.ElevFt) * f;
    }

    private void SetCruise(double ft)
    {
        _targetCruiseFt = Math.Clamp(ft, 3000, 41000);
        _s.Autopilot.AltFt = _targetCruiseFt;
        if (Phase == SimPhase.Cruise && _targetCruiseFt > _s.Position.AltMslFt + 100) Advance(SimPhase.Climb);
    }

    private void SetSpeed(double kt)
    {
        _manualSpeed = true;
        _targetSpdKt = Math.Clamp(kt, 120, 340);
        _s.Autopilot.SpdKt = _targetSpdKt;
    }

    private void SetGear(bool down)
    {
        _s.Controls.GearDown = down;
        _s.Controls.GearPct = down ? 100 : 0;
    }

    private void SetFlaps(int index)
    {
        var c = _s.Controls;
        c.FlapsIndex = Math.Clamp(index, 0, c.FlapsPositions - 1);
        c.FlapsPct = Math.Round(100.0 * c.FlapsIndex / (c.FlapsPositions - 1));
    }

    private void UpdateEngines(double n1Target, double dt)
    {
        double totalFlow = 0;
        foreach (var e in _s.Engines)
        {
            e.N1Pct = Math.Round(Approach(e.N1Pct, n1Target, 6 * dt + (dt == 0 ? 100 : 0)), 1);
            e.N2Pct = Math.Round(58 + e.N1Pct * 0.42, 1);
            e.EgtC = Math.Round(380 + e.N1Pct * 5.2);
            e.FuelFlowKgH = Math.Round(200 + Math.Max(0, e.N1Pct - 20) * 15);
            e.Rpm = 0;
            totalFlow += e.FuelFlowKgH;
        }

        var burn = totalFlow * dt / 3600;
        var t = _s.Fuel.Tanks;
        if (t.CenterKg > burn) t.CenterKg -= burn;
        else { t.LeftKg = Math.Max(0, t.LeftKg - burn / 2); t.RightKg = Math.Max(0, t.RightKg - burn / 2); }
        _s.Fuel.TotalKg = Math.Round(t.LeftKg + t.CenterKg + t.RightKg, 1);
    }

    private static double Mhz(long hz) => Math.Round(hz / 1_000_000.0, 3);

    private static void Swap(RadioPair p) => (p.ActiveMhz, p.StandbyMhz) = (p.StandbyMhz, p.ActiveMhz);

    private static double Approach(double value, double target, double maxStep) =>
        value < target ? Math.Min(target, value + maxStep) : Math.Max(target, value - maxStep);

    private static double Norm(double deg) => ((deg % 360) + 360) % 360;

    private static double AngleDiff(double target, double current) => ((target - current + 540) % 360) - 180;

    public static double DistanceNm(double lat1, double lon1, double lat2, double lon2)
    {
        const double r = 3440.065;
        var dLat = (lat2 - lat1) * Math.PI / 180;
        var dLon = (lon2 - lon1) * Math.PI / 180;
        var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2)
                + Math.Cos(lat1 * Math.PI / 180) * Math.Cos(lat2 * Math.PI / 180) * Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        return 2 * r * Math.Asin(Math.Min(1, Math.Sqrt(a)));
    }

    public static double BearingDeg(double lat1, double lon1, double lat2, double lon2)
    {
        var f1 = lat1 * Math.PI / 180;
        var f2 = lat2 * Math.PI / 180;
        var dl = (lon2 - lon1) * Math.PI / 180;
        var y = Math.Sin(dl) * Math.Cos(f2);
        var x = Math.Cos(f1) * Math.Sin(f2) - Math.Sin(f1) * Math.Cos(f2) * Math.Cos(dl);
        return Norm(Math.Atan2(y, x) * 180 / Math.PI);
    }
}
