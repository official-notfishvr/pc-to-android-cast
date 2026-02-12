using Fleck;

namespace PcScreenCast;

internal static class Program
{
    private static void Main(string[] args)
    {
        ConsoleHelper.TryEnableAnsi();

        var options = ServerOptions.Parse(args);
        var localIps = NetworkHelper.GetAllLocalIpAddresses();
        NetworkHelper.TryAddFirewallRule(options.Port);

        var server = new WebSocketServer($"ws://0.0.0.0:{options.Port}");
        server.Start(socket =>
        {
            socket.OnOpen = () =>
            {
                var ip = socket.ConnectionInfo.ClientIpAddress;
                ConsoleHelper.WriteColor($"[{DateTime.Now:HH:mm:ss}] ", 8);
                ConsoleHelper.WriteColor("Client connected: ", 7);
                ConsoleHelper.WriteColor($"{ip}\n", 10);

                var bounds = System.Windows.Forms.SystemInformation.VirtualScreen;
                var viewport = new ViewportState();
                var config = new StreamConfig(options);
                var streamStarted = false;
                var streamLock = new object();
                void MaybeStartStream()
                {
                    lock (streamLock)
                    {
                        if (streamStarted) return;
                        streamStarted = true;
                    }
                    _ = ScreenStreamService.StreamFramesAsync(socket, ip, viewport, config, options);
                }
                socket.OnMessage = msg =>
                {
                    var cw = Math.Clamp((int)(bounds.Width * config.Scale), 1, bounds.Width);
                    var ch = Math.Clamp((int)(bounds.Height * config.Scale), 1, bounds.Height);
                    ControlHandler.Handle(msg, bounds.Width, bounds.Height, cw, ch, viewport, config);
                    MaybeStartStream();
                };
                _ = Task.Run(async () =>
                {
                    await Task.Delay(400);
                    MaybeStartStream();
                });
            };
            socket.OnClose = () =>
            {
                ConsoleHelper.WriteColor($"[{DateTime.Now:HH:mm:ss}] ", 8);
                ConsoleHelper.WriteColor("Client disconnected: ", 7);
                ConsoleHelper.WriteColor($"{socket.ConnectionInfo.ClientIpAddress}\n", 12);
            };
            socket.OnError = ex =>
            {
                ConsoleHelper.WriteColor($"[{DateTime.Now:HH:mm:ss}] ", 8);
                ConsoleHelper.WriteColor("WebSocket error: ", 12);
                ConsoleHelper.WriteColor($"{ex.Message}\n", 12);
            };
        });

        PrintBanner(options, localIps);

        Console.ReadLine();
        server.Dispose();
    }

    private static void PrintBanner(ServerOptions options, IReadOnlyList<string> localIps)
    {
        ConsoleHelper.WriteLine();
        ConsoleHelper.WriteColor("  ╔══════════════════════════════════════════╗\n", 14);
        ConsoleHelper.WriteColor("  ║   PC Screen Cast — WebSocket Server      ║\n", 14);
        ConsoleHelper.WriteColor("  ╚══════════════════════════════════════════╝\n", 14);
        ConsoleHelper.WriteLine();
        ConsoleHelper.WriteColor("  Endpoint: ", 7);
        ConsoleHelper.WriteColor($"ws://<IP>:{options.Port}\n", 11);
        ConsoleHelper.WriteColor($"  Quality {options.Quality}%  ·  FPS {options.Fps}  ·  Scale {options.Scale:P0}\n", 8);
        ConsoleHelper.WriteLine();
        ConsoleHelper.WriteColor("  Connect from Android using one of these (same network):\n", 8);
        foreach (var ip in localIps)
        {
            var isHotspot = ip.StartsWith("192.168.137.");
            ConsoleHelper.WriteColor($"    ", 8);
            ConsoleHelper.WriteColor($"ws://{ip}:{options.Port}", isHotspot ? 10 : 11);
            if (isHotspot) ConsoleHelper.WriteColor("  ← use this with PC hotspot\n", 10);
            else ConsoleHelper.WriteLine();
        }
        ConsoleHelper.WriteLine();
        ConsoleHelper.WriteColor("  Tip: On phone, enter the IP and port, then Connect.\n", 8);
        ConsoleHelper.WriteColor("  Timeout? Use 192.168.137.1 when using PC hotspot.\n", 8);
        ConsoleHelper.WriteColor("  Press Enter to stop the server.\n", 8);
        ConsoleHelper.WriteLine();
    }
}
