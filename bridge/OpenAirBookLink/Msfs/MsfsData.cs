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

    [SimConnect("AMBIENT WIND DIRECTION", "degrees")] public double WindDir;
    [SimConnect("AMBIENT WIND VELOCITY", "knots")] public double WindSpd;
    [SimConnect("AMBIENT TEMPERATURE", "celsius")] public double Oat;
    [SimConnect("KOHLSMAN SETTING MB:1", "millibars")] public double Qnh;
}
