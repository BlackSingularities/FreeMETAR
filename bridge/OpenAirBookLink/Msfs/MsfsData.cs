using SimConnect.NET;

namespace OpenAirBookLink.Msfs;

// Jedna definicja danych SimConnect — kolejność pól = kolejność w definicji.
// Nazwy i jednostki SimVarów z dokumentacji MSFS SDK.
public struct MsfsData
{
    [SimConnect("TITLE", null, SimConnectDataType.String256)] public string Title;
    [SimConnect("SIM ON GROUND", "bool")] public double OnGround;
    [SimConnect("ZULU TIME", "seconds")] public double ZuluTime;
    [SimConnect("ZULU DAY OF YEAR", "number")] public double ZuluDayOfYear;
    [SimConnect("ZULU YEAR", "number")] public double ZuluYear;

    [SimConnect("PLANE LATITUDE", "degrees")] public double Lat;
    [SimConnect("PLANE LONGITUDE", "degrees")] public double Lon;
    [SimConnect("PLANE ALTITUDE", "feet")] public double AltMsl;
    [SimConnect("PLANE ALT ABOVE GROUND", "feet")] public double AltAgl;
    [SimConnect("INDICATED ALTITUDE", "feet")] public double AltInd;

    [SimConnect("AIRSPEED INDICATED", "knots")] public double Ias;
    [SimConnect("AIRSPEED TRUE", "knots")] public double Tas;
    [SimConnect("GROUND VELOCITY", "knots")] public double Gs;
    [SimConnect("AIRSPEED MACH", "mach")] public double Mach;
    [SimConnect("VERTICAL SPEED", "feet per minute")] public double Vs;

    [SimConnect("PLANE PITCH DEGREES", "degrees")] public double Pitch;
    [SimConnect("PLANE BANK DEGREES", "degrees")] public double Bank;
    [SimConnect("PLANE HEADING DEGREES TRUE", "degrees")] public double HdgTrue;
    [SimConnect("PLANE HEADING DEGREES MAGNETIC", "degrees")] public double HdgMag;
    [SimConnect("GPS GROUND TRUE TRACK", "degrees")] public double Track;

    [SimConnect("AUTOPILOT MASTER", "bool")] public double ApMaster;
    [SimConnect("AUTOPILOT FLIGHT DIRECTOR ACTIVE", "bool")] public double ApFd;
    [SimConnect("AUTOPILOT THROTTLE ARM", "bool")] public double ApAthrArm;
    [SimConnect("AUTOTHROTTLE ACTIVE", "bool")] public double ApAthrActive;
    [SimConnect("AUTOPILOT HEADING LOCK DIR", "degrees")] public double ApHdg;
    [SimConnect("AUTOPILOT ALTITUDE LOCK VAR", "feet")] public double ApAlt;
    [SimConnect("AUTOPILOT VERTICAL HOLD VAR", "feet per minute")] public double ApVs;
    [SimConnect("AUTOPILOT AIRSPEED HOLD VAR", "knots")] public double ApSpd;
    [SimConnect("AUTOPILOT HEADING LOCK", "bool")] public double ApHdgMode;
    [SimConnect("AUTOPILOT NAV1 LOCK", "bool")] public double ApNavMode;
    [SimConnect("AUTOPILOT APPROACH HOLD", "bool")] public double ApAprMode;
    [SimConnect("AUTOPILOT APPROACH IS LOCALIZER", "bool")] public double ApAprIsLoc;
    [SimConnect("AUTOPILOT ALTITUDE LOCK", "bool")] public double ApAltMode;
    [SimConnect("AUTOPILOT VERTICAL HOLD", "bool")] public double ApVsMode;
    [SimConnect("AUTOPILOT FLIGHT LEVEL CHANGE", "bool")] public double ApFlcMode;

    [SimConnect("COM ACTIVE FREQUENCY:1", "MHz")] public double Com1Active;
    [SimConnect("COM STANDBY FREQUENCY:1", "MHz")] public double Com1Standby;
    [SimConnect("COM ACTIVE FREQUENCY:2", "MHz")] public double Com2Active;
    [SimConnect("COM STANDBY FREQUENCY:2", "MHz")] public double Com2Standby;
    [SimConnect("NAV ACTIVE FREQUENCY:1", "MHz")] public double Nav1Active;
    [SimConnect("NAV STANDBY FREQUENCY:1", "MHz")] public double Nav1Standby;
    [SimConnect("NAV ACTIVE FREQUENCY:2", "MHz")] public double Nav2Active;
    [SimConnect("NAV STANDBY FREQUENCY:2", "MHz")] public double Nav2Standby;
    [SimConnect("TRANSPONDER CODE:1", "Bco16")] public double XpdrCode;
    [SimConnect("TRANSPONDER STATE:1", "Enum")] public double XpdrState;
    [SimConnect("TRANSPONDER IDENT:1", "bool")] public double XpdrIdent;

    [SimConnect("GEAR HANDLE POSITION", "bool")] public double GearHandle;
    [SimConnect("GEAR TOTAL PCT EXTENDED", "percent")] public double GearPct;
    [SimConnect("FLAPS HANDLE INDEX", "number")] public double FlapsIndex;
    [SimConnect("FLAPS NUM HANDLE POSITIONS", "number")] public double FlapsPositions;
    [SimConnect("FLAPS HANDLE PERCENT", "percent")] public double FlapsPct;
    [SimConnect("SPOILERS HANDLE POSITION", "percent")] public double SpoilersPct;
    [SimConnect("SPOILERS ARMED", "bool")] public double SpoilersArmed;
    [SimConnect("BRAKE PARKING POSITION", "bool")] public double ParkingBrake;
    [SimConnect("ELEVATOR TRIM PCT", "percent")] public double ElevTrimPct;
    [SimConnect("GENERAL ENG THROTTLE LEVER POSITION:1", "percent")] public double Throttle1;
    [SimConnect("GENERAL ENG MIXTURE LEVER POSITION:1", "percent")] public double Mixture1;
    [SimConnect("GENERAL ENG PROPELLER LEVER POSITION:1", "percent")] public double Prop1;
    [SimConnect("CAMERA STATE", "Enum")] public double CameraState;
    [SimConnect("CAMERA GAMEPLAY PITCH YAW:0", "radians")] public double CamPitchRad;
    [SimConnect("CAMERA GAMEPLAY PITCH YAW:1", "radians")] public double CamYawRad;

    [SimConnect("LIGHT BEACON", "bool")] public double LightBeacon;
    [SimConnect("LIGHT STROBE", "bool")] public double LightStrobe;
    [SimConnect("LIGHT NAV", "bool")] public double LightNav;
    [SimConnect("LIGHT LANDING", "bool")] public double LightLanding;
    [SimConnect("LIGHT TAXI", "bool")] public double LightTaxi;
    [SimConnect("LIGHT LOGO", "bool")] public double LightLogo;
    [SimConnect("LIGHT WING", "bool")] public double LightWing;
    [SimConnect("LIGHT PANEL", "bool")] public double LightPanel;
    [SimConnect("LIGHT CABIN", "bool")] public double LightCabin;

    [SimConnect("FUEL TOTAL QUANTITY WEIGHT", "pounds")] public double FuelTotalLbs;
    [SimConnect("FUEL WEIGHT PER GALLON", "pounds")] public double FuelLbsPerGal;
    [SimConnect("FUEL TANK LEFT MAIN QUANTITY", "gallons")] public double FuelLeftGal;
    [SimConnect("FUEL TANK CENTER QUANTITY", "gallons")] public double FuelCenterGal;
    [SimConnect("FUEL TANK RIGHT MAIN QUANTITY", "gallons")] public double FuelRightGal;

    [SimConnect("NUMBER OF ENGINES", "number")] public double EngineCount;
    [SimConnect("TURB ENG N1:1", "percent")] public double N1_1;
    [SimConnect("TURB ENG N2:1", "percent")] public double N2_1;
    [SimConnect("GENERAL ENG RPM:1", "rpm")] public double Rpm1;
    [SimConnect("GENERAL ENG EXHAUST GAS TEMPERATURE:1", "celsius")] public double Egt1;
    [SimConnect("ENG FUEL FLOW PPH:1", "pounds per hour")] public double Ff1;
    [SimConnect("TURB ENG N1:2", "percent")] public double N1_2;
    [SimConnect("TURB ENG N2:2", "percent")] public double N2_2;
    [SimConnect("GENERAL ENG RPM:2", "rpm")] public double Rpm2;
    [SimConnect("GENERAL ENG EXHAUST GAS TEMPERATURE:2", "celsius")] public double Egt2;
    [SimConnect("ENG FUEL FLOW PPH:2", "pounds per hour")] public double Ff2;
    [SimConnect("TURB ENG N1:3", "percent")] public double N1_3;
    [SimConnect("TURB ENG N2:3", "percent")] public double N2_3;
    [SimConnect("GENERAL ENG RPM:3", "rpm")] public double Rpm3;
    [SimConnect("GENERAL ENG EXHAUST GAS TEMPERATURE:3", "celsius")] public double Egt3;
    [SimConnect("ENG FUEL FLOW PPH:3", "pounds per hour")] public double Ff3;
    [SimConnect("TURB ENG N1:4", "percent")] public double N1_4;
    [SimConnect("TURB ENG N2:4", "percent")] public double N2_4;
    [SimConnect("GENERAL ENG RPM:4", "rpm")] public double Rpm4;
    [SimConnect("GENERAL ENG EXHAUST GAS TEMPERATURE:4", "celsius")] public double Egt4;
    [SimConnect("ENG FUEL FLOW PPH:4", "pounds per hour")] public double Ff4;
    // Dźwignie mocy pozostałych silników (silnik 1 to Throttle1 wyżej)
    [SimConnect("GENERAL ENG THROTTLE LEVER POSITION:2", "percent")] public double Throttle2;
    [SimConnect("GENERAL ENG THROTTLE LEVER POSITION:3", "percent")] public double Throttle3;
    [SimConnect("GENERAL ENG THROTTLE LEVER POSITION:4", "percent")] public double Throttle4;

    [SimConnect("AMBIENT WIND DIRECTION", "degrees")] public double WindDir;
    [SimConnect("AMBIENT WIND VELOCITY", "knots")] public double WindSpd;
    [SimConnect("AMBIENT TEMPERATURE", "celsius")] public double Oat;
    [SimConnect("KOHLSMAN SETTING MB:1", "millibars")] public double Qnh;

    // Profil samolotu
    [SimConnect("ENGINE TYPE", "Enum")] public double EngineType;
    [SimConnect("MAX GROSS WEIGHT", "pounds")] public double MaxGrossWeightLbs;
    [SimConnect("ATC MODEL", null, SimConnectDataType.String64)] public string AtcModel;
    [SimConnect("ATC TYPE", null, SimConnectDataType.String64)] public string AtcType;
    [SimConnect("CATEGORY", null, SimConnectDataType.String64)] public string SimCategory;

    // Przyziemienie (wartości ostatniego kontaktu z ziemią, trzymane przez MSFS)
    [SimConnect("G FORCE", "GForce")] public double GForce;
    [SimConnect("PLANE TOUCHDOWN NORMAL VELOCITY", "feet per minute")] public double TdVs;
    [SimConnect("PLANE TOUCHDOWN PITCH DEGREES", "degrees")] public double TdPitch;
    [SimConnect("PLANE TOUCHDOWN BANK DEGREES", "degrees")] public double TdBank;
    [SimConnect("PLANE TOUCHDOWN LATITUDE", "degrees")] public double TdLat;
    [SimConnect("PLANE TOUCHDOWN LONGITUDE", "degrees")] public double TdLon;

    // Awionika (PFD/MFD)
    [SimConnect("KOHLSMAN SETTING STD:1", "bool")] public double BaroStd;
    [SimConnect("PRESSURE ALTITUDE", "feet")] public double PressureAlt;
    [SimConnect("RADIO HEIGHT", "feet")] public double RadioAlt;
    [SimConnect("DECISION HEIGHT", "feet")] public double DecisionHeight;
    [SimConnect("TURN COORDINATOR BALL", "position 128")] public double SlipBall;
    [SimConnect("TURN INDICATOR RATE", "degrees per second")] public double TurnRate;
    [SimConnect("INCIDENCE ALPHA", "degrees")] public double Aoa;
    [SimConnect("ATTITUDE INDICATOR PITCH DEGREES", "degrees")] public double AttIndPitch;
    [SimConnect("ATTITUDE INDICATOR BANK DEGREES", "degrees")] public double AttIndBank;
    [SimConnect("HEADING INDICATOR", "degrees")] public double HdgInd;
    [SimConnect("SUCTION PRESSURE", "inHg")] public double Suction;
    [SimConnect("MAGVAR", "degrees")] public double MagVar;
    [SimConnect("TOTAL AIR TEMPERATURE", "celsius")] public double Tat;
    [SimConnect("DESIGN SPEED VS0", "knots")] public double Vs0;
    [SimConnect("DESIGN SPEED VS1", "knots")] public double Vs1;
    [SimConnect("DESIGN SPEED VC", "knots")] public double Vc;
    [SimConnect("AIRSPEED BARBER POLE", "knots")] public double BarberPole;
    [SimConnect("STALL WARNING", "bool")] public double StallWarning;
    [SimConnect("OVERSPEED WARNING", "bool")] public double OverspeedWarning;
    [SimConnect("MARKER BEACON STATE", "Enum")] public double Marker;

    [SimConnect("NAV HAS NAV:1", "bool")] public double Nav1Has;
    [SimConnect("NAV HAS LOCALIZER:1", "bool")] public double Nav1HasLoc;
    [SimConnect("NAV HAS GLIDE SLOPE:1", "bool")] public double Nav1HasGs;
    [SimConnect("NAV HAS DME:1", "bool")] public double Nav1HasDme;
    [SimConnect("NAV IDENT:1", null, SimConnectDataType.String64)] public string Nav1Ident;
    [SimConnect("NAV OBS:1", "degrees")] public double Nav1Obs;
    [SimConnect("NAV LOCALIZER:1", "degrees")] public double Nav1LocCourse;
    [SimConnect("NAV RADIAL:1", "degrees")] public double Nav1Radial;
    [SimConnect("NAV CDI:1", "number")] public double Nav1Cdi;
    [SimConnect("NAV GSI:1", "number")] public double Nav1Gsi;
    [SimConnect("NAV DME:1", "nautical miles")] public double Nav1Dme;
    [SimConnect("NAV TOFROM:1", "Enum")] public double Nav1ToFrom;
    [SimConnect("NAV HAS NAV:2", "bool")] public double Nav2Has;
    [SimConnect("NAV HAS LOCALIZER:2", "bool")] public double Nav2HasLoc;
    [SimConnect("NAV HAS DME:2", "bool")] public double Nav2HasDme;
    [SimConnect("NAV IDENT:2", null, SimConnectDataType.String64)] public string Nav2Ident;
    [SimConnect("NAV OBS:2", "degrees")] public double Nav2Obs;
    [SimConnect("NAV RADIAL:2", "degrees")] public double Nav2Radial;
    [SimConnect("NAV CDI:2", "number")] public double Nav2Cdi;
    [SimConnect("NAV DME:2", "nautical miles")] public double Nav2Dme;
    [SimConnect("NAV TOFROM:2", "Enum")] public double Nav2ToFrom;
    [SimConnect("ADF SIGNAL:1", "number")] public double AdfSignal;
    [SimConnect("ADF RADIAL:1", "degrees")] public double AdfRadial;

    [SimConnect("GPS IS ACTIVE FLIGHT PLAN", "bool")] public double GpsActivePlan;
    [SimConnect("GPS IS APPROACH ACTIVE", "bool")] public double GpsApproach;
    [SimConnect("GPS DRIVES NAV1", "bool")] public double GpsDrivesNav1;
    [SimConnect("GPS WP NEXT ID", null, SimConnectDataType.String64)] public string GpsNextId;
    [SimConnect("GPS WP PREV ID", null, SimConnectDataType.String64)] public string GpsPrevId;
    [SimConnect("GPS WP DISTANCE", "nautical miles")] public double GpsNextDist;
    [SimConnect("GPS WP BEARING", "degrees")] public double GpsNextBearingTrue;
    [SimConnect("GPS WP DESIRED TRACK", "degrees")] public double GpsDtkTrue;
    [SimConnect("GPS WP CROSS TRK", "nautical miles")] public double GpsXtk;
    [SimConnect("GPS WP ETE", "seconds")] public double GpsNextEte;
    [SimConnect("GPS ETE", "seconds")] public double GpsDestEte;
    [SimConnect("GPS WP NEXT LAT", "degrees")] public double GpsNextLat;
    [SimConnect("GPS WP NEXT LON", "degrees")] public double GpsNextLon;
    [SimConnect("GPS WP PREV LAT", "degrees")] public double GpsPrevLat;
    [SimConnect("GPS WP PREV LON", "degrees")] public double GpsPrevLon;
    [SimConnect("GPS FLIGHT PLAN WP INDEX", "number")] public double GpsWpIndex;
    [SimConnect("GPS FLIGHT PLAN WP COUNT", "number")] public double GpsWpCount;

    [SimConnect("AUTOPILOT FLIGHT DIRECTOR PITCH", "degrees")] public double FdPitch;
    [SimConnect("AUTOPILOT FLIGHT DIRECTOR BANK", "degrees")] public double FdBank;
    [SimConnect("AUTOPILOT ALTITUDE ARM", "bool")] public double ApAltArm;
    [SimConnect("AUTOPILOT GLIDESLOPE ARM", "bool")] public double ApGsArm;
    [SimConnect("AUTOPILOT GLIDESLOPE HOLD", "bool")] public double ApGsHold;
    [SimConnect("AUTOPILOT BACKCOURSE HOLD", "bool")] public double ApBc;
    [SimConnect("AUTOPILOT WING LEVELER", "bool")] public double ApWingLeveler;
    [SimConnect("AUTOPILOT PITCH HOLD", "bool")] public double ApPitchHold;
    [SimConnect("AUTOPILOT AIRSPEED HOLD", "bool")] public double ApSpdHold;
    [SimConnect("AUTOPILOT MACH HOLD", "bool")] public double ApMachHold;
    [SimConnect("AUTOPILOT TAKEOFF POWER ACTIVE", "bool")] public double ApToga;
    [SimConnect("AUTOPILOT YAW DAMPER", "bool")] public double ApYawDamper;
    [SimConnect("AUTOPILOT MACH HOLD VAR", "number")] public double ApMachVar;
    [SimConnect("BRAKE LEFT POSITION", "percent")] public double BrakeLeft;
    [SimConnect("BRAKE RIGHT POSITION", "percent")] public double BrakeRight;
}
