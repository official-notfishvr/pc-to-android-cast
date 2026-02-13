using Fleck;

namespace PcScreenCast;

internal static class Program
{
    private static void Main(string[] args)
    {
        var options = ServerOptions.Parse(args);
        var localIps = NetworkHelper.GetAllLocalIpAddresses();
        NetworkHelper.TryAddFirewallRule(options.Port);

        ServerUI.SetTitle("PC Screen Cast — Server");

        var server = new WebSocketServer($"ws://0.0.0.0:{options.Port}");
        server.Start(socket =>
        {
            socket.OnOpen = () =>
            {
                var ip = socket.ConnectionInfo.ClientIpAddress;
                ServerUI.LogConnected(ip);

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
                ServerUI.LogDisconnected(socket.ConnectionInfo.ClientIpAddress);
            };
            socket.OnError = ex =>
            {
                ServerUI.LogWebSocketError(ex.Message);
            };
        });

        ServerUI.PrintBanner(options, localIps);

        System.Console.ReadLine();
        server.Dispose();
    }
}
