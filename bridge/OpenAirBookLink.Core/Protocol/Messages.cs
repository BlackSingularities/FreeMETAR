using System.Text.Json;
using System.Text.Json.Serialization;

namespace OpenAirBookLink.Core.Protocol;

public static class LinkProtocol
{
    public const int Version = 1;
    public const int WebSocketPort = 47810;
    public const int DiscoveryPort = 47811;
    public const int MaxClientMessageBytes = 16 * 1024;
    public const int DefaultRateHz = 10;
    public const int MinRateHz = 1;
    public const int MaxRateHz = 20;
    public const int MaxNavdataPerSecond = 10;
    public static readonly IReadOnlyList<string> Features = new[] { "navdata", "touchdown", "aircraft_category" };
}

public static class ErrorCodes
{
    public const string BadJson = "bad_json";
    public const string UnsupportedVersion = "unsupported_version";
    public const string UnknownType = "unknown_type";
    public const string NotPaired = "not_paired";
    public const string PairingFailed = "pairing_failed";
    public const string RateLimited = "rate_limited";
    public const string NotAllowed = "not_allowed";
    public const string InvalidValue = "invalid_value";
    public const string SimUnavailable = "sim_unavailable";
    public const string SimError = "sim_error";
    public const string NotFound = "not_found";
}

public static class LinkJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        NumberHandling = JsonNumberHandling.AllowNamedFloatingPointLiterals,
    };

    public static string Serialize<T>(T message) where T : LinkMessage =>
        JsonSerializer.Serialize(message, Options);
}

// Wspólne pola każdej wiadomości.
public abstract class LinkMessage
{
    [JsonPropertyOrder(-2)] public int V { get; init; } = LinkProtocol.Version;
    [JsonPropertyOrder(-1)] public abstract string Type { get; }
}

public sealed class StateMessage : LinkMessage
{
    public override string Type => "state";
    public long Seq { get; init; }
    public long T { get; init; }
    public required FlightState Data { get; init; }
}

public sealed class HelloOkMessage : LinkMessage
{
    public override string Type => "hello_ok";
    public string? Id { get; init; }
    public required string Token { get; init; }
    public string Server { get; init; } = "OpenAirBook Link";
    public required string Version { get; init; }
    public required string Source { get; init; }
    public int RateHz { get; init; }
    public required LimitsInfo Limits { get; init; }
    // Funkcje dodane po pierwszym wydaniu protokołu v1 — telefon może sprawdzić, czy Link je obsługuje.
    public IReadOnlyList<string> Features { get; init; } = LinkProtocol.Features;
}

public sealed class LimitsInfo
{
    public double MaxDeflection { get; init; }
    public int AxisTimeoutMs { get; init; }
    public int NavdataPerSecond { get; init; } = LinkProtocol.MaxNavdataPerSecond;
}

public sealed class StatusMessage : LinkMessage
{
    public override string Type => "status";
    public required string Source { get; init; }
    public bool SimConnected { get; init; }
    public string? Message { get; init; }
}

public sealed class AckMessage : LinkMessage
{
    public override string Type => "ack";
    public string? Id { get; init; }
    public required string Name { get; init; }
}

public sealed class ErrorMessage : LinkMessage
{
    public override string Type => "error";
    [JsonIgnore(Condition = JsonIgnoreCondition.Never)] public string? Id { get; init; }
    public required string Code { get; init; }
    public required string Message { get; init; }
}

public sealed class PongMessage : LinkMessage
{
    public override string Type => "pong";
    public string? Id { get; init; }
    public long? T { get; init; }
    public long ServerT { get; init; }
}

public sealed class AnnounceMessage : LinkMessage
{
    public override string Type => "announce";
    public string Name { get; init; } = "OpenAirBook Link";
    public required string Version { get; init; }
    public required string Host { get; init; }
    public int Port { get; init; }
    public required IReadOnlyList<string> Ips { get; init; }
    public required string Source { get; init; }
}

// Wiadomość od telefonu po wstępnym parsowaniu.
public sealed class IncomingMessage
{
    public required string Type { get; init; }
    public string? Id { get; init; }
    public required JsonElement Root { get; init; }

    public string? GetString(string name) =>
        Root.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.String ? p.GetString() : null;

    public bool GetBool(string name) =>
        Root.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.True;

    public int? GetInt(string name) =>
        Root.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.Number && p.TryGetInt32(out var i) ? i : null;

    public bool Has(string name) => Root.TryGetProperty(name, out var p) && p.ValueKind != JsonValueKind.Null;

    // Sparsuj tekst ramki. Zwraca null i ustawia błąd, gdy wiadomość jest niepoprawna.
    public static IncomingMessage? Parse(string text, out ErrorMessage? error)
    {
        error = null;
        JsonElement root;
        try
        {
            using var doc = JsonDocument.Parse(text);
            root = doc.RootElement.Clone();
        }
        catch (JsonException)
        {
            error = new ErrorMessage { Code = ErrorCodes.BadJson, Message = "Niepoprawny JSON" };
            return null;
        }

        if (root.ValueKind != JsonValueKind.Object)
        {
            error = new ErrorMessage { Code = ErrorCodes.BadJson, Message = "Oczekiwano obiektu JSON" };
            return null;
        }

        string? id = null;
        if (root.TryGetProperty("id", out var idEl))
        {
            id = idEl.ValueKind switch
            {
                JsonValueKind.String => idEl.GetString(),
                JsonValueKind.Number => idEl.GetRawText(),
                _ => null,
            };
        }

        if (!root.TryGetProperty("v", out var vEl) || vEl.ValueKind != JsonValueKind.Number
            || !vEl.TryGetInt32(out var v) || v != LinkProtocol.Version)
        {
            error = new ErrorMessage { Id = id, Code = ErrorCodes.UnsupportedVersion, Message = $"Obsługiwana wersja protokołu: {LinkProtocol.Version}" };
            return null;
        }

        if (!root.TryGetProperty("type", out var typeEl) || typeEl.ValueKind != JsonValueKind.String
            || string.IsNullOrEmpty(typeEl.GetString()))
        {
            error = new ErrorMessage { Id = id, Code = ErrorCodes.BadJson, Message = "Brak pola type" };
            return null;
        }

        return new IncomingMessage { Type = typeEl.GetString()!, Id = id, Root = root };
    }
}
