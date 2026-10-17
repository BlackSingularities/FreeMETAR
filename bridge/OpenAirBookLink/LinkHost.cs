using System.Reflection;
using System.Text.Json;
using OpenAirBookLink.Core.Control;
using OpenAirBookLink.Core.Discovery;
using OpenAirBookLink.Core.Pairing;
using OpenAirBookLink.Core.Protocol;
using OpenAirBookLink.Core.Server;
using OpenAirBookLink.Core.Sources;
using OpenAirBookLink.Msfs;

namespace OpenAirBookLink;

// Składa wszystko razem: serwer, broadcast, źródło danych (MSFS albo symulacja).
public sealed class LinkHost : IDisposable
{
    public static readonly string DataDir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OpenAirBook Link");

    private static readonly string SettingsPath = Path.Combine(DataDir, "settings.json");

    private readonly DiscoveryBroadcaster _discovery;
    private IFlightSource _source;

    public LinkHost(bool? simulationOverride)
    {
        Version = Assembly.GetExecutingAssembly().GetName().Version?.ToString(3) ?? "1.0.0";
        Pairing = new PairingManager(Path.Combine(DataDir, "tokens.json"));
        Axes = new AxisController();
        Server = new LinkServer(Pairing, Axes, Version);
        Server.Log += m => Log?.Invoke(m);

        _discovery = new DiscoveryBroadcaster(() => new AnnounceMessage
        {
            Version = Version,
            Host = Environment.MachineName,
            Port = Server.Port,
            Ips = LocalAddresses(),
            Source = Server.SourceName,
        });
        _discovery.Log += m => Log?.Invoke(m);

        SimulationMode = simulationOverride ?? LoadSettings().SimulationMode;
        _source = CreateSource(SimulationMode);
    }

    public string Version { get; }
    public PairingManager Pairing { get; }
    public AxisController Axes { get; }
    public LinkServer Server { get; }
    public bool SimulationMode { get; private set; }
    public IFlightSource Source => _source;

    public event Action<string>? Log;
    public event Action? SourceStatusChanged;

    public void Start()
    {
        Server.Start();
        _discovery.Start();
        _source.Start();
        Server.Source = _source;
        Log?.Invoke($"Rozgłaszanie UDP na porcie {LinkProtocol.DiscoveryPort} co 2 s");
    }

    public void SetSimulationMode(bool enabled)
    {
        if (enabled == SimulationMode) return;
        SimulationMode = enabled;
        SaveSettings(new Settings { SimulationMode = enabled });

        var old = _source;
        old.StatusChanged -= OnSourceStatusChanged;
        old.Dispose();
        _source = CreateSource(enabled);
        _source.Start();
        Server.Source = _source;
        Log?.Invoke(enabled ? "Tryb symulacji włączony: lot EPWA → EPGD" : "Tryb symulacji wyłączony — łączę z MSFS");
        SourceStatusChanged?.Invoke();
    }

    public static IReadOnlyList<string> LocalAddresses() =>
        DiscoveryBroadcaster.LocalIPv4().Select(a => a.Address.ToString()).Distinct().ToList();

    private IFlightSource CreateSource(bool simulation)
    {
        IFlightSource source;
        if (simulation)
        {
            source = new SimulationSource(Axes);
        }
        else
        {
            var msfs = new MsfsSource(Axes);
            msfs.Log += m => Log?.Invoke(m);
            source = msfs;
        }
        source.StatusChanged += OnSourceStatusChanged;
        return source;
    }

    private void OnSourceStatusChanged()
    {
        Server.BroadcastStatus();
        SourceStatusChanged?.Invoke();
    }

    private sealed class Settings
    {
        public bool SimulationMode { get; set; }
    }

    private static Settings LoadSettings()
    {
        try
        {
            if (File.Exists(SettingsPath))
                return JsonSerializer.Deserialize<Settings>(File.ReadAllText(SettingsPath)) ?? new Settings();
        }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException)
        {
        }
        return new Settings();
    }

    private static void SaveSettings(Settings s)
    {
        try
        {
            Directory.CreateDirectory(DataDir);
            File.WriteAllText(SettingsPath, JsonSerializer.Serialize(s));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }

    public void Dispose()
    {
        _discovery.Dispose();
        _source.Dispose();
        Server.Dispose();
    }
}
