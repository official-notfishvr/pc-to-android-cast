using System.Text.Json;

namespace PcScreenCast;

internal static class Protocol
{
    public const int Version = 1;

    public static string CreateHelloAck(ServerOptions options)
    {
        var obj = new
        {
            t = "hello_ack",
            v = Version,
            requireAuth = options.RequireAuth,
            features = new
            {
                stream = true,
                control = true,
                clipboard = false,
                files = true,
                sensors = false
            }
        };
        return JsonSerializer.Serialize(obj);
    }

    public static string CreateAuthOk() => "{\"t\":\"auth_ok\"}";

    public static string CreateAuthFail() => "{\"t\":\"auth_fail\"}";

    public static string CreateStatus(string message)
    {
        var obj = new { t = "status", s = message };
        return JsonSerializer.Serialize(obj);
    }

    public static string CreateError(string code, string message)
    {
        var obj = new { t = "error", code, message };
        return JsonSerializer.Serialize(obj);
    }
}
