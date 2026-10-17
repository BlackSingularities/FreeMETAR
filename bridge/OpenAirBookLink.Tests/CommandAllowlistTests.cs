using System.Text.Json;
using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Tests;

public class CommandAllowlistTests
{
    private static JsonElement Num(double v) => JsonDocument.Parse(v.ToString(System.Globalization.CultureInfo.InvariantCulture)).RootElement;

    [Theory]
    [InlineData("AP_MASTER")]
    [InlineData("GEAR_TOGGLE")]
    [InlineData("TOGGLE_BEACON_LIGHTS")]
    [InlineData("PARKING_BRAKES")]
    [InlineData("SPOILERS_ARM_TOGGLE")]
    [InlineData("ELEV_TRIM_UP")]
    [InlineData("COM_STBY_RADIO_SWAP")]
    [InlineData("AP_N1_HOLD")]
    [InlineData("AP_PANEL_MACH_HOLD")]
    [InlineData("AP_BANK_HOLD")]
    [InlineData("AP_WING_LEVELER")]
    [InlineData("YAW_DAMPER_TOGGLE")]
    [InlineData("AP_AIRSPEED_HOLD")]
    public void Accepts_allowed_commands_without_value(string name)
    {
        var r = CommandAllowlist.Validate(name, null);
        Assert.True(r.Ok);
        Assert.Equal(name, r.Command!.Spec.Name);
    }

    [Theory]
    [InlineData("SIM_RATE_INCR")]          // istnieje w MSFS, ale nie jest dozwolone
    [InlineData("PAUSE_TOGGLE")]
    [InlineData("SITUATION_RESET")]
    [InlineData("AXIS_ELEVATOR_SET")]      // osie tylko przez wiadomość `axis`
    [InlineData("ap_master")]              // wielkość liter ma znaczenie
    [InlineData("")]
    [InlineData(null)]
    public void Rejects_commands_outside_allowlist(string? name)
    {
        var r = CommandAllowlist.Validate(name, null);
        Assert.False(r.Ok);
        Assert.Equal(ErrorCodes.NotAllowed, r.ErrorCode);
    }

    [Fact]
    public void Value_required_and_range_checked()
    {
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("HEADING_BUG_SET", null).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("HEADING_BUG_SET", Num(360)).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("HEADING_BUG_SET", Num(12.5)).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue,
            CommandAllowlist.Validate("HEADING_BUG_SET", JsonDocument.Parse("\"90\"").RootElement).ErrorCode);
        Assert.True(CommandAllowlist.Validate("HEADING_BUG_SET", Num(270)).Ok);
        Assert.Equal(270u, CommandAllowlist.Validate("HEADING_BUG_SET", Num(270)).Command!.Data);
    }

    [Fact]
    public void Mach_set_takes_mach_times_100_between_10_and_99()
    {
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("AP_MACH_VAR_SET", null).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("AP_MACH_VAR_SET", Num(9)).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("AP_MACH_VAR_SET", Num(100)).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("AP_MACH_VAR_SET", Num(0.78)).ErrorCode);
        Assert.True(CommandAllowlist.Validate("AP_MACH_VAR_SET", Num(10)).Ok);
        Assert.Equal(78u, CommandAllowlist.Validate("AP_MACH_VAR_SET", Num(78)).Command!.Data);
        Assert.True(CommandAllowlist.Validate("AP_MACH_VAR_SET", Num(99)).Ok);
    }

    [Fact]
    public void New_autopilot_commands_do_not_take_value_and_are_listed_once()
    {
        foreach (var name in new[] { "AP_N1_HOLD", "AP_PANEL_MACH_HOLD", "AP_BANK_HOLD", "AP_WING_LEVELER", "YAW_DAMPER_TOGGLE", "AP_AIRSPEED_HOLD" })
            Assert.False(CommandAllowlist.All.Single(s => s.Name == name).NeedsValue);
        Assert.Equal(CommandAllowlist.All.Count, CommandAllowlist.All.Select(s => s.Name).Distinct().Count());
    }

    [Fact]
    public void Radio_frequency_ranges()
    {
        Assert.True(CommandAllowlist.Validate("COM_STBY_RADIO_SET_HZ", Num(121_500_000)).Ok);
        Assert.False(CommandAllowlist.Validate("COM_STBY_RADIO_SET_HZ", Num(110_300_000)).Ok);
        Assert.True(CommandAllowlist.Validate("NAV1_STBY_SET_HZ", Num(110_300_000)).Ok);
        Assert.False(CommandAllowlist.Validate("NAV1_STBY_SET_HZ", Num(121_500_000)).Ok);
    }

    [Fact]
    public void Negative_vertical_speed_is_encoded_as_twos_complement()
    {
        var r = CommandAllowlist.Validate("AP_VS_VAR_SET_ENGLISH", Num(-1500));
        Assert.True(r.Ok);
        Assert.Equal(unchecked((uint)-1500), r.Command!.Data);
    }

    [Theory]
    [InlineData(7000, 0x7000u)]
    [InlineData(1200, 0x1200u)]
    [InlineData(17, 0x0017u)]
    public void Squawk_encoded_as_bcd(long code, uint expected)
    {
        var r = CommandAllowlist.Validate("XPNDR_SET", Num(code));
        Assert.True(r.Ok);
        Assert.Equal(expected, r.Command!.Data);
        Assert.Equal(code.ToString("D4"), CommandAllowlist.DecodeSquawk(expected));
    }

    [Fact]
    public void Squawk_with_digit_8_or_9_is_rejected()
    {
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("XPNDR_SET", Num(1280)).ErrorCode);
        Assert.Equal(ErrorCodes.InvalidValue, CommandAllowlist.Validate("XPNDR_SET", Num(7779)).ErrorCode);
    }

    [Fact]
    public void Transponder_mode_is_a_simvar_write_not_an_event()
    {
        var r = CommandAllowlist.Validate("XPNDR_MODE", Num(4));
        Assert.True(r.Ok);
        Assert.Equal(CommandTarget.TransponderState, r.Command!.Spec.Target);
        Assert.False(CommandAllowlist.Validate("XPNDR_MODE", Num(6)).Ok);
    }

    [Fact]
    public void Allowlist_covers_every_required_group()
    {
        var groups = CommandAllowlist.All.Select(s => s.Group).ToHashSet();
        foreach (var g in Enum.GetValues<CommandGroup>()) Assert.Contains(g, groups);
    }

    [Fact]
    public void Every_command_in_protocol_doc_is_in_allowlist_and_vice_versa()
    {
        var doc = File.ReadAllText(FindRepoFile("docs/link-protocol.md"));
        foreach (var spec in CommandAllowlist.All)
            Assert.True(doc.Contains($"`{spec.Name}`"), $"{spec.Name} nie jest opisane w docs/link-protocol.md");
    }

    internal static string FindRepoFile(string relative)
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, relative))) dir = dir.Parent;
        return dir == null ? throw new FileNotFoundException(relative) : Path.Combine(dir.FullName, relative);
    }
}
