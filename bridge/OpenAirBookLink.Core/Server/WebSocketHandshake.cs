using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;

namespace OpenAirBookLink.Core.Server;

// Minimalny handshake HTTP → WebSocket (RFC 6455) na gołym TcpListener.
// HttpListener na wszystkich interfejsach wymaga uprawnień administratora (urlacl), stąd własna obsługa.
internal static class WebSocketHandshake
{
    private const string Guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private const int MaxHeaderBytes = 8 * 1024;

    public enum Outcome { Upgraded, PlainHttp, Invalid }

    public static async Task<Outcome> AcceptAsync(NetworkStream stream, CancellationToken ct)
    {
        var buffer = new byte[MaxHeaderBytes];
        var length = 0;
        while (true)
        {
            if (length == buffer.Length) return Outcome.Invalid;
            var read = await stream.ReadAsync(buffer.AsMemory(length), ct).ConfigureAwait(false);
            if (read == 0) return Outcome.Invalid;
            length += read;
            if (IndexOfHeaderEnd(buffer, length) >= 0) break;
        }

        var lines = Encoding.ASCII.GetString(buffer, 0, length).Split("\r\n");
        if (lines.Length == 0 || !lines[0].StartsWith("GET ", StringComparison.Ordinal))
        {
            await WriteAsync(stream, "HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\nContent-Length: 0\r\n\r\n", ct);
            return Outcome.Invalid;
        }

        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var line in lines.Skip(1))
        {
            var colon = line.IndexOf(':');
            if (colon > 0) headers[line[..colon].Trim()] = line[(colon + 1)..].Trim();
        }

        var isUpgrade = headers.TryGetValue("Upgrade", out var upgrade)
                        && upgrade.Equals("websocket", StringComparison.OrdinalIgnoreCase);
        if (!isUpgrade || !headers.TryGetValue("Sec-WebSocket-Key", out var key) || string.IsNullOrWhiteSpace(key))
        {
            // Zwykłe wejście przeglądarką — pozwala sprawdzić z telefonu, czy zapora przepuszcza port.
            const string body = "OpenTakeoff Link dziala. Polacz sie z aplikacji OpenTakeoff.\n";
            await WriteAsync(stream,
                $"HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n{body}", ct);
            return Outcome.PlainHttp;
        }

        var accept = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key + Guid)));
        await WriteAsync(stream,
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            $"Sec-WebSocket-Accept: {accept}\r\n\r\n", ct);
        return Outcome.Upgraded;
    }

    private static int IndexOfHeaderEnd(byte[] b, int length)
    {
        for (var i = 3; i < length; i++)
            if (b[i - 3] == '\r' && b[i - 2] == '\n' && b[i - 1] == '\r' && b[i] == '\n') return i;
        return -1;
    }

    private static Task WriteAsync(NetworkStream stream, string text, CancellationToken ct) =>
        stream.WriteAsync(Encoding.ASCII.GetBytes(text), ct).AsTask();
}
