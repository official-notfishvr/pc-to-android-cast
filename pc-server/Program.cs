using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;
using Fleck;

var port = 9090;
var quality = 75;
var fps = 20;
var scale = 1.0;

// Parse args: --port 9090 --quality 75 --fps 20 --scale 0.75
for (int i = 0; i < args.Length - 1; i++)
{
    if (args[i] == "--port" && int.TryParse(args[i + 1], out var p)) port = p;
    if (args[i] == "--quality" && int.TryParse(args[i + 1], out var q)) quality = Math.Clamp(q, 1, 100);
    if (args[i] == "--fps" && int.TryParse(args[i + 1], out var f)) fps = Math.Clamp(f, 1, 60);
    if (args[i] == "--scale" && double.TryParse(args[i + 1].Replace(",", "."), out var s)) scale = Math.Clamp(s, 0.25, 1.0);
}

var encoder = GetJpegEncoder();
var interval = TimeSpan.FromMilliseconds(1000.0 / fps);
var localIps = GetAllLocalIpAddresses();
TryAddFirewallRule(port);

var server = new WebSocketServer($"ws://0.0.0.0:{port}");
server.Start(socket =>
{
    socket.OnOpen = () =>
    {
        var ip = socket.ConnectionInfo.ClientIpAddress;
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] WebSocket client connected: {ip}");
        var bounds = SystemInformation.VirtualScreen;
        var cw = (int)(bounds.Width * scale);
        var ch = (int)(bounds.Height * scale);
        socket.OnMessage = msg => HandleControl(msg, bounds.Width, bounds.Height, cw, ch);
        _ = StreamFramesAsync(socket, ip);
    };
    socket.OnClose = () =>
    {
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Client disconnected: {socket.ConnectionInfo.ClientIpAddress}");
    };
    socket.OnError = ex =>
    {
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] WebSocket error: {ex.Message}");
    };
});

Console.WriteLine();
Console.WriteLine("  PC Screen Cast Server (WebSocket)");
Console.WriteLine("  ==================================");
Console.WriteLine($"  ws://<IP>:{port}");
Console.WriteLine($"  Quality: {quality}% | FPS: {fps} | Scale: {scale:P0}");
Console.WriteLine();
Console.WriteLine("  Use one of these IPs (phone must be on same network):");
foreach (var ip in localIps)
{
    var note = ip.StartsWith("192.168.137.") ? " <- hotspot" : "";
    Console.WriteLine($"    ws://{ip}:{port}{note}");
}
Console.WriteLine();
Console.WriteLine("  On your Android phone, enter the IP and port, then Connect.");
Console.WriteLine("  Press Enter to stop.");
Console.WriteLine();

Console.ReadLine();
server.Dispose();

async Task StreamFramesAsync(IWebSocketConnection socket, string clientIp)
{
    var bounds = SystemInformation.VirtualScreen;
    var captureWidth = (int)(bounds.Width * scale);
    var captureHeight = (int)(bounds.Height * scale);
    Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Capture: {captureWidth}x{captureHeight} for {clientIp}");
    using var bitmap = new Bitmap(captureWidth, captureHeight, PixelFormat.Format24bppRgb);
    using var g = Graphics.FromImage(bitmap);

    var encoderParams = new EncoderParameters(1)
    {
        Param = { [0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, (long)quality) }
    };

    var frameCount = 0;
    try
    {
        while (true)
        {
            g.CopyFromScreen(bounds.X, bounds.Y, 0, 0, new Size(bounds.Width, bounds.Height), CopyPixelOperation.SourceCopy);
            using var ms = new MemoryStream();
            bitmap.Save(ms, encoder!, encoderParams);
            var bytes = ms.ToArray();
            frameCount++;
            if (frameCount == 1) Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] First frame sent: {bytes.Length} bytes");
            else if (frameCount % 60 == 0) Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Frame {frameCount}");
            socket.Send(bytes);
            await Task.Delay(interval);
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Stream error for {clientIp}: {ex.Message}");
    }
}

static void HandleControl(string msg, int screenW, int screenH, int captureW, int captureH)
{
    try
    {
        using var doc = JsonDocument.Parse(msg);
        var root = doc.RootElement;
        var t = root.TryGetProperty("t", out var tp) ? tp.GetString() : null;
        var x = root.TryGetProperty("x", out var xp) ? xp.GetInt32() : 0;
        var y = root.TryGetProperty("y", out var yp) ? yp.GetInt32() : 0;
        var screenX = (int)(x * (double)screenW / captureW);
        var screenY = (int)(y * (double)screenH / captureH);
        screenX = Math.Clamp(screenX, 0, screenW - 1);
        screenY = Math.Clamp(screenY, 0, screenH - 1);
        if (t == "m") SetCursorPos(screenX, screenY);
        else if (t == "c")
        {
            var b = root.TryGetProperty("b", out var bp) ? bp.GetInt32() : 0;
            SetCursorPos(screenX, screenY);
            mouse_event(b == 1 ? 0x0008 : 0x0002, 0, 0, 0, 0);
            mouse_event(b == 1 ? 0x0010 : 0x0004, 0, 0, 0, 0);
        }
    }
    catch { /* ignore parse errors */ }
}

[DllImport("user32.dll")]
static extern bool SetCursorPos(int x, int y);

[DllImport("user32.dll")]
static extern void mouse_event(int dwFlags, int dx, int dy, int dwData, int dwExtraInfo);

static ImageCodecInfo? GetJpegEncoder()
{
    return ImageCodecInfo.GetImageEncoders()
        .FirstOrDefault(c => c.MimeType == "image/jpeg");
}

static IReadOnlyList<string> GetAllLocalIpAddresses()
{
    var list = new List<string>();
    try
    {
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up ||
                ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            foreach (var ip in ni.GetIPProperties().UnicastAddresses)
            {
                if (ip.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork &&
                    !System.Net.IPAddress.IsLoopback(ip.Address))
                    list.Add(ip.Address.ToString());
            }
        }
    }
    catch { /* ignore */ }
    return list.Count > 0 ? list : new List<string> { "127.0.0.1" };
}

static void TryAddFirewallRule(int port)
{
    const string ruleName = "PC Screen Cast";
    try
    {
        RunNetsh($"advfirewall firewall delete rule name=\"{ruleName}\"");
        RunNetsh($"advfirewall firewall add rule name=\"{ruleName}\" dir=in action=allow protocol=tcp localport={port}");
    }
    catch { /* ignore */ }
}

static void RunNetsh(string args)
{
    using var p = Process.Start(new ProcessStartInfo
    {
        FileName = "netsh",
        Arguments = args,
        UseShellExecute = false,
        CreateNoWindow = true,
        RedirectStandardError = true,
        RedirectStandardOutput = true
    });
    p?.WaitForExit(3000);
}
