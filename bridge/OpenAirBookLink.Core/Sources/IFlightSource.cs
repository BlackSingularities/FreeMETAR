using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Protocol;

namespace OpenAirBookLink.Core.Sources;

public sealed record CommandResult(bool Ok, string? ErrorCode = null, string? ErrorMessage = null)
{
    public static readonly CommandResult Success = new(true);
    public static CommandResult Fail(string code, string message) => new(false, code, message);
}

// Źródło stanu samolotu i odbiorca komend: MSFS (SimConnect) albo symulacja.
public interface IFlightSource : IDisposable
{
    // "msfs" albo "simulation"
    string Kind { get; }

    // Czy są aktualne dane (dla MSFS: połączono i przyszła pierwsza paczka).
    bool IsConnected { get; }

    string StatusText { get; }

    event Action? StatusChanged;

    // Ostatnia migawka (kopia, bezpieczna do serializacji w innym wątku) albo null.
    FlightState? GetSnapshot();

    Task<CommandResult> ExecuteAsync(ValidatedCommand command, CancellationToken ct);

    // Wspólny kontroler osi — źródło samo okresowo woła Tick() i wysyła wynik.
    AxisController Axes { get; }

    void Start();
}
