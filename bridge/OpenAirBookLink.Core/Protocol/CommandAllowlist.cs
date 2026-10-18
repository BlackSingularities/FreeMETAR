using System.Text.Json;

namespace OpenAirBookLink.Core.Protocol;

public enum CommandTarget
{
    // Zdarzenie SimConnect (SimConnect_TransmitClientEvent)
    Event,
    // Zapis jednej zmiennej symulatora (tylko XPNDR_MODE)
    TransponderState,
    // Zapis CAMERA STATE (widok z kabiny / zewnętrzny / dron)
    CameraState,
}

public enum CommandGroup { Autopilot, Radio, Transponder, Gear, Flaps, Spoilers, Brakes, Lights, Trim, Engines, View }

public sealed record CommandSpec(string Name, CommandGroup Group, CommandTarget Target = CommandTarget.Event,
    long? Min = null, long? Max = null, bool SquawkCode = false)
{
    public bool NeedsValue => Min.HasValue;
}

// Komenda po walidacji: nazwa z listy i gotowa wartość DWORD dla SimConnect.
public sealed record ValidatedCommand(CommandSpec Spec, long Value, uint Data);

public sealed record CommandValidation(ValidatedCommand? Command, string? ErrorCode, string? ErrorMessage)
{
    public bool Ok => Command != null;
}

// Jedyne komendy, jakie telefon może wysłać do symulatora. Wszystko spoza listy jest odrzucane.
public static class CommandAllowlist
{
    public const long ComMinHz = 118_000_000, ComMaxHz = 136_990_000;
    public const long NavMinHz = 108_000_000, NavMaxHz = 117_950_000;

    private static readonly CommandSpec[] Specs =
    {
        // Autopilot
        new("AP_MASTER", CommandGroup.Autopilot),
        new("AUTOPILOT_ON", CommandGroup.Autopilot),
        new("AUTOPILOT_OFF", CommandGroup.Autopilot),
        new("TOGGLE_FLIGHT_DIRECTOR", CommandGroup.Autopilot),
        new("AUTO_THROTTLE_ARM", CommandGroup.Autopilot),
        new("YAW_DAMPER_TOGGLE", CommandGroup.Autopilot),
        new("AP_HDG_HOLD", CommandGroup.Autopilot),
        new("AP_NAV1_HOLD", CommandGroup.Autopilot),
        new("AP_APR_HOLD", CommandGroup.Autopilot),
        new("AP_LOC_HOLD", CommandGroup.Autopilot),
        new("AP_BC_HOLD", CommandGroup.Autopilot),
        new("AP_ALT_HOLD", CommandGroup.Autopilot),
        new("AP_VS_HOLD", CommandGroup.Autopilot),
        new("FLIGHT_LEVEL_CHANGE", CommandGroup.Autopilot),
        new("AP_PANEL_SPEED_HOLD", CommandGroup.Autopilot),
        new("HEADING_BUG_SET", CommandGroup.Autopilot, Min: 0, Max: 359),
        new("HEADING_BUG_INC", CommandGroup.Autopilot),
        new("HEADING_BUG_DEC", CommandGroup.Autopilot),
        new("AP_ALT_VAR_SET_ENGLISH", CommandGroup.Autopilot, Min: 0, Max: 50_000),
        new("AP_ALT_VAR_INC", CommandGroup.Autopilot),
        new("AP_ALT_VAR_DEC", CommandGroup.Autopilot),
        new("AP_VS_VAR_SET_ENGLISH", CommandGroup.Autopilot, Min: -8_000, Max: 8_000),
        new("AP_VS_VAR_INC", CommandGroup.Autopilot),
        new("AP_VS_VAR_DEC", CommandGroup.Autopilot),
        new("AP_SPD_VAR_SET", CommandGroup.Autopilot, Min: 0, Max: 500),
        new("AP_SPD_VAR_INC", CommandGroup.Autopilot),
        new("AP_SPD_VAR_DEC", CommandGroup.Autopilot),
        new("AP_AIRSPEED_HOLD", CommandGroup.Autopilot),
        new("AP_N1_HOLD", CommandGroup.Autopilot),
        new("AP_PANEL_MACH_HOLD", CommandGroup.Autopilot),
        // Mach × 100 (np. 78 = M0.78)
        new("AP_MACH_VAR_SET", CommandGroup.Autopilot, Min: 10, Max: 99),
        new("AP_BANK_HOLD", CommandGroup.Autopilot),
        new("AP_WING_LEVELER", CommandGroup.Autopilot),

        // Radia
        new("COM_RADIO_SET_HZ", CommandGroup.Radio, Min: ComMinHz, Max: ComMaxHz),
        new("COM_STBY_RADIO_SET_HZ", CommandGroup.Radio, Min: ComMinHz, Max: ComMaxHz),
        new("COM2_RADIO_SET_HZ", CommandGroup.Radio, Min: ComMinHz, Max: ComMaxHz),
        new("COM2_STBY_RADIO_SET_HZ", CommandGroup.Radio, Min: ComMinHz, Max: ComMaxHz),
        new("NAV1_RADIO_SET_HZ", CommandGroup.Radio, Min: NavMinHz, Max: NavMaxHz),
        new("NAV1_STBY_SET_HZ", CommandGroup.Radio, Min: NavMinHz, Max: NavMaxHz),
        new("NAV2_RADIO_SET_HZ", CommandGroup.Radio, Min: NavMinHz, Max: NavMaxHz),
        new("NAV2_STBY_SET_HZ", CommandGroup.Radio, Min: NavMinHz, Max: NavMaxHz),
        new("COM_STBY_RADIO_SWAP", CommandGroup.Radio),
        new("COM2_RADIO_SWAP", CommandGroup.Radio),
        new("NAV1_RADIO_SWAP", CommandGroup.Radio),
        new("NAV2_RADIO_SWAP", CommandGroup.Radio),

        // Transponder
        new("XPNDR_SET", CommandGroup.Transponder, Min: 0, Max: 7777, SquawkCode: true),
        new("XPNDR_IDENT_ON", CommandGroup.Transponder),
        new("XPNDR_MODE", CommandGroup.Transponder, CommandTarget.TransponderState, Min: 0, Max: 5),

        // Podwozie, klapy, spoilery, hamulec
        new("GEAR_UP", CommandGroup.Gear),
        new("GEAR_DOWN", CommandGroup.Gear),
        new("GEAR_TOGGLE", CommandGroup.Gear),
        new("FLAPS_UP", CommandGroup.Flaps),
        new("FLAPS_DOWN", CommandGroup.Flaps),
        new("FLAPS_INCR", CommandGroup.Flaps),
        new("FLAPS_DECR", CommandGroup.Flaps),
        new("FLAPS_SET", CommandGroup.Flaps, Min: 0, Max: 16383),
        new("SPOILERS_ON", CommandGroup.Spoilers),
        new("SPOILERS_OFF", CommandGroup.Spoilers),
        new("SPOILERS_TOGGLE", CommandGroup.Spoilers),
        new("SPOILERS_ARM_TOGGLE", CommandGroup.Spoilers),
        new("SPOILERS_SET", CommandGroup.Spoilers, Min: 0, Max: 16383),
        new("PARKING_BRAKES", CommandGroup.Brakes),
        new("PARKING_BRAKE_SET", CommandGroup.Brakes, Min: 0, Max: 1),

        // Światła
        new("TOGGLE_BEACON_LIGHTS", CommandGroup.Lights),
        new("STROBES_TOGGLE", CommandGroup.Lights),
        new("TOGGLE_NAV_LIGHTS", CommandGroup.Lights),
        new("LANDING_LIGHTS_TOGGLE", CommandGroup.Lights),
        new("TOGGLE_TAXI_LIGHTS", CommandGroup.Lights),
        new("TOGGLE_LOGO_LIGHTS", CommandGroup.Lights),
        new("TOGGLE_WING_LIGHTS", CommandGroup.Lights),
        new("PANEL_LIGHTS_TOGGLE", CommandGroup.Lights),
        new("TOGGLE_CABIN_LIGHTS", CommandGroup.Lights),

        // Trymer
        new("ELEV_TRIM_UP", CommandGroup.Trim),
        new("ELEV_TRIM_DN", CommandGroup.Trim),
        new("ELEVATOR_TRIM_SET", CommandGroup.Trim, Min: -16383, Max: 16383),
        new("AILERON_TRIM_LEFT", CommandGroup.Trim),
        new("AILERON_TRIM_RIGHT", CommandGroup.Trim),
        new("RUDDER_TRIM_LEFT", CommandGroup.Trim),
        new("RUDDER_TRIM_RIGHT", CommandGroup.Trim),

        // Silniki: dźwignie wszystkich silników naraz (0 = jałowy / uboga / małe obroty, 16383 = pełne)
        new("THROTTLE_SET", CommandGroup.Engines, Min: 0, Max: 16383),
        new("THROTTLE_CUT", CommandGroup.Engines),
        new("THROTTLE_FULL", CommandGroup.Engines),
        new("THROTTLE_REVERSE_THRUST_TOGGLE", CommandGroup.Engines),
        new("AUTO_THROTTLE_TO_GA", CommandGroup.Engines),
        new("MIXTURE_SET", CommandGroup.Engines, Min: 0, Max: 16383),
        new("PROPELLER_SET", CommandGroup.Engines, Min: 0, Max: 16383),

        // Kamera MSFS: 2 kabina, 3 zewnętrzna (pościg), 4 dron
        new("CAMERA_STATE", CommandGroup.View, CommandTarget.CameraState, Min: 2, Max: 4),
    };

    // Zdarzenia osi — nie są komendami `command`, przychodzą przez wiadomość `axis`.
    public static readonly string[] AxisEvents = { "AXIS_AILERONS_SET", "AXIS_ELEVATOR_SET", "AXIS_RUDDER_SET" };

    private static readonly Dictionary<string, CommandSpec> ByName =
        Specs.ToDictionary(s => s.Name, StringComparer.Ordinal);

    public static IReadOnlyCollection<CommandSpec> All => Specs;

    public static bool IsAllowed(string? name) => name != null && ByName.ContainsKey(name);

    public static CommandValidation Validate(string? name, JsonElement? value)
    {
        if (string.IsNullOrEmpty(name) || !ByName.TryGetValue(name, out var spec))
            return Fail(ErrorCodes.NotAllowed, $"Komenda poza listą dozwolonych: {name}");

        if (!spec.NeedsValue)
            return new CommandValidation(new ValidatedCommand(spec, 0, 0), null, null);

        if (value is not { ValueKind: JsonValueKind.Number } v || !v.TryGetDouble(out var d)
            || double.IsNaN(d) || double.IsInfinity(d) || d != Math.Floor(d))
            return Fail(ErrorCodes.InvalidValue, $"{name} wymaga wartości całkowitej {spec.Min}…{spec.Max}");

        if (d < spec.Min!.Value || d > spec.Max!.Value)
            return Fail(ErrorCodes.InvalidValue, $"{name}: wartość {d} poza zakresem {spec.Min}…{spec.Max}");

        var n = (long)d;
        uint data;
        if (spec.SquawkCode)
        {
            if (!TryEncodeSquawk(n, out data))
                return Fail(ErrorCodes.InvalidValue, $"{name}: kod transpondera musi mieć cyfry 0–7");
        }
        else
        {
            data = unchecked((uint)(int)n);
        }

        return new CommandValidation(new ValidatedCommand(spec, n, data), null, null);
    }

    // 7000 → 0x7000 (BCD16, jak oczekuje XPNDR_SET).
    public static bool TryEncodeSquawk(long code, out uint bcd)
    {
        bcd = 0;
        if (code < 0 || code > 7777) return false;
        var digits = code.ToString("D4");
        foreach (var c in digits)
        {
            if (c > '7') return false;
            bcd = (bcd << 4) | (uint)(c - '0');
        }
        return true;
    }

    // 0x7000 → "7000"
    public static string DecodeSquawk(uint bcd) =>
        $"{(bcd >> 12) & 0xF}{(bcd >> 8) & 0xF}{(bcd >> 4) & 0xF}{bcd & 0xF}";

    private static CommandValidation Fail(string code, string message) => new(null, code, message);
}
