using System.Drawing;
using System.Drawing.Imaging;
using System.Windows.Forms;
using Fleck;

namespace PcScreenCast;

internal static class ScreenStreamService
{
    public static ImageCodecInfo? GetJpegEncoder()
    {
        return ImageCodecInfo.GetImageEncoders()
            .FirstOrDefault(c => c.MimeType == "image/jpeg");
    }

    public static async Task StreamFramesAsync(
        IWebSocketConnection socket,
        string clientIp,
        ViewportState viewport,
        StreamConfig config,
        ServerOptions options)
    {
        var bounds = SystemInformation.VirtualScreen;
        var encoder = GetJpegEncoder();
        if (encoder == null) return;

        int captureWidth = (int)(bounds.Width * config.Scale);
        int captureHeight = (int)(bounds.Height * config.Scale);
        captureWidth = Math.Clamp(captureWidth, 1, bounds.Width);
        captureHeight = Math.Clamp(captureHeight, 1, bounds.Height);

        ServerUI.LogStreamingStart(clientIp, captureWidth, captureHeight);

        using var bitmap = new Bitmap(captureWidth, captureHeight, PixelFormat.Format24bppRgb);
        using var g = Graphics.FromImage(bitmap);

        var frameCount = 0;
        try
        {
            while (true)
            {
                config.GetForZoomed(false, out var qualityFull, out var fpsFull);
                g.CopyFromScreen(bounds.X, bounds.Y, 0, 0, new Size(bounds.Width, bounds.Height), CopyPixelOperation.SourceCopy);

                int vx, vy, vw, vh;
                lock (viewport.Lock)
                {
                    vx = viewport.X;
                    vy = viewport.Y;
                    vw = viewport.W;
                    vh = viewport.H;
                }

                var threshold = 0.95;
                var isZoomed = vw > 0 && vh > 0 && (vw < captureWidth * threshold || vh < captureHeight * threshold);
                config.GetForZoomed(isZoomed, out var quality, out var fps);

                var encoderParams = new EncoderParameters(1)
                {
                    Param = { [0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, (long)quality) }
                };

                byte[] bytes;
                if (isZoomed)
                {
                    vx = Math.Clamp(vx, 0, captureWidth - 1);
                    vy = Math.Clamp(vy, 0, captureHeight - 1);
                    vw = Math.Clamp(vw, 1, captureWidth - vx);
                    vh = Math.Clamp(vh, 1, captureHeight - vy);
                    var rect = new Rectangle(vx, vy, vw, vh);
                    using var crop = bitmap.Clone(rect, bitmap.PixelFormat);
                    using var ms = new MemoryStream();
                    crop.Save(ms, encoder, encoderParams);
                    bytes = ms.ToArray();
                }
                else
                {
                    using var ms = new MemoryStream();
                    bitmap.Save(ms, encoder, encoderParams);
                    bytes = ms.ToArray();
                }

                frameCount++;
                if (frameCount == 1)
                    ServerUI.LogFirstFrameSent(bytes.Length);
                socket.Send(bytes);
                await Task.Delay(TimeSpan.FromMilliseconds(1000.0 / fps));
            }
        }
        catch (Exception ex)
        {
            ServerUI.LogStreamError(clientIp, ex.Message);
        }
    }
}
