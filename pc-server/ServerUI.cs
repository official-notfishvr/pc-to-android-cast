using System;
using System.Runtime.CompilerServices;
using Fish.Console;

namespace PcScreenCast;

internal static class ServerUI
{
    private static ConsoleColor _savedFg;
    private static ConsoleColor _savedBg;

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    private static void SaveColors()
    {
        _savedFg = FishConsole.ForegroundColor;
        _savedBg = FishConsole.BackgroundColor;
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    private static void RestoreColors()
    {
        FishConsole.ForegroundColor = _savedFg;
        FishConsole.BackgroundColor = _savedBg;
    }

    public static void SetTitle(string title) => FishConsole.SetTitle(title);

    public static void Clear() => FishConsole.Clear();

    public static void WriteDim(string text)
    {
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.DarkGray;
        FishConsole.Write(text);
        RestoreColors();
    }

    public static void WriteAccent(string text)
    {
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.Cyan;
        FishConsole.Write(text);
        RestoreColors();
    }

    public static void WriteSuccess(string text)
    {
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.Green;
        FishConsole.Write(text);
        RestoreColors();
    }

    public static void WriteWarning(string text)
    {
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.Yellow;
        FishConsole.Write(text);
        RestoreColors();
    }

    public static void WriteError(string text)
    {
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.Red;
        FishConsole.Write(text);
        RestoreColors();
    }

    public static void WriteHighlight(string text)
    {
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.White;
        FishConsole.Write(text);
        RestoreColors();
    }

    public static void PrintBanner(ServerOptions options, IReadOnlyList<string> localIps)
    {
        FishConsole.WriteLine();
        // Header box
        SaveColors();
        FishConsole.ForegroundColor = ConsoleColor.DarkCyan;
        FishConsole.WriteLine("  ┌────────────────────────────────────────────┐");
        FishConsole.WriteLine("  │                                            │");
        FishConsole.ForegroundColor = ConsoleColor.Cyan;
        FishConsole.Write("  │   ");
        FishConsole.ForegroundColor = ConsoleColor.White;
        FishConsole.Write("PC Screen Cast");
        FishConsole.ForegroundColor = ConsoleColor.DarkGray;
        FishConsole.Write(" — ");
        FishConsole.ForegroundColor = ConsoleColor.Gray;
        FishConsole.Write("WebSocket Server");
        FishConsole.ForegroundColor = ConsoleColor.DarkCyan;
        FishConsole.WriteLine("   │");
        FishConsole.WriteLine("  │                                            │");
        FishConsole.WriteLine("  └────────────────────────────────────────────┘");
        RestoreColors();

        FishConsole.WriteLine();
        WriteDim("  Endpoint  ");
        FishConsole.Write("ws://<IP>:");
        WriteHighlight(options.Port.ToString());
        FishConsole.WriteLine();
        WriteDim("  Defaults  ");
        FishConsole.Write("Quality ");
        WriteAccent($"{options.Quality}%");
        FishConsole.Write("  ·  FPS ");
        WriteAccent(options.Fps.ToString());
        FishConsole.Write("  ·  Scale ");
        WriteAccent($"{options.Scale:P0}");
        FishConsole.WriteLine();
        FishConsole.WriteLine();

        WriteDim("  Connect from Android (same network):");
        FishConsole.WriteLine();
        foreach (var ip in localIps)
        {
            var isHotspot = ip.StartsWith("192.168.137.");
            FishConsole.Write("    ");
            WriteAccent($"ws://{ip}:{options.Port}");
            if (isHotspot)
            {
                FishConsole.Write("  ");
                WriteDim("← PC hotspot");
            }
            FishConsole.WriteLine();
        }
        FishConsole.WriteLine();

        WriteDim("  ");
        FishConsole.Write("Tip");
        WriteDim(": Enter IP + port on phone, then Connect.");
        FishConsole.WriteLine();
        WriteDim("  ");
        FishConsole.Write("Hotspot");
        WriteDim(": Use 192.168.137.1 if connection times out.");
        FishConsole.WriteLine();
        FishConsole.WriteLine();
        WriteDim("  Press ");
        FishConsole.Write("Enter");
        WriteDim(" to stop the server.");
        FishConsole.WriteLine();
        FishConsole.WriteLine();
    }

    public static void LogConnected(string ip)
    {
        WriteDim($"[{DateTime.Now:HH:mm:ss}] ");
        WriteSuccess("● Connected ");
        WriteHighlight(ip);
        FishConsole.WriteLine();
    }

    public static void LogDisconnected(string ip)
    {
        WriteDim($"[{DateTime.Now:HH:mm:ss}] ");
        WriteDim("○ Disconnected ");
        WriteHighlight(ip);
        FishConsole.WriteLine();
    }

    public static void LogWebSocketError(string message)
    {
        WriteDim($"[{DateTime.Now:HH:mm:ss}] ");
        WriteError("WebSocket error ");
        FishConsole.WriteLine(message);
    }

    public static void LogStreamingStart(string clientIp, int width, int height)
    {
        WriteDim($"[{DateTime.Now:HH:mm:ss}] ");
        WriteAccent("▸ Streaming ");
        FishConsole.Write($"{width}×{height} → ");
        WriteHighlight(clientIp);
        FishConsole.WriteLine();
    }

    public static void LogFirstFrameSent(int bytes)
    {
        WriteDim($"[{DateTime.Now:HH:mm:ss}] ");
        WriteSuccess("✓ First frame ");
        WriteDim($"({bytes:N0} bytes)");
        FishConsole.WriteLine();
    }

    public static void LogStreamError(string clientIp, string message)
    {
        WriteDim($"[{DateTime.Now:HH:mm:ss}] ");
        WriteError("Stream error ");
        WriteHighlight(clientIp);
        FishConsole.Write(": ");
        FishConsole.WriteLine(message);
    }
}
