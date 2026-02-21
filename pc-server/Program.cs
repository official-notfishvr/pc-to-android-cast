using Fleck;
using System.Text.Json;

namespace PcScreenCast;

internal static class Program
{
    private static void Main(string[] args)
    {
        FileTransferService.EnsureRootExists();
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
                var auth = new AuthState { IsAuthed = !options.RequireAuth };
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

                socket.Send(Protocol.CreateHelloAck(options));
                if (options.RequireAuth)
                    socket.Send(Protocol.CreateStatus("Pairing required"));

                socket.OnMessage = msg =>
                {
                    string? type = null;
                    try
                    {
                        using var doc = JsonDocument.Parse(msg);
                        if (doc.RootElement.TryGetProperty("t", out var tp))
                            type = tp.GetString();

                        if (type == "hello")
                        {
                            lock (auth.Lock)
                            {
                                auth.DeviceId = doc.RootElement.TryGetProperty("deviceId", out var did) ? did.GetString() : null;
                                auth.DeviceName = doc.RootElement.TryGetProperty("name", out var nm) ? nm.GetString() : null;
                            }
                            socket.Send(Protocol.CreateHelloAck(options));
                            return;
                        }

                        if (type == "auth")
                        {
                            var pin = doc.RootElement.TryGetProperty("pin", out var pinEl) ? pinEl.GetString() : null;
                            var ok = !options.RequireAuth || string.IsNullOrEmpty(options.PairingPin) || pin == options.PairingPin;
                            lock (auth.Lock) auth.IsAuthed = ok;
                            socket.Send(ok ? Protocol.CreateAuthOk() : Protocol.CreateAuthFail());
                            if (ok) MaybeStartStream();
                            return;
                        }
                    }
                    catch
                    {
                        // ignore parse errors
                    }

                    lock (auth.Lock)
                    {
                        if (!auth.IsAuthed)
                        {
                            if (type == "v" || type == "config")
                                return;
                            return;
                        }
                    }

                    if (!string.IsNullOrEmpty(type) && type.StartsWith("fs_", StringComparison.Ordinal))
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(msg);
                            var resp = FileTransferService.Handle(doc.RootElement);
                            socket.Send(resp);
                        }
                        catch (Exception ex)
                        {
                            socket.Send(Protocol.CreateError("fs", ex.Message));
                        }
                        return;
                    }

                    var cw = Math.Clamp((int)(bounds.Width * config.Scale), 1, bounds.Width);
                    var ch = Math.Clamp((int)(bounds.Height * config.Scale), 1, bounds.Height);
                    ControlHandler.Handle(msg, bounds.Width, bounds.Height, cw, ch, viewport, config);
                    MaybeStartStream();
                };
                _ = Task.Run(async () =>
                {
                    await Task.Delay(400);
                    lock (auth.Lock)
                    {
                        if (auth.IsAuthed) MaybeStartStream();
                    }
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
