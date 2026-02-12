using System.Runtime.InteropServices;

namespace PcScreenCast.Native;

internal static class User32
{
    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(int dwFlags, int dx, int dy, int dwData, int dwExtraInfo);

    [DllImport("user32.dll")]
    public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

    [DllImport("user32.dll")]
    public static extern bool GetCursorPos(out POINT lpPoint);

    [DllImport("user32.dll")]
    public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    public const int MOUSEEVENTF_WHEEL = 0x0800;
    public const int MOUSEEVENTF_HWHEEL = 0x1000;
    public const uint KEYEVENTF_KEYUP = 0x0002u;
    public const uint KEYEVENTF_UNICODE = 0x0004u;
}
