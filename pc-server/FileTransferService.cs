using System.Text;
using System.Text.Json;

namespace PcScreenCast;

internal static class FileTransferService
{
    private const int ChunkSize = 64 * 1024;

    public static string RootDir { get; } = Path.Combine(AppContext.BaseDirectory, "Shared");

    public static void EnsureRootExists()
    {
        Directory.CreateDirectory(RootDir);
    }

    private static string SafeCombine(string relativePath)
    {
        relativePath = (relativePath ?? string.Empty).Replace('\\', '/').TrimStart('/');
        var full = Path.GetFullPath(Path.Combine(RootDir, relativePath));
        var rootFull = Path.GetFullPath(RootDir);
        if (!full.StartsWith(rootFull, StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException("Invalid path");
        return full;
    }

    public static string Handle(JsonElement root)
    {
        var t = root.TryGetProperty("t", out var tp) ? tp.GetString() : null;
        return t switch
        {
            "fs_list" => HandleList(root),
            "fs_mkdir" => HandleMkdir(root),
            "fs_delete" => HandleDelete(root),
            "fs_get" => HandleGet(root),
            "fs_put" => HandlePut(root),
            _ => Protocol.CreateError("unknown", "Unknown fs message")
        };
    }

    private static string HandleList(JsonElement root)
    {
        var path = root.TryGetProperty("path", out var p) ? p.GetString() : "";
        var full = SafeCombine(path ?? "");
        if (!Directory.Exists(full))
            return Protocol.CreateError("fs_list", "Directory not found");

        var dirs = Directory.GetDirectories(full)
            .Select(d => new { name = Path.GetFileName(d), type = "dir", size = (long?)null })
            .ToArray();
        var files = Directory.GetFiles(full)
            .Select(f => new FileInfo(f))
            .Select(fi => new { name = fi.Name, type = "file", size = (long?)fi.Length })
            .ToArray();

        var resp = new
        {
            t = "fs_list_resp",
            path = path ?? "",
            root = "",
            items = dirs.Concat(files).ToArray()
        };
        return JsonSerializer.Serialize(resp);
    }

    private static string HandleMkdir(JsonElement root)
    {
        var path = root.TryGetProperty("path", out var p) ? p.GetString() : null;
        if (string.IsNullOrWhiteSpace(path))
            return Protocol.CreateError("fs_mkdir", "Missing path");
        var full = SafeCombine(path);
        Directory.CreateDirectory(full);
        return "{\"t\":\"fs_mkdir_ok\"}";
    }

    private static string HandleDelete(JsonElement root)
    {
        var path = root.TryGetProperty("path", out var p) ? p.GetString() : null;
        if (string.IsNullOrWhiteSpace(path))
            return Protocol.CreateError("fs_delete", "Missing path");
        var full = SafeCombine(path);
        if (Directory.Exists(full)) Directory.Delete(full, true);
        else if (File.Exists(full)) File.Delete(full);
        return "{\"t\":\"fs_delete_ok\"}";
    }

    private static string HandleGet(JsonElement root)
    {
        var path = root.TryGetProperty("path", out var p) ? p.GetString() : null;
        if (string.IsNullOrWhiteSpace(path))
            return Protocol.CreateError("fs_get", "Missing path");
        var full = SafeCombine(path);
        if (!File.Exists(full))
            return Protocol.CreateError("fs_get", "File not found");

        var bytes = File.ReadAllBytes(full);
        var b64 = Convert.ToBase64String(bytes);
        var resp = new { t = "fs_get_resp", path, data = b64 };
        return JsonSerializer.Serialize(resp);
    }

    private static string HandlePut(JsonElement root)
    {
        var path = root.TryGetProperty("path", out var p) ? p.GetString() : null;
        var data = root.TryGetProperty("data", out var d) ? d.GetString() : null;
        if (string.IsNullOrWhiteSpace(path) || string.IsNullOrEmpty(data))
            return Protocol.CreateError("fs_put", "Missing path or data");

        var full = SafeCombine(path);
        Directory.CreateDirectory(Path.GetDirectoryName(full)!);
        var bytes = Convert.FromBase64String(data);
        File.WriteAllBytes(full, bytes);
        return "{\"t\":\"fs_put_ok\"}";
    }
}
