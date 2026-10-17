using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Pairing;
using OpenAirBookLink.Core.Protocol;
using OpenAirBookLink.Core.Sources;

namespace OpenAirBookLink.Tests;

public class SimulationTests
{
    private static readonly DateTime Now = new(2026, 10, 17, 12, 0, 0, DateTimeKind.Utc);

    [Fact]
    public void Flies_from_EPWA_and_lands_at_EPGD()
    {
        var sim = new FlightSimulation();
        var phases = new List<SimPhase> { sim.Phase };
        double maxAlt = 0;
        for (var i = 0; i < 4 * 3600 * 2 && sim.Phase != SimPhase.Stopped; i++)
        {
            sim.Step(0.5, AxisOutput.Neutral, Now);
            if (phases[^1] != sim.Phase) phases.Add(sim.Phase);
            maxAlt = Math.Max(maxAlt, sim.State.Position.AltMslFt);
            Assert.InRange(sim.State.Speed.IasKt, 0, 360);
            Assert.InRange(sim.State.Attitude.BankDeg, -30, 30);
        }

        Assert.Equal(SimPhase.Stopped, sim.Phase);
        Assert.Equal(new[] { SimPhase.Parked, SimPhase.TakeoffRoll, SimPhase.Climb, SimPhase.Cruise, SimPhase.Descent, SimPhase.Approach, SimPhase.Rollout, SimPhase.Stopped }, phases);
        Assert.InRange(maxAlt, 23500, 24100);
        Assert.True(sim.DistanceToArrivalNm < 3, $"Zatrzymał się {sim.DistanceToArrivalNm:F1} NM od EPGD");
        Assert.True(sim.State.Aircraft.OnGround);
        Assert.True(sim.State.Fuel.TotalKg < 6400);
    }

    [Fact]
    public void Heading_mode_steers_to_bug()
    {
        var sim = new FlightSimulation();
        for (var i = 0; i < 600 && sim.Phase != SimPhase.Cruise; i++) sim.Step(1, AxisOutput.Neutral, Now);
        Exec(sim, "HEADING_BUG_SET", 90);
        Exec(sim, "AP_HDG_HOLD");
        for (var i = 0; i < 180; i++) sim.Step(1, AxisOutput.Neutral, Now);
        Assert.InRange(sim.State.Attitude.HdgMagDeg, 85, 95);
    }

    [Fact]
    public void Commands_change_state()
    {
        var sim = new FlightSimulation();
        Exec(sim, "COM_STBY_RADIO_SET_HZ", 118_005_000);
        Exec(sim, "COM_STBY_RADIO_SWAP");
        Exec(sim, "XPNDR_SET", 7000);
        Exec(sim, "TOGGLE_TAXI_LIGHTS");
        Exec(sim, "PARKING_BRAKE_SET", 1);
        Exec(sim, "FLAPS_INCR");

        Assert.Equal(118.005, sim.State.Radios.Com1.ActiveMhz);
        Assert.Equal("7000", sim.State.Radios.Transponder.Code);
        Assert.True(sim.State.Lights.Taxi);
        Assert.True(sim.State.Controls.ParkingBrake);
        Assert.Equal(2, sim.State.Controls.FlapsIndex);

        // Hamulec postojowy wstrzymuje start
        for (var i = 0; i < 60; i++) sim.Step(0.5, AxisOutput.Neutral, Now);
        Assert.Equal(SimPhase.Parked, sim.Phase);
    }

    [Fact]
    public void Axes_bank_the_aircraft_when_airborne()
    {
        var sim = new FlightSimulation();
        for (var i = 0; i < 600 && sim.Phase != SimPhase.Cruise; i++) sim.Step(1, AxisOutput.Neutral, Now);
        for (var i = 0; i < 20; i++) sim.Step(0.5, new AxisOutput(0.8, 0, 0, false), Now);
        Assert.True(sim.State.Attitude.BankDeg > 20);
        Assert.True(sim.State.Axes.Active);
    }

    [Fact]
    public void Pairing_blocks_after_five_failures()
    {
        var p = new PairingManager();
        var wrong = p.Code == "000000" ? "111111" : "000000";
        for (var i = 0; i < PairingManager.MaxFailures; i++) Assert.False(p.TryPair("1.2.3.4", wrong, null, Now).Ok);
        var blocked = p.TryPair("1.2.3.4", p.Code, null, Now.AddSeconds(1));
        Assert.True(blocked.RateLimited);
        Assert.True(p.TryPair("1.2.3.4", p.Code, null, Now.AddSeconds(61)).Ok);
        Assert.True(p.TryPair("5.6.7.8", p.Code, null, Now).Ok);
    }

    private static void Exec(FlightSimulation sim, string name, long? value = null)
    {
        var v = value.HasValue ? System.Text.Json.JsonDocument.Parse(value.Value.ToString()).RootElement : (System.Text.Json.JsonElement?)null;
        var validation = CommandAllowlist.Validate(name, v);
        Assert.True(validation.Ok, validation.ErrorMessage);
        Assert.True(sim.Execute(validation.Command!).Ok);
    }
}
