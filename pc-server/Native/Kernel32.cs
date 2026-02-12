using System.Runtime.InteropServices;

namespace PcScreenCast.Native;

internal static class Kernel32
{
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr GetStdHandle(int nStdHandle);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool GetConsoleMode(IntPtr hConsoleHandle, out uint lpMode);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool SetConsoleMode(IntPtr hConsoleHandle, uint dwMode);

    public const int STD_OUTPUT_HANDLE = -11;
    public const uint ENABLE_VIRTUAL_TERMINAL_PROCESSING = 4;
}
