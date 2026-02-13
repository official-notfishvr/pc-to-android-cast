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

            if (t == "dc")
            {
                Native.User32.GetCursorPos(out var pt);
                Native.User32.mouse_event(0x0002, pt.X, pt.Y, 0, 0);
                Native.User32.mouse_event(0x0004, pt.X, pt.Y, 0, 0);
                Native.User32.mouse_event(0x0002, pt.X, pt.Y, 0, 0);
                Native.User32.mouse_event(0x0004, pt.X, pt.Y, 0, 0);
                return;
            }

            if (t == "cm")
            {
                Native.User32.GetCursorPos(out var pt);
                Native.User32.mouse_event(0x0020, pt.X, pt.Y, 0, 0);
                Native.User32.mouse_event(0x0040, pt.X, pt.Y, 0, 0);
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
                var (down, up) = GetMouseButtonFlags(b);
                Native.User32.mouse_event(down, 0, 0, 0, 0);
                Native.User32.mouse_event(up, 0, 0, 0, 0);
            }
            else if (t == "k")
            {
                var keyCode = root.TryGetProperty("k", out var kp) ? kp.GetInt32() : 0;
                var keyDown = root.TryGetProperty("d", out var dp) ? dp.GetInt32() : 1;
                var ctrl = root.TryGetProperty("ctrl", out var ctrlEl) && ctrlEl.GetBoolean();
                var alt = root.TryGetProperty("alt", out var altEl) && altEl.GetBoolean();
                var win = root.TryGetProperty("win", out var winEl) && winEl.GetBoolean();
                var shift = root.TryGetProperty("shift", out var shiftEl) && shiftEl.GetBoolean();
                var vk = (byte)(keyCode & 0xFF);
                const byte VK_CONTROL = 0x11, VK_MENU = 0x12, VK_LWIN = 0x5B, VK_SHIFT = 0x10;
                var isModifier = vk == VK_CONTROL || vk == VK_MENU || vk == VK_LWIN || vk == VK_SHIFT;
                if (!isModifier && keyDown != 0 && (ctrl || alt || win || shift))
                {
                    if (ctrl) Native.User32.keybd_event(VK_CONTROL, 0, 0, 0);
                    if (shift) Native.User32.keybd_event(VK_SHIFT, 0, 0, 0);
                    if (alt) Native.User32.keybd_event(VK_MENU, 0, 0, 0);
                    if (win) Native.User32.keybd_event(VK_LWIN, 0, 0, 0);
                    Native.User32.keybd_event(vk, 0, 0, 0);
                    Native.User32.keybd_event(vk, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                    if (win) Native.User32.keybd_event(VK_LWIN, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                    if (alt) Native.User32.keybd_event(VK_MENU, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                    if (shift) Native.User32.keybd_event(VK_SHIFT, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                    if (ctrl) Native.User32.keybd_event(VK_CONTROL, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                }
                else
                {
                    if (keyDown != 0)
                        Native.User32.keybd_event(vk, 0, 0, 0);
                    else
                        Native.User32.keybd_event(vk, 0, Native.User32.KEYEVENTF_KEYUP, 0);
                }
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

    private static (int down, int up) GetMouseButtonFlags(int button)
    {
        return button switch
        {
            1 => (0x0008, 0x0010),  // right
            2 => (0x0020, 0x0040),  // middle
            _ => (0x0002, 0x0004)   // left
        };
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
