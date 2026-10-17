using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Pairing;
using OpenAirBookLink.Core.Protocol;
using OpenAirBookLink.Core.Server;
using OpenAirBookLink.Core.Sources;

namespace OpenAirBookLink.Tests;

// Serwer na losowym porcie + symulacja + prawdziwy klient WebSocket.
public sealed class LinkServerTests : IAsyncLifetime
{
    private readonly PairingManager _pairing = new();
    private readonly AxisController _axes = new();
    private LinkServer _server = null!;
    private SimulationSource _sim = null!;

    public Task InitializeAsync()
    {
        _server = new LinkServer(_pairing, _axes, "test", port: 0);
        _sim = new SimulationSource(_axes);
        _sim.Start();
        _server.Start();
        _server.Source = _sim;
        return Task.CompletedTask;
    }

    public Task DisposeAsync()
    {
        _server.Dispose();
        _sim.Dispose();
        return Task.CompletedTask;
    }

    [Fact]
    public async Task Pairs_with_code_streams_state_and_accepts_commands()
    {
        using var ws = await ConnectAsync();
        await SendAsync(ws, new { v = 1, type = "hello", id = "h1", code = _pairing.Code, client = new { name = "Test" }, rateHz = 20 });
        var ok = await ReceiveTypeAsync(ws, "hello_ok");
        Assert.Equal("h1", ok.GetProperty("id").GetString());
        Assert.Equal("simulation", ok.GetProperty("source").GetString());
        Assert.False(string.IsNullOrEmpty(ok.GetProperty("token").GetString()));

        var state = await ReceiveTypeAsync(ws, "state");
        Assert.Equal("simulation", state.GetProperty("data").GetProperty("source").GetString());

        await SendAsync(ws, new { v = 1, type = "command", id = "c1", name = "HEADING_BUG_SET", value = 123 });
        var ack = await ReceiveTypeAsync(ws, "ack");
        Assert.Equal("c1", ack.GetProperty("id").GetString());

        // Komenda widoczna w kolejnych `state`
        var deadline = DateTime.UtcNow.AddSeconds(5);
        while (true)
        {
            var s = await ReceiveTypeAsync(ws, "state");
            if (s.GetProperty("data").GetProperty("autopilot").GetProperty("hdgDeg").GetDouble() == 123) break;
            Assert.True(DateTime.UtcNow < deadline, "HEADING_BUG_SET nie trafił do stanu");
        }
    }

    [Fact]
    public async Task Rejects_command_outside_allowlist()
    {
        using var ws = await PairedAsync();
        await SendAsync(ws, new { v = 1, type = "command", id = "x", name = "SITUATION_RESET" });
        var err = await ReceiveTypeAsync(ws, "error");
        Assert.Equal("x", err.GetProperty("id").GetString());
        Assert.Equal(ErrorCodes.NotAllowed, err.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Commands_before_pairing_are_refused()
    {
        using var ws = await ConnectAsync();
        await SendAsync(ws, new { v = 1, type = "command", id = "c", name = "AP_MASTER" });
        var err = await ReceiveTypeAsync(ws, "error");
        Assert.Equal(ErrorCodes.NotPaired, err.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Wrong_code_fails_and_token_reconnects()
    {
        using (var bad = await ConnectAsync())
        {
            var wrong = _pairing.Code == "000000" ? "111111" : "000000";
            await SendAsync(bad, new { v = 1, type = "hello", id = "h", code = wrong });
            Assert.Equal(ErrorCodes.PairingFailed, (await ReceiveTypeAsync(bad, "error")).GetProperty("code").GetString());
        }

        string token;
        using (var first = await ConnectAsync())
        {
            await SendAsync(first, new { v = 1, type = "hello", id = "h", code = _pairing.Code });
            token = (await ReceiveTypeAsync(first, "hello_ok")).GetProperty("token").GetString()!;
        }

        _pairing.RegenerateCode();
        using var second = await ConnectAsync();
        await SendAsync(second, new { v = 1, type = "hello", id = "h2", token });
        Assert.Equal(token, (await ReceiveTypeAsync(second, "hello_ok")).GetProperty("token").GetString());
    }

    [Fact]
    public async Task Ping_gets_pong_with_same_id()
    {
        using var ws = await ConnectAsync();
        await SendAsync(ws, new { v = 1, type = "ping", id = "p9", t = 42 });
        var pong = await ReceiveTypeAsync(ws, "pong");
        Assert.Equal("p9", pong.GetProperty("id").GetString());
        Assert.Equal(42, pong.GetProperty("t").GetInt64());
    }

    [Fact]
    public async Task Axis_control_takes_over_and_disconnect_releases()
    {
        var ws = await PairedAsync();
        await SendAsync(ws, new { v = 1, type = "axis", aileron = 2.0, elevator = 0.1, rudder = 0 });
        await WaitUntil(() => _axes.IsActive);
        Assert.Equal(_axes.MaxDeflection, _axes.Peek(Environment.TickCount64).Aileron);

        ws.Abort();
        ws.Dispose();
        await WaitUntil(() => !_axes.IsActive);
    }

    [Fact]
    public async Task Bad_axis_value_returns_error()
    {
        using var ws = await PairedAsync();
        await SendAsync(ws, new { v = 1, type = "axis", aileron = "lewo" });
        Assert.Equal(ErrorCodes.InvalidValue, (await ReceiveTypeAsync(ws, "error")).GetProperty("code").GetString());
    }

    [Fact]
    public async Task Hello_ok_advertises_navdata_and_touchdown()
    {
        using var ws = await ConnectAsync();
        await SendAsync(ws, new { v = 1, type = "hello", id = "h", code = _pairing.Code });
        var ok = await ReceiveTypeAsync(ws, "hello_ok");
        var features = ok.GetProperty("features").EnumerateArray().Select(f => f.GetString()).ToList();
        Assert.Contains("navdata", features);
        Assert.Contains("touchdown", features);
        Assert.Equal(10, ok.GetProperty("limits").GetProperty("navdataPerSecond").GetInt32());
    }

    [Fact]
    public async Task Navdata_for_EPWA_and_EPGD_in_simulation()
    {
        using var ws = await PairedAsync();

        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "a1", kind = "airport", icao = "epwa" });
        var airport = await ReceiveTypeAsync(ws, "navdata");
        Assert.Equal("a1", airport.GetProperty("id").GetString());
        Assert.Equal("airport", airport.GetProperty("kind").GetString());
        var data = airport.GetProperty("data");
        Assert.Equal("EPWA", data.GetProperty("icao").GetString());
        Assert.Equal(4, data.GetProperty("runways").GetArrayLength());

        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "p1", kind = "procedures", icao = "EPGD" });
        var procs = (await ReceiveTypeAsync(ws, "navdata")).GetProperty("data");
        Assert.Equal(2, procs.GetProperty("departures").GetArrayLength());
        Assert.Equal(2, procs.GetProperty("arrivals").GetArrayLength());
        Assert.Equal("ILS 29", procs.GetProperty("approaches")[0].GetProperty("name").GetString());

        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "l1", kind = "procedure_legs", icao = "EPGD", procType = "approach", name = "ILS 29" });
        var legs = await ReceiveTypeAsync(ws, "navdata");
        Assert.Equal("l1", legs.GetProperty("id").GetString());
        Assert.Equal("RW29", legs.GetProperty("data").GetProperty("legs")[2].GetProperty("fix").GetString());
        Assert.Equal(2, legs.GetProperty("data").GetProperty("missed").GetArrayLength());

        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "f1", kind = "fix", ident = "WAR", nearLat = 52.2, nearLon = 21.0 });
        var fix = (await ReceiveTypeAsync(ws, "navdata")).GetProperty("data").GetProperty("results")[0];
        Assert.Equal("vor", fix.GetProperty("type").GetString());

        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "x1", kind = "airport", icao = "KJFK" });
        var err = await ReceiveTypeAsync(ws, "error");
        Assert.Equal("x1", err.GetProperty("id").GetString());
        Assert.Equal(ErrorCodes.NotFound, err.GetProperty("code").GetString());

        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "x2", kind = "weather", icao = "EPWA" });
        Assert.Equal(ErrorCodes.InvalidValue, (await ReceiveTypeAsync(ws, "error")).GetProperty("code").GetString());
    }

    [Fact]
    public async Task Navdata_is_limited_to_ten_per_second()
    {
        using var ws = await PairedAsync();
        for (var i = 0; i < 15; i++)
            await SendAsync(ws, new { v = 1, type = "navdata_request", id = $"r{i}", kind = "airport", icao = "EPGD" });

        int ok = 0, limited = 0;
        while (ok + limited < 15)
        {
            var m = await ReceiveAnyAsync(ws, "navdata", "error");
            if (m.GetProperty("type").GetString() == "navdata") ok++;
            else if (m.GetProperty("code").GetString() == ErrorCodes.RateLimited) limited++;
        }
        Assert.InRange(ok, 10, 11);
        Assert.InRange(limited, 4, 5);
    }

    [Fact]
    public async Task Navdata_without_source_is_sim_unavailable()
    {
        using var ws = await PairedAsync();
        _server.Source = null;
        await SendAsync(ws, new { v = 1, type = "navdata_request", id = "n", kind = "airport", icao = "EPWA" });
        Assert.Equal(ErrorCodes.SimUnavailable, (await ReceiveTypeAsync(ws, "error")).GetProperty("code").GetString());
    }

    [Fact]
    public async Task Touchdown_reaches_client_in_simulation()
    {
        using var server = new LinkServer(_pairing, _axes, "test", port: 0);
        using var sim = new SimulationSource(new AxisController(), timeScale: 20, startOnFinalNm: 1.5);
        sim.Start();
        server.Start();
        server.Source = sim;

        using var ws = new ClientWebSocket();
        await ws.ConnectAsync(new Uri($"ws://127.0.0.1:{server.Port}/"), CancellationToken.None);
        await SendAsync(ws, new { v = 1, type = "hello", id = "h", code = _pairing.Code, rateHz = 20 });
        await ReceiveTypeAsync(ws, "hello_ok");

        var deadline = DateTime.UtcNow.AddSeconds(20);
        while (true)
        {
            var data = (await ReceiveTypeAsync(ws, "state")).GetProperty("data");
            if (data.TryGetProperty("touchdown", out var td))
            {
                Assert.Equal(1, td.GetProperty("seq").GetInt64());
                Assert.Equal(-180, td.GetProperty("vsFpm").GetDouble());
                Assert.Equal(1.2, td.GetProperty("gForce").GetDouble());
                Assert.Equal("airliner", data.GetProperty("aircraft").GetProperty("category").GetString());
                break;
            }
            Assert.True(DateTime.UtcNow < deadline, "Brak touchdown w state");
        }
    }

    private async Task<ClientWebSocket> ConnectAsync()
    {
        var ws = new ClientWebSocket();
        await ws.ConnectAsync(new Uri($"ws://127.0.0.1:{_server.Port}/"), CancellationToken.None);
        return ws;
    }

    private async Task<ClientWebSocket> PairedAsync()
    {
        var ws = await ConnectAsync();
        await SendAsync(ws, new { v = 1, type = "hello", id = "h", code = _pairing.Code });
        await ReceiveTypeAsync(ws, "hello_ok");
        return ws;
    }

    private static Task SendAsync(ClientWebSocket ws, object message) =>
        ws.SendAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(message)), WebSocketMessageType.Text, true, CancellationToken.None);

    internal static Task<JsonElement> ReceiveTypeAsync(ClientWebSocket ws, string type) => ReceiveAnyAsync(ws, type);

    internal static async Task<JsonElement> ReceiveAnyAsync(ClientWebSocket ws, params string[] types)
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var buffer = new byte[64 * 1024];
        while (true)
        {
            var length = 0;
            ValueWebSocketReceiveResult r;
            do
            {
                r = await ws.ReceiveAsync(buffer.AsMemory(length), cts.Token);
                length += r.Count;
            } while (!r.EndOfMessage);
            var root = JsonDocument.Parse(Encoding.UTF8.GetString(buffer, 0, length)).RootElement;
            Assert.Equal(1, root.GetProperty("v").GetInt32());
            if (types.Contains(root.GetProperty("type").GetString())) return root;
        }
    }

    private static async Task WaitUntil(Func<bool> condition)
    {
        var deadline = DateTime.UtcNow.AddSeconds(5);
        while (!condition())
        {
            Assert.True(DateTime.UtcNow < deadline, "Warunek nie spełniony w 5 s");
            await Task.Delay(20);
        }
    }
}
