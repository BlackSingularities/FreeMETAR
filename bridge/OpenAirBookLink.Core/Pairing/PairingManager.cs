using System.Security.Cryptography;
using System.Text.Json;

namespace OpenAirBookLink.Core.Pairing;

public sealed record PairingResult(bool Ok, string? Token, bool RateLimited);

// Kod parowania (6 cyfr) i tokeny sparowanych telefonów.
// Tokeny trafiają do pliku, żeby telefon nie musiał parować się po restarcie programu.
public sealed class PairingManager
{
    public const int MaxFailures = 5;
    public static readonly TimeSpan BlockTime = TimeSpan.FromSeconds(60);

    private readonly object _lock = new();
    private readonly HashSet<string> _tokens = new(StringComparer.Ordinal);
    private readonly Dictionary<string, (int Count, DateTime BlockedUntil)> _failures = new();
    private readonly string? _storePath;

    public PairingManager(string? storePath = null)
    {
        _storePath = storePath;
        Load();
        Code = NewCode();
    }

    public string Code { get; private set; }

    public event Action? CodeChanged;

    public int PairedCount
    {
        get { lock (_lock) return _tokens.Count; }
    }

    public void RegenerateCode()
    {
        lock (_lock) Code = NewCode();
        CodeChanged?.Invoke();
    }

    public void RevokeAll()
    {
        lock (_lock)
        {
            _tokens.Clear();
            Save();
        }
    }

    public PairingResult TryPair(string remoteIp, string? code, string? token, DateTime now)
    {
        lock (_lock)
        {
            if (_failures.TryGetValue(remoteIp, out var f) && f.BlockedUntil > now)
                return new PairingResult(false, null, true);

            if (!string.IsNullOrEmpty(token) && _tokens.Contains(token))
            {
                _failures.Remove(remoteIp);
                return new PairingResult(true, token, false);
            }

            if (!string.IsNullOrEmpty(code) && FixedTimeEquals(code, Code))
            {
                _failures.Remove(remoteIp);
                var newToken = NewToken();
                _tokens.Add(newToken);
                Save();
                return new PairingResult(true, newToken, false);
            }

            var count = (f.Count) + 1;
            _failures[remoteIp] = count >= MaxFailures ? (0, now + BlockTime) : (count, DateTime.MinValue);
            return new PairingResult(false, null, false);
        }
    }

    private static string NewCode() => RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

    private static string NewToken() =>
        Convert.ToBase64String(RandomNumberGenerator.GetBytes(24)).Replace('+', '-').Replace('/', '_');

    private static bool FixedTimeEquals(string a, string b) =>
        CryptographicOperations.FixedTimeEquals(System.Text.Encoding.UTF8.GetBytes(a), System.Text.Encoding.UTF8.GetBytes(b));

    private void Load()
    {
        if (_storePath == null || !File.Exists(_storePath)) return;
        try
        {
            var tokens = JsonSerializer.Deserialize<string[]>(File.ReadAllText(_storePath));
            if (tokens != null) foreach (var t in tokens) _tokens.Add(t);
        }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException)
        {
            // Uszkodzony plik = telefony sparują się ponownie.
        }
    }

    private void Save()
    {
        if (_storePath == null) return;
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(_storePath)!);
            File.WriteAllText(_storePath, JsonSerializer.Serialize(_tokens.ToArray()));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }
}
