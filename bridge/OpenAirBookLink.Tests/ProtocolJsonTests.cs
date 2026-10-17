using System.Text.Json;
using OpenAirBookLink.Core.Protocol;
using OpenAirBookLink.Core.Sources;

namespace OpenAirBookLink.Tests;

public class ProtocolJsonTests
{
    [Fact]
    public void State_message_has_version_type_and_camel_case_fields()
    {
        var sim = new FlightSimulation();
        var json = LinkJson.Serialize(new StateMessage { Seq = 7, T = 1234, Data = sim.State });
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        Assert.Equal(1, root.GetProperty("v").GetInt32());
        Assert.Equal("state", root.GetProperty("type").GetString());
        Assert.Equal(7, root.GetProperty("seq").GetInt64());
        var data = root.GetProperty("data");
        Assert.Equal("simulation", data.GetProperty("source").GetString());
        Assert.Equal(FlightSimulation.Departure.Lat, data.GetProperty("position").GetProperty("lat").GetDouble(), 6);
        Assert.True(data.GetProperty("speed").TryGetProperty("iasKt", out _));
        Assert.True(data.GetProperty("autopilot").GetProperty("modes").TryGetProperty("flc", out _));
        Assert.Equal("2000", data.GetProperty("radios").GetProperty("transponder").GetProperty("code").GetString());
        Assert.Equal(2, data.GetProperty("engines").GetArrayLength());
        Assert.True(data.GetProperty("fuel").GetProperty("tanks").TryGetProperty("centerKg", out _));
    }

    [Fact]
    public void Version_and_type_come_first()
    {
        var json = LinkJson.Serialize(new AckMessage { Id = "c1", Name = "AP_MASTER" });
        Assert.StartsWith("{\"v\":1,\"type\":\"ack\"", json);
    }

    [Fact]
    public void Error_message_always_contains_id_even_when_null()
    {
        var json = LinkJson.Serialize(new ErrorMessage { Code = ErrorCodes.BadJson, Message = "x" });
        using var doc = JsonDocument.Parse(json);
        Assert.Equal(JsonValueKind.Null, doc.RootElement.GetProperty("id").ValueKind);
    }

    [Fact]
    public void Sim_time_is_iso_utc()
    {
        var state = new FlightState { SimTimeUtc = new DateTime(2026, 10, 17, 12, 34, 56, DateTimeKind.Utc) };
        var json = JsonSerializer.Serialize(state, LinkJson.Options);
        Assert.Contains("\"simTimeUtc\":\"2026-10-17T12:34:56Z\"", json);
    }

    [Fact]
    public void State_round_trips()
    {
        var sim = new FlightSimulation();
        var copy = sim.State.Clone();
        Assert.Equal(sim.State.Radios.Com1.ActiveMhz, copy.Radios.Com1.ActiveMhz);
        Assert.Equal(sim.State.Lights.Beacon, copy.Lights.Beacon);
        Assert.NotSame(sim.State.Position, copy.Position);
    }

    [Theory]
    [InlineData("{\"v\":1,\"type\":\"ping\",\"id\":\"p1\"}", "ping", "p1")]
    [InlineData("{\"v\":1,\"type\":\"command\",\"id\":42,\"name\":\"AP_MASTER\"}", "command", "42")]
    public void Parses_valid_messages(string text, string type, string id)
    {
        var msg = IncomingMessage.Parse(text, out var error);
        Assert.Null(error);
        Assert.NotNull(msg);
        Assert.Equal(type, msg!.Type);
        Assert.Equal(id, msg.Id);
    }

    [Theory]
    [InlineData("nie json", ErrorCodes.BadJson)]
    [InlineData("[1,2]", ErrorCodes.BadJson)]
    [InlineData("{\"type\":\"ping\"}", ErrorCodes.UnsupportedVersion)]
    [InlineData("{\"v\":2,\"type\":\"ping\"}", ErrorCodes.UnsupportedVersion)]
    [InlineData("{\"v\":1}", ErrorCodes.BadJson)]
    public void Rejects_invalid_messages(string text, string code)
    {
        var msg = IncomingMessage.Parse(text, out var error);
        Assert.Null(msg);
        Assert.Equal(code, error!.Code);
    }

    [Fact]
    public void Announce_contains_port_and_name()
    {
        var json = LinkJson.Serialize(new AnnounceMessage
        {
            Version = "1.0.5", Host = "PC", Port = 47810, Ips = new[] { "192.168.1.2" }, Source = "none",
        });
        using var doc = JsonDocument.Parse(json);
        Assert.Equal("announce", doc.RootElement.GetProperty("type").GetString());
        Assert.Equal("OpenAirBook Link", doc.RootElement.GetProperty("name").GetString());
        Assert.Equal(47810, doc.RootElement.GetProperty("port").GetInt32());
    }
}
