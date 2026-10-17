using OpenAirBookLink.Core.Control;

namespace OpenAirBookLink.Tests;

public class AxisControllerTests
{
    [Fact]
    public void Deflection_is_clamped_to_limit()
    {
        var axes = new AxisController(maxDeflection: 0.8);
        Assert.Equal(AxisUpdateResult.Ok, axes.Update("a", 1.0, -1.0, 0.5, nowMs: 0));
        var o = axes.Tick(10)!;
        Assert.Equal(0.8, o.Aileron);
        Assert.Equal(-0.8, o.Elevator);
        Assert.Equal(0.5, o.Rudder);
        Assert.False(o.Final);
    }

    [Fact]
    public void Auto_neutral_after_500ms_without_updates()
    {
        var axes = new AxisController();
        axes.Update("a", 0.4, 0.2, -0.1, nowMs: 1000);

        Assert.False(axes.Tick(1400)!.Final);          // 400 ms — jeszcze steruje
        Assert.True(axes.IsActive);

        var neutral = axes.Tick(1501)!;                 // 501 ms bez aktualizacji
        Assert.True(neutral.Final);
        Assert.Equal((0.0, 0.0, 0.0), (neutral.Aileron, neutral.Elevator, neutral.Rudder));
        Assert.False(axes.IsActive);

        Assert.Null(axes.Tick(1550));                   // neutral wysyłany tylko raz, potem cisza
    }

    [Fact]
    public void Each_update_resets_the_timeout()
    {
        var axes = new AxisController();
        for (long t = 0; t <= 3000; t += 300) axes.Update("a", 0.3, null, null, t);
        Assert.False(axes.Tick(3400)!.Final);
        Assert.True(axes.Tick(3501)!.Final);
    }

    [Fact]
    public void Missing_axis_keeps_previous_value()
    {
        var axes = new AxisController();
        axes.Update("a", 0.3, 0.2, 0.1, 0);
        axes.Update("a", null, -0.5, null, 50);
        var o = axes.Tick(60)!;
        Assert.Equal((0.3, -0.5, 0.1), (o.Aileron, o.Elevator, o.Rudder));
    }

    [Fact]
    public void Only_one_owner_until_released_or_timed_out()
    {
        var axes = new AxisController();
        axes.Update("a", 0.1, 0, 0, 0);
        Assert.Equal(AxisUpdateResult.Busy, axes.Update("b", 0.5, 0, 0, 100));

        axes.Release("a");
        Assert.True(axes.Tick(110)!.Final);
        Assert.Equal(AxisUpdateResult.Ok, axes.Update("b", 0.5, 0, 0, 120));
        Assert.Equal("b", axes.Owner);

        // b milknie — po 500 ms a może przejąć
        Assert.Equal(AxisUpdateResult.Ok, axes.Update("a", 0.2, 0, 0, 700));
    }

    [Fact]
    public void Release_by_non_owner_is_ignored()
    {
        var axes = new AxisController();
        axes.Update("a", 0.1, 0, 0, 0);
        axes.Release("b");
        Assert.True(axes.IsActive);
    }

    [Theory]
    [InlineData(double.NaN)]
    [InlineData(double.PositiveInfinity)]
    public void Non_finite_values_are_rejected(double v)
    {
        var axes = new AxisController();
        Assert.Equal(AxisUpdateResult.Invalid, axes.Update("a", v, 0, 0, 0));
        Assert.False(axes.IsActive);
    }
}
