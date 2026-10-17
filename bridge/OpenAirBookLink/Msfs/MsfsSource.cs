using System.Runtime.InteropServices;
using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Protocol;
using OpenAirBookLink.Core.Sources;
using SimConnect.NET;
using SimConnect.NET.SimVar;

namespace OpenAirBookLink.Msfs;

// Połączenie z MSFS 2024 przez SimConnect (pakiet SimConnect.NET z natywnym SimConnect.dll).
// Samo łączy się ponownie, gdy symulator zostanie zamknięty lub uruchomiony później.
public sealed class MsfsSource : IFlightSource
{
    private const uint PauseEventId = 9001;
    private const uint FirstClientEventId = 10000;
    private const uint UserAircraft = 0;
    private const uint PriorityHighest = 1;
    private const double LbsToKg = 0.45359237;
    private const int AxisRange = 16383;

    // Kierunek osi w zdarzeniach AXIS_*: ujemny aileron = w prawo, ujemny elevator = nos w górę.
    // Do potwierdzenia na żywym MSFS 2024 — zmiana znaku tylko tutaj.
    private const int AileronSign = -1;
    private const int ElevatorSign = -1;
    private const int RudderSign = 1;

    private readonly object _lock = new();
    private readonly Dictionary<string, uint> _eventIds = new(StringComparer.Ordinal);
    private CancellationTokenSource? _cts;
    private SimConnectClient? _client;
    private ISimVarSubscription? _subscription;
    private MsfsData? _last;
    private bool _paused;
    private string _status = "Czekam na MSFS…";
    private bool _connected;
    private string? _lastError;

    public MsfsSource(AxisController axes)
    {
        Axes = axes;
        uint id = FirstClientEventId;
        foreach (var spec in CommandAllowlist.All.Where(s => s.Target == CommandTarget.Event)) _eventIds[spec.Name] = id++;
        foreach (var axis in CommandAllowlist.AxisEvents) _eventIds[axis] = id++;
    }

    public string Kind => "msfs";
    public bool IsConnected => _connected && _last != null;
    public string StatusText => _status;
    public AxisController Axes { get; }

    public event Action? StatusChanged;
    public event Action<string>? Log;

    public void Start()
    {
        if (_cts != null) return;
        _cts = new CancellationTokenSource();
        _ = RunAsync(_cts.Token);
    }

    public FlightState? GetSnapshot()
    {
        MsfsData d;
        bool paused;
        lock (_lock)
        {
            if (_last == null) return null;
            d = _last.Value;
            paused = _paused;
        }
        return Map(d, paused, Axes.IsActive);
    }

    public async Task<CommandResult> ExecuteAsync(ValidatedCommand command, CancellationToken ct)
    {
        var client = _client;
        if (client is not { IsConnected: true }) return CommandResult.Fail(ErrorCodes.SimUnavailable, "Brak połączenia z MSFS");

        if (command.Spec.Target == CommandTarget.TransponderState)
        {
            await client.SimVars.SetAsync("TRANSPONDER STATE:1", "Enum", (double)command.Value, cancellationToken: ct);
            return CommandResult.Success;
        }

        await TransmitAsync(client, command.Spec.Name, command.Data, ct);
        return CommandResult.Success;
    }

    private async Task RunAsync(CancellationToken ct)
    {
        var axisTask = AxisLoopAsync(ct);
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await ConnectAsync(ct);
                while (!ct.IsCancellationRequested && _client is { IsConnected: true })
                    await Task.Delay(1000, ct);
                if (!ct.IsCancellationRequested) Log?.Invoke("MSFS rozłączony");
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                break;
            }
            catch (DllNotFoundException e)
            {
                SetStatus(false, "Brak SimConnect.dll: " + e.Message);
            }
            catch (Exception e)
            {
                // MSFS nie działa albo jeszcze się ładuje — normalny stan, spróbuj za chwilę.
                SetStatus(false, "Czekam na MSFS…");
                if (e.Message != _lastError) Log?.Invoke($"MSFS niedostępny — ponawiam co 5 s ({e.Message})");
                _lastError = e.Message;
            }

            await DisconnectAsync();
            try { await Task.Delay(TimeSpan.FromSeconds(5), ct); }
            catch (OperationCanceledException) { break; }
        }
        await DisconnectAsync();
        try { await axisTask; } catch (OperationCanceledException) { }
    }

    private async Task ConnectAsync(CancellationToken ct)
    {
        var client = new SimConnectClient("OpenAirBook Link");
        _client = client;
        await client.ConnectAsync(cancellationToken: ct);

        foreach (var (name, id) in _eventIds)
        {
            var hr = Native.SimConnect_MapClientEventToSimEvent(client.Handle, id, name);
            if (hr < 0) Log?.Invoke($"Nie zmapowano zdarzenia {name}: 0x{hr:X8}");
        }

        client.SystemEventReceived += (_, e) =>
        {
            if (e.EventId != PauseEventId) return;
            lock (_lock) _paused = e.Data != 0;
        };
        await client.SubscribeToEventAsync("Pause", PauseEventId, ct);

        _subscription = client.SimVars.Subscribe<MsfsData>(SimConnectPeriod.VisualFrame, data =>
        {
            var first = false;
            lock (_lock)
            {
                first = _last == null;
                _last = data;
            }
            if (first) StatusChanged?.Invoke();
        }, cancellationToken: ct);

        var sim = client.IsMSFS2024 ? "MSFS 2024" : "MSFS";
        _lastError = null;
        SetStatus(true, $"Połączono z {sim}");
        Log?.Invoke($"Połączono z {sim} przez SimConnect");
    }

    private async Task DisconnectAsync()
    {
        var client = _client;
        _client = null;
        _subscription?.Dispose();
        _subscription = null;
        lock (_lock) _last = null;
        if (_connected) SetStatus(false, "Czekam na MSFS…");
        if (client == null) return;
        try
        {
            await client.DisconnectAsync();
        }
        catch (Exception)
        {
        }
        client.Dispose();
    }

    // Co 50 ms: wychylenia z telefonu (lub jednorazowy neutral po auto-neutralu) do MSFS.
    private async Task AxisLoopAsync(CancellationToken ct)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(50));
        while (await timer.WaitForNextTickAsync(ct))
        {
            var output = Axes.Tick(Environment.TickCount64);
            var client = _client;
            if (output == null || client is not { IsConnected: true }) continue;
            try
            {
                await TransmitAsync(client, "AXIS_AILERONS_SET", ToAxis(output.Aileron * AileronSign), ct);
                await TransmitAsync(client, "AXIS_ELEVATOR_SET", ToAxis(output.Elevator * ElevatorSign), ct);
                await TransmitAsync(client, "AXIS_RUDDER_SET", ToAxis(output.Rudder * RudderSign), ct);
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                Log?.Invoke($"Osie: {e.Message}");
            }
        }
    }

    private static uint ToAxis(double v) => unchecked((uint)(int)Math.Round(Math.Clamp(v, -1, 1) * AxisRange));

    // Biblioteka serializuje wywołania na uchwycie SimConnect i rzuca wyjątek przy błędzie HRESULT.
    private Task TransmitAsync(SimConnectClient client, string eventName, uint data, CancellationToken ct) =>
        client.InputEvents.TransmitClientEventAsync(UserAircraft, _eventIds[eventName], data, PriorityHighest,
            SimConnectEventOptions.GroupIdIsPriority, ct);

    private void SetStatus(bool connected, string text)
    {
        var changed = connected != _connected || text != _status;
        _connected = connected;
        _status = text;
        if (changed) StatusChanged?.Invoke();
    }

    internal static FlightState Map(MsfsData d, bool paused, bool axesActive)
    {
        var lbsPerGal = d.FuelLbsPerGal > 0 ? d.FuelLbsPerGal : 6.7;
        var engines = new List<EngineData>();
        var count = Math.Clamp((int)d.EngineCount, 0, 4);
        var raw = new[]
        {
            (d.N1_1, d.N2_1, d.Rpm1, d.Egt1, d.Ff1), (d.N1_2, d.N2_2, d.Rpm2, d.Egt2, d.Ff2),
            (d.N1_3, d.N2_3, d.Rpm3, d.Egt3, d.Ff3), (d.N1_4, d.N2_4, d.Rpm4, d.Egt4, d.Ff4),
        };
        for (var i = 0; i < count; i++)
        {
            var (n1, n2, rpm, egt, ff) = raw[i];
            engines.Add(new EngineData
            {
                N1Pct = R(n1, 1), N2Pct = R(n2, 1), Rpm = R(rpm, 0), EgtC = R(egt, 0), FuelFlowKgH = R(ff * LbsToKg, 0),
            });
        }

        DateTime? simTime = null;
        if (d.ZuluYear >= 1900 && d.ZuluDayOfYear >= 1)
        {
            simTime = new DateTime((int)d.ZuluYear, 1, 1, 0, 0, 0, DateTimeKind.Utc)
                .AddDays(d.ZuluDayOfYear - 1).AddSeconds(Math.Floor(d.ZuluTime));
        }

        return new FlightState
        {
            Source = "msfs",
            Aircraft = new AircraftInfo { Title = d.Title ?? "", OnGround = B(d.OnGround), Paused = paused },
            SimTimeUtc = simTime,
            Position = new PositionData { Lat = d.Lat, Lon = d.Lon, AltMslFt = R(d.AltMsl, 0), AltAglFt = R(d.AltAgl, 0), AltIndFt = R(d.AltInd, 0) },
            Speed = new SpeedData { IasKt = R(d.Ias, 1), TasKt = R(d.Tas, 1), GsKt = R(d.Gs, 1), Mach = R(d.Mach, 3), VsFpm = R(d.Vs, 0) },
            // MSFS: pochylenie dodatnie = nos w dół, przechylenie dodatnie = w lewo → odwracamy.
            Attitude = new AttitudeData
            {
                PitchDeg = R(-d.Pitch, 1), BankDeg = R(-d.Bank, 1),
                HdgTrueDeg = R(d.HdgTrue, 1), HdgMagDeg = R(d.HdgMag, 1), TrackTrueDeg = R(d.Track, 1),
            },
            Autopilot = new AutopilotData
            {
                Master = B(d.ApMaster), Fd = B(d.ApFd), Athr = B(d.ApAthrArm) || B(d.ApAthrActive),
                HdgDeg = R(d.ApHdg, 0), AltFt = R(d.ApAlt, 0), VsFpm = R(d.ApVs, 0), SpdKt = R(d.ApSpd, 0),
                Modes = new AutopilotModes
                {
                    Hdg = B(d.ApHdgMode), Nav = B(d.ApNavMode),
                    Apr = B(d.ApAprMode) && !B(d.ApAprIsLoc), Loc = B(d.ApAprMode) && B(d.ApAprIsLoc),
                    Alt = B(d.ApAltMode), Vs = B(d.ApVsMode), Flc = B(d.ApFlcMode),
                },
            },
            Radios = new RadiosData
            {
                Com1 = new RadioPair { ActiveMhz = R(d.Com1Active, 3), StandbyMhz = R(d.Com1Standby, 3) },
                Com2 = new RadioPair { ActiveMhz = R(d.Com2Active, 3), StandbyMhz = R(d.Com2Standby, 3) },
                Nav1 = new RadioPair { ActiveMhz = R(d.Nav1Active, 3), StandbyMhz = R(d.Nav1Standby, 3) },
                Nav2 = new RadioPair { ActiveMhz = R(d.Nav2Active, 3), StandbyMhz = R(d.Nav2Standby, 3) },
                Transponder = new TransponderData
                {
                    Code = CommandAllowlist.DecodeSquawk((uint)d.XpdrCode),
                    Mode = TransponderData.ModeName((int)d.XpdrState),
                    Ident = B(d.XpdrIdent),
                },
            },
            Controls = new ControlsData
            {
                GearDown = B(d.GearHandle), GearPct = R(d.GearPct, 0),
                FlapsIndex = (int)d.FlapsIndex, FlapsPositions = (int)d.FlapsPositions + 1, FlapsPct = R(d.FlapsPct, 0),
                SpoilersPct = R(d.SpoilersPct, 0), SpoilersArmed = B(d.SpoilersArmed),
                ParkingBrake = B(d.ParkingBrake), ElevatorTrimPct = R(d.ElevTrimPct, 1),
            },
            Lights = new LightsData
            {
                Beacon = B(d.LightBeacon), Strobe = B(d.LightStrobe), Nav = B(d.LightNav), Landing = B(d.LightLanding),
                Taxi = B(d.LightTaxi), Logo = B(d.LightLogo), Wing = B(d.LightWing), Panel = B(d.LightPanel), Cabin = B(d.LightCabin),
            },
            Fuel = new FuelData
            {
                TotalKg = R(d.FuelTotalLbs * LbsToKg, 0),
                Tanks = new FuelTanks
                {
                    LeftKg = R(d.FuelLeftGal * lbsPerGal * LbsToKg, 0),
                    CenterKg = R(d.FuelCenterGal * lbsPerGal * LbsToKg, 0),
                    RightKg = R(d.FuelRightGal * lbsPerGal * LbsToKg, 0),
                },
            },
            Engines = engines,
            Environment = new EnvironmentData { WindDirDeg = R(d.WindDir, 0), WindSpdKt = R(d.WindSpd, 0), OatC = R(d.Oat, 1), QnhHpa = R(d.Qnh, 1) },
            Axes = new AxesData { Active = axesActive },
        };
    }

    private static bool B(double v) => v != 0;
    private static double R(double v, int digits) => double.IsFinite(v) ? Math.Round(v, digits) : 0;

    public void Dispose()
    {
        _cts?.Cancel();
        _cts = null;
    }

    private static class Native
    {
        [DllImport("SimConnect.dll")]
        public static extern int SimConnect_MapClientEventToSimEvent(IntPtr hSimConnect, uint eventId,
            [MarshalAs(UnmanagedType.LPStr)] string eventName);
    }
}
