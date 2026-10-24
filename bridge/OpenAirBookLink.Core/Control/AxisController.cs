namespace OpenAirBookLink.Core.Control;

public sealed record AxisOutput(double Aileron, double Elevator, double Rudder, bool Final)
{
    public static readonly AxisOutput Neutral = new(0, 0, 0, true);
}

// Osie pomocnicze, 0…1: hamulec aerodynamiczny (spoilery) i hamulce kół. null = bez zmian, nie wysyłać.
public sealed record AuxOutput(double? Speedbrake, double? Brake);

public enum AxisUpdateResult { Ok, Invalid, Busy }

// Sterowanie osiami z telefonu: przycinanie wychyleń, jeden właściciel naraz
// i auto-neutral, gdy telefon przestanie przysyłać aktualizacje.
public sealed class AxisController
{
    public const double DefaultMaxDeflection = 0.8;
    public const int DefaultTimeoutMs = 500;

    private readonly object _lock = new();
    private double _aileron, _elevator, _rudder;
    private long _lastUpdateMs;
    private bool _pendingNeutral;

    // Osie pomocnicze mają osobnego właściciela: hamulec z telefonu nie przejmuje lotek i steru wysokości.
    private string? _auxOwner;
    private long _auxUpdateMs;
    private double? _speedbrake;
    private bool _speedbrakeDirty;
    private double _brake;
    private bool _brakeHeld, _brakeReleasePending;

    public AxisController(double maxDeflection = DefaultMaxDeflection, int timeoutMs = DefaultTimeoutMs)
    {
        if (maxDeflection <= 0 || maxDeflection > 1) throw new ArgumentOutOfRangeException(nameof(maxDeflection));
        MaxDeflection = maxDeflection;
        TimeoutMs = timeoutMs;
    }

    public double MaxDeflection { get; }
    public int TimeoutMs { get; }
    public string? Owner { get; private set; }
    public bool IsActive => Owner != null;

    public AxisUpdateResult Update(string owner, double? aileron, double? elevator, double? rudder, long nowMs)
    {
        if (!IsValid(aileron) || !IsValid(elevator) || !IsValid(rudder)) return AxisUpdateResult.Invalid;

        lock (_lock)
        {
            ExpireIfStale(nowMs);
            if (Owner != null && Owner != owner) return AxisUpdateResult.Busy;

            Owner = owner;
            _pendingNeutral = false;
            _lastUpdateMs = nowMs;
            if (aileron.HasValue) _aileron = Clamp(aileron.Value);
            if (elevator.HasValue) _elevator = Clamp(elevator.Value);
            if (rudder.HasValue) _rudder = Clamp(rudder.Value);
            return AxisUpdateResult.Ok;
        }
    }

    // Natychmiastowe zwolnienie (wiadomość `axis` z `release`, rozłączenie telefonu).
    public void Release(string owner)
    {
        lock (_lock)
        {
            if (_auxOwner == owner) ReleaseAux();
            if (Owner != owner) return;
            ResetToNeutral();
        }
    }

    // Hamulec aerodynamiczny zostaje tam, gdzie go ustawiono (jak dźwignia); hamulce kół działają,
    // dopóki telefon je przysyła, i puszczają się same po [TimeoutMs] (jak pedały).
    public AxisUpdateResult UpdateAux(string owner, double? speedbrake, double? brake, long nowMs)
    {
        if (!IsValid(speedbrake) || !IsValid(brake)) return AxisUpdateResult.Invalid;
        lock (_lock)
        {
            ExpireAux(nowMs);
            if (_auxOwner != null && _auxOwner != owner) return AxisUpdateResult.Busy;
            _auxOwner = owner;
            _auxUpdateMs = nowMs;
            if (speedbrake.HasValue)
            {
                _speedbrake = Math.Clamp(speedbrake.Value, 0, 1);
                _speedbrakeDirty = true;
            }
            if (brake.HasValue)
            {
                _brake = Math.Clamp(brake.Value, 0, 1);
                _brakeHeld = _brake > 0;
                _brakeReleasePending = !_brakeHeld;
            }
            return AxisUpdateResult.Ok;
        }
    }

    // Wartości osi pomocniczych do wysłania w tym cyklu albo null, gdy nie ma nic nowego.
    public AuxOutput? TickAux(long nowMs)
    {
        lock (_lock)
        {
            ExpireAux(nowMs);
            double? sb = _speedbrakeDirty ? _speedbrake : null;
            _speedbrakeDirty = false;
            double? br = null;
            if (_brakeHeld) br = _brake;
            else if (_brakeReleasePending) { br = 0; _brakeReleasePending = false; }
            return sb == null && br == null ? null : new AuxOutput(sb, br);
        }
    }

    private void ExpireAux(long nowMs)
    {
        if (_auxOwner != null && nowMs - _auxUpdateMs > TimeoutMs) ReleaseAux();
    }

    private void ReleaseAux()
    {
        _auxOwner = null;
        if (_brakeHeld || _brake > 0) _brakeReleasePending = true;
        _brakeHeld = false;
        _brake = 0;
    }

    // Wołane cyklicznie przez źródło danych. Zwraca wychylenia do wysłania,
    // jednorazowe neutralne wyjście po zwolnieniu albo null, gdy osie są wolne.
    public AxisOutput? Tick(long nowMs)
    {
        lock (_lock)
        {
            ExpireIfStale(nowMs);
            if (Owner != null) return new AxisOutput(_aileron, _elevator, _rudder, false);
            if (_pendingNeutral)
            {
                _pendingNeutral = false;
                return AxisOutput.Neutral;
            }
            return null;
        }
    }

    // Bieżące wychylenia bez zdejmowania flagi neutralu (dla symulacji i UI).
    public AxisOutput Peek(long nowMs)
    {
        lock (_lock)
        {
            ExpireIfStale(nowMs);
            return Owner != null ? new AxisOutput(_aileron, _elevator, _rudder, false) : AxisOutput.Neutral;
        }
    }

    private void ExpireIfStale(long nowMs)
    {
        if (Owner != null && nowMs - _lastUpdateMs > TimeoutMs) ResetToNeutral();
    }

    private void ResetToNeutral()
    {
        Owner = null;
        _aileron = _elevator = _rudder = 0;
        _pendingNeutral = true;
    }

    private double Clamp(double v) => Math.Clamp(v, -MaxDeflection, MaxDeflection);

    private static bool IsValid(double? v) => v is null || double.IsFinite(v.Value);
}
