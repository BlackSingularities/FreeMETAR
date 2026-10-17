using System.Text.Json;
using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Protocol;
using OpenAirBookLink.Core.Sources;

namespace OpenAirBookLink.Tests;

public class TouchdownTests
{
    private static TouchdownSample S(long t, bool ground, double vs = -200, double g = 1.0, double? tdVs = null) =>
        new(t, 1_700_000_000_000 + t, ground, vs, g, 3, -1, 290, 54.38, 18.47, 135, 130, TdVsFpm: tdVs);

    [Fact]
    public void No_touchdown_while_parked_or_on_first_ground_frame()
    {
        var latch = new TouchdownLatch();
        for (var t = 0; t < 2000; t += 50) latch.Update(S(t, ground: true));
        Assert.Null(latch.Current);
    }

    [Fact]
    public void Latches_touchdown_and_keeps_it_after_rollout()
    {
        var latch = new TouchdownLatch();
        for (var t = 0; t < 3000; t += 50) latch.Update(S(t, ground: false, vs: -210, g: 1.02));
        latch.Update(S(3000, ground: true, vs: 0, g: 1.15));
        var td = latch.Current!;
        Assert.Equal(1, td.Seq);
        Assert.Equal(-210, td.VsFpm);        // ostatnia klatka w powietrzu (brak PLANE TOUCHDOWN *)
        Assert.Equal(1_700_000_003_000, td.T);

        // Szczyt przeciążenia w oknie po kontakcie
        latch.Update(S(3100, ground: true, vs: 0, g: 1.38));
        latch.Update(S(3400, ground: true, vs: 0, g: 1.05));
        Assert.Equal(1.38, latch.Current!.GForce);

        // Po oknie wartości się nie zmieniają, seq zostaje
        for (var t = 3700; t < 20_000; t += 50) latch.Update(S(t, ground: true, vs: 0, g: 1.9));
        Assert.Equal(1.38, latch.Current!.GForce);
        Assert.Equal(1, latch.Current.Seq);
    }

    [Fact]
    public void Bounce_increments_seq()
    {
        var latch = new TouchdownLatch();
        for (var t = 0; t < 2000; t += 50) latch.Update(S(t, ground: false));
        latch.Update(S(2000, ground: true));
        // odbicie: 0,5 s w powietrzu
        for (var t = 2050; t < 2550; t += 50) latch.Update(S(t, ground: false, vs: 120));
        latch.Update(S(2550, ground: true));
        Assert.Equal(2, latch.Current!.Seq);
        // krótkie mignięcie (np. drganie amortyzatora) się nie liczy
        latch.Update(S(2600, ground: false));
        latch.Update(S(2650, ground: true));
        Assert.Equal(2, latch.Current!.Seq);
    }

    [Fact]
    public void Prefers_fresh_touchdown_simvars_and_ignores_stale_ones()
    {
        var latch = new TouchdownLatch();
        // W powietrzu MSFS wciąż trzyma wartość poprzedniego lądowania (−400)
        for (var t = 0; t < 2000; t += 50) latch.Update(S(t, ground: false, vs: -150, tdVs: 400));
        latch.Update(S(2000, ground: true, tdVs: 400));
        Assert.Equal(-150, latch.Current!.VsFpm);
        // Klatkę później MSFS podaje świeżą wartość (dodatnią przy opadaniu) → −172
        latch.Update(S(2050, ground: true, tdVs: 172));
        Assert.Equal(-172, latch.Current!.VsFpm);
    }

    [Fact]
    public void Simulation_latches_touchdown_at_EPGD()
    {
        var sim = new FlightSimulation();
        sim.PositionOnFinal(4);
        Assert.Equal(SimPhase.Approach, sim.Phase);
        var now = new DateTime(2026, 10, 18, 12, 0, 0, DateTimeKind.Utc);
        for (var i = 0; i < 600 && sim.Phase == SimPhase.Approach; i++) sim.Step(0.5, AxisOutput.Neutral, now);

        Assert.Equal(SimPhase.Rollout, sim.Phase);
        var td = sim.State.Touchdown!;
        Assert.Equal(1, td.Seq);
        Assert.Equal(-180, td.VsFpm);
        Assert.Equal(1.2, td.GForce);
        Assert.True(FlightSimulation.DistanceNm(td.Lat, td.Lon, FlightSimulation.Arrival.Lat, FlightSimulation.Arrival.Lon) < 1.5);
        Assert.InRange(td.HeadingTrueDeg, 280, 300);

        // zatrzaśnięte: zostaje w stanie podczas dobiegu i w JSON `state`
        for (var i = 0; i < 20; i++) sim.Step(0.5, AxisOutput.Neutral, now);
        using var doc = JsonDocument.Parse(LinkJson.Serialize(new StateMessage { Data = sim.State }));
        var t = doc.RootElement.GetProperty("data").GetProperty("touchdown");
        Assert.Equal(1, t.GetProperty("seq").GetInt64());
        foreach (var f in new[] { "t", "vsFpm", "gForce", "pitchDeg", "bankDeg", "headingTrueDeg", "lat", "lon", "iasKt", "gsKt" })
            Assert.True(t.TryGetProperty(f, out _), f);
    }

    [Fact]
    public void State_has_aircraft_profile_and_no_touchdown_before_landing()
    {
        var sim = new FlightSimulation();
        using var doc = JsonDocument.Parse(LinkJson.Serialize(new StateMessage { Data = sim.State }));
        var data = doc.RootElement.GetProperty("data");
        var ac = data.GetProperty("aircraft");
        Assert.Equal("jet", ac.GetProperty("engineType").GetString());
        Assert.Equal("A320", ac.GetProperty("atcModel").GetString());
        Assert.Equal("AIRBUS", ac.GetProperty("atcType").GetString());
        Assert.Equal("airliner", ac.GetProperty("category").GetString());
        Assert.False(data.TryGetProperty("touchdown", out _));
    }

    [Theory]
    [InlineData("jet", 2, 170_000, "Airbus A320neo", "", "airliner")]
    [InlineData("jet", 2, 17_000, "Cessna Citation CJ4", "", "bizjet")]
    [InlineData("jet", 2, 0, "Unknown Jet", "", "bizjet")]
    [InlineData("jet", 4, 0, "Unknown Jet", "", "airliner")]
    [InlineData("turboprop", 1, 7_400, "Daher TBM 930", "", "turboprop")]
    [InlineData("turboprop", 2, 50_000, "ATR 72-600", "", "airliner")]
    [InlineData("piston", 1, 2_550, "Cessna 172", "", "ga")]
    [InlineData("none", 0, 1_200, "Glider", "", "ga")]
    [InlineData("helicopter", 2, 6_000, "Airbus H135", "", "helicopter")]
    [InlineData("piston", 1, 1_600, "Cabri G2", "Helicopter", "helicopter")]
    public void Aircraft_category_heuristic(string engine, int count, double mtow, string title, string simCategory, string expected) =>
        Assert.Equal(expected, AircraftClassifier.Classify(engine, count, mtow, title, simCategory));

    [Theory]
    [InlineData("TT:ATCCOM.AC_MODEL_A320.0.text", "A320")]
    [InlineData("TT:ATCCOM.ATC_NAME AIRBUS.0.text", "AIRBUS")]
    [InlineData("C172", "C172")]
    [InlineData("", "")]
    public void Atc_strings_are_cleaned(string raw, string expected) => Assert.Equal(expected, AircraftClassifier.CleanAtcString(raw));

    [Theory]
    [InlineData(0, "piston")]
    [InlineData(1, "jet")]
    [InlineData(2, "none")]
    [InlineData(3, "helicopter")]
    [InlineData(5, "turboprop")]
    public void Engine_type_names(int v, string expected) => Assert.Equal(expected, AircraftClassifier.EngineTypeName(v));

    [Fact]
    public void Simulation_accepts_every_allowlisted_command()
    {
        foreach (var spec in CommandAllowlist.All)
        {
            var sim = new FlightSimulation();
            var value = spec.NeedsValue ? JsonDocument.Parse(spec.Min!.Value.ToString()).RootElement : (JsonElement?)null;
            var v = CommandAllowlist.Validate(spec.Name, value);
            Assert.True(v.Ok, $"{spec.Name}: {v.ErrorMessage}");
            Assert.True(sim.Execute(v.Command!).Ok, spec.Name);
        }
    }
}
