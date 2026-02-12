using System.Runtime.InteropServices;
using System.Text.Json;

namespace PcScreenCast;

internal static class ControlHandler
{
    public static void Handle(string msg, int screenW, int screenH, int captureW, int captureH, ViewportState viewport, StreamConfig? config = null)
    {
        try
        {
            using var doc = JsonDocument.Parse(msg);
            var root = doc.RootElement;
            var t = root.TryGetProperty("t", out var tp) ? tp.GetString() : null;
            var x = root.TryGetProperty("x", out var xp) ? xp.GetInt32() : 0;
            var y = root.TryGetProperty("y", out var yp) ? yp.GetInt32() : 0;

            if (t == "config" && config != null)
            {
                if (root.TryGetProperty("scale", out var scaleEl) && scaleEl.TryGetDouble(out var scale))
                    config.Scale = Math.Clamp(scale, 0.25, 1.0);
                if (root.TryGetProperty("quality", out var qEl) && qEl.TryGetInt32(out var q))
                    config.Quality = Math.Clamp(q, 1, 100);
                if (root.TryGetProperty("fps", out var fpsEl) && fpsEl.TryGetInt32(out var fps))
                    config.Fps = Math.Clamp(fps, 1, 60);
                if (root.TryGetProperty("qualityZoomed", out var qzEl) && qzEl.TryGetInt32(out var qz) && qz > 0)
                    config.QualityZoomed = Math.Clamp(qz, 1, 100);
                else if (root.TryGetProperty("qualityZoomed", out _))
                    config.QualityZoomed = null;
                if (root.TryGetProperty("fpsZoomed", out var fzEl) && fzEl.TryGetInt32(out var fz) && fz > 0)
                    config.FpsZoomed = Math.Clamp(fz, 1, 60);
                else if (root.TryGetProperty("fpsZoomed", out _))
                    config.FpsZoomed = null;
                return;
            }

            if (t == "v")
            {
                var w = root.TryGetProperty("w", out var wp) ? wp.GetInt32() : captureW;
                var h = root.TryGetProperty("h", out var hp) ? hp.GetInt32() : captureH;
                lock (viewport.Lock)
                {
                    viewport.X = x;
                    viewport.Y = y;
                    viewport.W = w;
                    viewport.H = h;
                }
                return;
            }

            int vpx, vpy;
            lock (viewport.Lock)
            {
                vpx = viewport.X;
                vpy = viewport.Y;
            }
            var captureX = vpx + x;
            var captureY = vpy + y;
            var screenX = (int)(captureX * (double)screenW / captureW);
            var screenY = (int)(captureY * (double)screenH / captureH);
            screenX = Math.Clamp(screenX, 0, screenW - 1);
            screenY = Math.Clamp(screenY, 0, screenH - 1);

            if (t == "m")
            {
                Native.User32.SetCursorPos(screenX, screenY);
            }
            else if (t == "c")
            {
                var b = root.TryGetProperty("b", out var bp) ? bp.GetInt32() : 0;
                Native.User32.SetCursorPos(screenX, screenY);
                Native.User32.mouse_event(b == 1 ? 0x0008 : 0x0002, 0, 0, 0, 0);
                Native.User32.mouse_event(b == 1 ? 0x0010 : 0x0004, 0, 0, 0, 0);
            }
            else if (t == "k")
            {
                var keyCode = root.TryGetProperty("k", out var kp) ? kp.GetInt32() : 0;
                var keyDown = root.TryGetProperty("d", out var dp) ? dp.GetInt32() : 1;
                var ctrl = root.TryGetProperty("ctrl", out var cp) && cp.GetBoolean();
                var alt = root.TryGetProperty("alt", out var ap) && ap.GetBoolean();
                var win = root.TryGetProperty("win", out var wp) && wp.GetBoolean();
                if (ctrl) Native.User32.keybd_event(0x11, 0, 0, 0);
                if (alt) Native.User32.keybd_event(0x12, 0, 0, 0);
                if (win) Native.User32.keybd_event(0x5B, 0, 0, 0);
                if (keyDown != 0)
                {
                    Native.User32.keybd_event((byte)(keyCode & 0xFF), 0, 0, 0);
                    Native.User32.keybd_event((byte)(keyCode & 0xFF), 0, Native.User32.KEYEVENTF_KEYUP, 0);
                }
                else
                    Native.User32.keybd_event((byte)(keyCode & 0xFF), 0, Native.User32.KEYEVENTF_KEYUP, 0);
                if (win) Native.User32.keybd_event(0x5B, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                if (alt) Native.User32.keybd_event(0x12, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                if (ctrl) Native.User32.keybd_event(0x11, 0, Native.User32.KEYEVENTF_KEYUP, 0);
            }
            else if (t == "u")
            {
                var text = root.TryGetProperty("s", out var sp) ? sp.GetString() : null;
                if (!string.IsNullOrEmpty(text))
                    SendUnicodeText(text);
            }
            else if (t == "s")
            {
                var dy = root.TryGetProperty("dy", out var dyp) ? dyp.GetInt32() : 0;
                var dx = root.TryGetProperty("dx", out var dxp) ? dxp.GetInt32() : 0;
                if (dy != 0 || dx != 0)
                    DoScroll(dx, dy);
            }
        }
        catch { /* ignore parse errors */ }
    }

    private static void SendUnicodeText(string text)
    {
        foreach (var c in text)
        {
            var scan = (ushort)c;
            var down = new Native.INPUT { type = 1, ki = new Native.KEYBDINPUT { wVk = 0, wScan = scan, dwFlags = Native.User32.KEYEVENTF_UNICODE } };
            var up = new Native.INPUT { type = 1, ki = new Native.KEYBDINPUT { wVk = 0, wScan = scan, dwFlags = Native.User32.KEYEVENTF_UNICODE | Native.User32.KEYEVENTF_KEYUP } };
            Native.User32.SendInput(2, new[] { down, up }, Marshal.SizeOf<Native.INPUT>());
        }
    }

    private static void DoScroll(int deltaX, int deltaY)
    {
        Native.User32.GetCursorPos(out var pt);
        if (deltaY != 0)
            Native.User32.mouse_event(Native.User32.MOUSEEVENTF_WHEEL, pt.X, pt.Y, deltaY * 120, 0);
        if (deltaX != 0)
            Native.User32.mouse_event(Native.User32.MOUSEEVENTF_HWHEEL, pt.X, pt.Y, deltaX * 120, 0);
    }
}
