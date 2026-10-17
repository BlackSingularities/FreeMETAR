using System.Text.Json;
using OpenAirBookLink.Core.NavData;
using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Tests;

public class NavDataTests
{
    private readonly SimulationNavData _sim = new();

    private static IncomingMessage Msg(object o)
    {
        var json = JsonSerializer.Serialize(o);
        var m = IncomingMessage.Parse(json, out var err);
        Assert.Null(err);
        return m!;
    }

    private static NavdataQuery Query(object o)
    {
        var q = NavdataQuery.Parse(Msg(o), out var error);
        Assert.True(q != null, error);
        return q!;
    }

    [Fact]
    public async Task Simulation_airports_have_runways_with_ils_and_frequencies()
    {
        foreach (var icao in new[] { "EPWA", "EPGD" })
        {
            var a = await _sim.GetAirportAsync(icao, default);
            Assert.Equal(icao, a.Icao);
            Assert.True(a.Runways.Count >= 2);
            Assert.Contains(a.Runways, r => r.Ils != null && r.Ils.FreqMhz is >= 108 and <= 112 && r.Ils.GsAngleDeg == 3.0);
            Assert.Contains(a.Frequencies, f => f.Type == FrequencyTypes.Tower);
            foreach (var r in a.Runways)
                Assert.True(NavMath.DistanceNm(a.Lat, a.Lon, r.ThresholdLat, r.ThresholdLon) < 3, $"{icao} {r.Ident} za daleko od lotniska");
        }

        var gd29 = (await _sim.GetAirportAsync("EPGD", default)).Runways.Single(r => r.Ident == "29");
        Assert.Equal(109.3, gd29.Ils!.FreqMhz);
        Assert.Equal(54.3790, gd29.ThresholdLat);
    }

    [Fact]
    public async Task Simulation_procedures_two_sids_two_stars_ils_and_rnav()
    {
        foreach (var icao in new[] { "EPWA", "EPGD" })
        {
            var p = (await _sim.GetProceduresAsync(icao, default)).ToSummary();
            Assert.Equal(2, p.Departures.Count);
            Assert.Equal(2, p.Arrivals.Count);
            Assert.Contains(p.Approaches, a => a.Type == ApproachTypes.Ils);
            Assert.Contains(p.Approaches, a => a.Type == ApproachTypes.Rnav);
            Assert.All(p.Departures, d => Assert.NotEmpty(d.Runways));
        }

        var wa = (await _sim.GetProceduresAsync("EPWA", default)).ToSummary();
        var rnav = wa.Approaches.Single(a => a.Type == ApproachTypes.Rnav);
        Assert.Equal("RNAV Z 29", rnav.Name);
        Assert.Equal("Z", rnav.Suffix);
        Assert.Equal("29", rnav.Runway);
    }

    [Fact]
    public async Task Approach_legs_join_transition_and_final_and_have_missed()
    {
        var procs = await _sim.GetProceduresAsync("EPGD", default);
        var legs = procs.GetLegs(Query(new { v = 1, type = "navdata_request", kind = "procedure_legs", icao = "EPGD", procType = "approach", name = "ils 29", transition = "GD401" }));

        Assert.Equal("ILS 29", legs.Name);
        Assert.Equal("29", legs.Runway);
        Assert.Equal(new[] { "GD401", "GD291", "FI29", "RW29" }, legs.Legs.Select(l => l.Fix));  // IF GD291 nie jest dublowany
        Assert.Equal("IF", legs.Legs[0].LegType);
        Assert.Equal(AltDescs.At, legs.Legs[2].AltDesc);
        Assert.True(legs.Legs[^1].FlyOver);
        Assert.Equal(54.3790, legs.Legs[^1].Lat);
        Assert.All(legs.Legs, l => Assert.NotNull(l.Lat));
        Assert.Equal(2, legs.Missed.Count);
        Assert.Equal("CA", legs.Missed[0].LegType);
        Assert.Null(legs.Missed[0].Lat);
    }

    [Fact]
    public async Task Departure_legs_runway_then_common_then_enroute()
    {
        var procs = await _sim.GetProceduresAsync("EPWA", default);
        var legs = procs.GetLegs(Query(new { v = 1, type = "x", kind = "procedure_legs", icao = "EPWA", procType = "departure", name = "LOGDA1K", runway = "RW15", transition = "NEPOX" }));
        Assert.Equal("15", legs.Runway);
        Assert.Equal(new[] { "VA", "DF", "TF", "TF" }, legs.Legs.Select(l => l.LegType));
        Assert.Equal(new[] { "", "WA542", "LOGDA", "NEPOX" }, legs.Legs.Select(l => l.Fix));
        Assert.Equal(220, legs.Legs[1].SpeedKt);

        var ex = Assert.Throws<NavDataException>(() => procs.GetLegs(Query(new { v = 1, type = "x", kind = "procedure_legs", icao = "EPWA", procType = "departure", name = "LOGDA1K", runway = "33" })));
        Assert.Equal(ErrorCodes.NotFound, ex.Code);
    }

    [Fact]
    public async Task Arrival_legs_common_then_runway_transition()
    {
        var procs = await _sim.GetProceduresAsync("EPWA", default);
        var legs = procs.GetLegs(Query(new { v = 1, type = "x", kind = "procedure_legs", icao = "EPWA", procType = "arrival", name = "BAMSO1S", runway = "29" }));
        Assert.Equal(new[] { "BAMSO", "WA421", "WA291" }, legs.Legs.Select(l => l.Fix));
        Assert.Equal(AltDescs.Between, legs.Legs[1].AltDesc);
        Assert.Equal(5000, legs.Legs[1].Alt2Ft);
    }

    [Fact]
    public async Task Unknown_airport_is_not_found()
    {
        var ex = await Assert.ThrowsAsync<NavDataException>(() => _sim.GetAirportAsync("KJFK", default));
        Assert.Equal(ErrorCodes.NotFound, ex.Code);
    }

    [Fact]
    public async Task Fix_results_sorted_by_distance()
    {
        var svc = new NavDataService();
        var near = (FixSearchInfo)await svc.ExecuteAsync(Query(new { v = 1, type = "x", kind = "fix", ident = "soxer", nearLat = 50.0, nearLon = 8.5 }), _sim, "simulation", default);
        Assert.Equal(2, near.Results.Count);
        Assert.Equal("ED", near.Results[0].Region);

        var far = (FixSearchInfo)await svc.ExecuteAsync(Query(new { v = 1, type = "x", kind = "fix", ident = "SOXER", nearLat = 52.2, nearLon = 21.0 }), _sim, "simulation", default);
        Assert.Equal("EP", far.Results[0].Region);

        var vor = (FixSearchInfo)await svc.ExecuteAsync(Query(new { v = 1, type = "x", kind = "fix", ident = "WAR" }), _sim, "simulation", default);
        Assert.Equal(FixTypes.Vor, vor.Results.Single().Type);
        Assert.Equal(113.45, vor.Results[0].FreqMhz);

        var ex = await Assert.ThrowsAsync<NavDataException>(() => svc.ExecuteAsync(Query(new { v = 1, type = "x", kind = "fix", ident = "NOSUCH" }), _sim, "simulation", default));
        Assert.Equal(ErrorCodes.NotFound, ex.Code);
    }

    [Theory]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"metar\",\"icao\":\"EPWA\"}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"airport\"}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"airport\",\"icao\":\"EP WA\"}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"airport\",\"icao\":\"EPWAEPWAX\"}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"procedure_legs\",\"icao\":\"EPWA\",\"procType\":\"sid\",\"name\":\"X\"}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"procedure_legs\",\"icao\":\"EPWA\",\"procType\":\"departure\"}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"fix\",\"ident\":\"WAR\",\"nearLat\":52}")]
    [InlineData("{\"v\":1,\"type\":\"navdata_request\",\"kind\":\"fix\",\"ident\":\"WAR\",\"nearLat\":95,\"nearLon\":20}")]
    public void Invalid_requests_are_rejected(string json)
    {
        var msg = IncomingMessage.Parse(json, out _)!;
        Assert.Null(NavdataQuery.Parse(msg, out var error));
        Assert.False(string.IsNullOrEmpty(error));
    }

    [Fact]
    public async Task Cache_keeps_results_for_ten_minutes_and_shares_concurrent_loads()
    {
        var now = new DateTime(2026, 10, 18, 12, 0, 0, DateTimeKind.Utc);
        var svc = new NavDataService(() => now);
        var provider = new CountingProvider();
        var q = Query(new { v = 1, type = "x", kind = "airport", icao = "EPWA" });

        await Task.WhenAll(Enumerable.Range(0, 5).Select(_ => svc.ExecuteAsync(q, provider, "msfs", default)));
        Assert.Equal(1, provider.AirportCalls);

        now += TimeSpan.FromMinutes(9);
        await svc.ExecuteAsync(q, provider, "msfs", default);
        Assert.Equal(1, provider.AirportCalls);

        // Inne źródło (symulacja zamiast MSFS) ma osobny wpis
        await svc.ExecuteAsync(q, provider, "simulation", default);
        Assert.Equal(2, provider.AirportCalls);

        now += TimeSpan.FromMinutes(2);
        await svc.ExecuteAsync(q, provider, "msfs", default);
        Assert.Equal(3, provider.AirportCalls);

        // procedures i procedure_legs korzystają z jednego pobrania drzewa procedur
        await svc.ExecuteAsync(Query(new { v = 1, type = "x", kind = "procedures", icao = "EPGD" }), provider, "msfs", default);
        await svc.ExecuteAsync(Query(new { v = 1, type = "x", kind = "procedure_legs", icao = "EPGD", procType = "approach", name = "ILS 29" }), provider, "msfs", default);
        Assert.Equal(1, provider.ProcedureCalls);
    }

    [Fact]
    public async Task Errors_are_not_cached()
    {
        var svc = new NavDataService();
        var provider = new CountingProvider();
        var q = Query(new { v = 1, type = "x", kind = "airport", icao = "LFPG" });
        await Assert.ThrowsAsync<NavDataException>(() => svc.ExecuteAsync(q, provider, "msfs", default));
        await Assert.ThrowsAsync<NavDataException>(() => svc.ExecuteAsync(q, provider, "msfs", default));
        Assert.Equal(2, provider.AirportCalls);
    }

    [Fact]
    public void Token_bucket_allows_ten_per_second()
    {
        long now = 0;
        var bucket = new TokenBucket(LinkProtocol.MaxNavdataPerSecond, clockMs: () => now);
        for (var i = 0; i < 10; i++) Assert.True(bucket.TryTake());
        Assert.False(bucket.TryTake());
        now += 100;
        Assert.True(bucket.TryTake());
        Assert.False(bucket.TryTake());
        now += 5000;
        for (var i = 0; i < 10; i++) Assert.True(bucket.TryTake());
        Assert.False(bucket.TryTake());
    }

    [Theory]
    [InlineData("RW29", "29")]
    [InlineData("rwy 9", "09")]
    [InlineData("11l", "11L")]
    [InlineData("33", "33")]
    public void Runway_names_are_normalized(string input, string expected) =>
        Assert.Equal(expected, NavMath.NormalizeRunway(input));

    [Theory]
    [InlineData(29, 0, "29")]
    [InlineData(9, 1, "09L")]
    [InlineData(36, 3, "36C")]
    [InlineData(37, 0, "N")]
    [InlineData(0, 0, "")]
    public void Msfs_runway_numbers_to_ident(int number, int designator, string expected) =>
        Assert.Equal(expected, NavMath.RunwayIdent(number, designator));

    [Fact]
    public async Task Navdata_json_shapes_are_stable()
    {
        var airport = LinkJson.Serialize(new NavdataMessage { Id = "n1", Kind = "airport", Data = await _sim.GetAirportAsync("EPWA", default) });
        using var a = JsonDocument.Parse(airport);
        Assert.StartsWith("{\"v\":1,\"type\":\"navdata\"", airport);
        var rwy = a.RootElement.GetProperty("data").GetProperty("runways")[1];
        Assert.Equal("29", rwy.GetProperty("ident").GetString());
        Assert.Equal(JsonValueKind.Null, rwy.GetProperty("ils").ValueKind);  // null jest jawnie w JSON
        foreach (var f in new[] { "headingTrueDeg", "lengthFt", "widthFt", "thresholdLat", "thresholdLon", "elevFt" })
            Assert.True(rwy.TryGetProperty(f, out _), f);
        var ils = a.RootElement.GetProperty("data").GetProperty("runways")[3].GetProperty("ils");
        foreach (var f in new[] { "ident", "freqMhz", "locCourseDeg", "gsAngleDeg" }) Assert.True(ils.TryGetProperty(f, out _), f);

        var legs = (await _sim.GetProceduresAsync("EPWA", default)).GetLegs(Query(new { v = 1, type = "x", kind = "procedure_legs", icao = "EPWA", procType = "approach", name = "ILS 33" }));
        using var l = JsonDocument.Parse(LinkJson.Serialize(new NavdataMessage { Id = "n2", Kind = "procedure_legs", Data = legs }));
        var leg = l.RootElement.GetProperty("data").GetProperty("missed")[0];
        foreach (var f in new[] { "fix", "lat", "lon", "legType", "altDesc", "alt1Ft", "alt2Ft", "speedKt", "courseDeg", "distNm", "flyOver" })
            Assert.True(leg.TryGetProperty(f, out _), f);
        Assert.Equal(JsonValueKind.Null, l.RootElement.GetProperty("data").GetProperty("transition").ValueKind);
    }

    private sealed class CountingProvider : INavDataProvider
    {
        private readonly SimulationNavData _inner = new();
        public int AirportCalls;
        public int ProcedureCalls;

        public async Task<AirportInfo> GetAirportAsync(string icao, CancellationToken ct)
        {
            Interlocked.Increment(ref AirportCalls);
            await Task.Delay(20, ct);
            return await _inner.GetAirportAsync(icao, ct);
        }

        public Task<AirportProcedures> GetProceduresAsync(string icao, CancellationToken ct)
        {
            Interlocked.Increment(ref ProcedureCalls);
            return _inner.GetProceduresAsync(icao, ct);
        }

        public Task<IReadOnlyList<FixResult>> FindFixAsync(string ident, CancellationToken ct) => _inner.FindFixAsync(ident, ct);
    }
}
