using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.NetworkInformation;
using System.Text;
using System.Windows.Forms;

var port = 9090;
var quality = 50;  // JPEG quality 1-100
var fps = 15;      // Frames per second
var scale = 1.0;   // Scale factor 0.25-1.0 (smaller = less bandwidth)

// Parse args: --port 9090 --quality 50 --fps 15 --scale 0.5
for (int i = 0; i < args.Length - 1; i++)
{
    if (args[i] == "--port" && int.TryParse(args[i + 1], out var p)) port = p;
    if (args[i] == "--quality" && int.TryParse(args[i + 1], out var q)) quality = Math.Clamp(q, 1, 100);
    if (args[i] == "--fps" && int.TryParse(args[i + 1], out var f)) fps = Math.Clamp(f, 1, 60);
    if (args[i] == "--scale" && double.TryParse(args[i + 1].Replace(",", "."), out var s)) scale = Math.Clamp(s, 0.25, 1.0);
}

var encoder = GetJpegEncoder();
var interval = TimeSpan.FromMilliseconds(1000.0 / fps);
var listener = new HttpListener();
listener.Prefixes.Add($"http://+:{port}/");
listener.Start();
Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Server started on port {port}");

var localIps = GetAllLocalIpAddresses();
TryAddFirewallRule(port);
Console.WriteLine();
Console.WriteLine("  PC Screen Cast Server");
Console.WriteLine("  =====================");
Console.WriteLine($"  Stream URL: http://<IP>:{port}/stream");
Console.WriteLine($"  Quality: {quality}% | FPS: {fps} | Scale: {scale:P0}");
Console.WriteLine();
Console.WriteLine("  Use one of these IPs (phone must be on same network):");
foreach (var ip in localIps)
{
    var note = ip.StartsWith("192.168.137.") ? " <- hotspot" : "";
    Console.WriteLine($"    http://{ip}:{port}{note}");
}
Console.WriteLine();
Console.WriteLine("  On your Android phone, enter the IP and port, then Connect.");
Console.WriteLine("  Press Ctrl+C to stop.");
Console.WriteLine();

_ = Task.Run(async () =>
{
    while (listener.IsListening)
    {
        try
        {
            var context = await listener.GetContextAsync();
            var clientIp = context.Request.RemoteEndPoint?.Address?.ToString() ?? "?";
            var path = context.Request.Url?.AbsolutePath ?? "/";
            Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Request: {path} from {clientIp}");
            if (path == "/stream")
            {
                Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Client {clientIp} connected, starting stream...");
                context.Response.ContentType = "multipart/x-mixed-replace; boundary=frame";
                context.Response.AddHeader("Cache-Control", "no-cache");
                context.Response.SendChunked = true;
                await StreamFrames(context.Response.OutputStream, clientIp);
                Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Client {clientIp} disconnected");
            }
            else
            {
                Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] 404: {path}");
                context.Response.StatusCode = 404;
                context.Response.Close();
            }
        }
        catch (HttpListenerException) { break; }
        catch (Exception ex) { Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Error: {ex}"); }
    }
});

Console.ReadLine();
listener.Stop();

async Task StreamFrames(Stream output, string clientIp)
{
    var bounds = SystemInformation.VirtualScreen;
    var captureWidth = (int)(bounds.Width * scale);
    var captureHeight = (int)(bounds.Height * scale);
    Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Capture: {captureWidth}x{captureHeight}");
    using var bitmap = new Bitmap(captureWidth, captureHeight, PixelFormat.Format24bppRgb);
    using var g = Graphics.FromImage(bitmap);

    var encoderParams = new EncoderParameters(1)
    {
        Param = { [0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, (long)quality) }
    };

    var frameCount = 0;
    while (true)
    {
        try
        {
            g.CopyFromScreen(bounds.X, bounds.Y, 0, 0, new Size(bounds.Width, bounds.Height), CopyPixelOperation.SourceCopy);

            using var ms = new MemoryStream();
            bitmap.Save(ms, encoder, encoderParams);
            var bytes = ms.ToArray();
            frameCount++;
            if (frameCount == 1) Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] First frame sent: {bytes.Length} bytes");
            else if (frameCount % 30 == 0) Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Frame {frameCount}");

            var header = $"--frame\r\nContent-Type: image/jpeg\r\nContent-Length: {bytes.Length}\r\n\r\n";
            await output.WriteAsync(Encoding.ASCII.GetBytes(header));
            await output.WriteAsync(bytes);
            await output.FlushAsync();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Stream error for {clientIp}: {ex.Message}");
            break;
        }
        await Task.Delay(interval);
    }
}

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
    catch { /* ignore - firewall rule requires admin */ }
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
