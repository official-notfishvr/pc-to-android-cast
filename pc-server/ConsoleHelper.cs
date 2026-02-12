namespace PcScreenCast;

internal static class ConsoleHelper
{
    public static bool AnsiEnabled { get; private set; }

    public static void TryEnableAnsi()
    {
        if (!OperatingSystem.IsWindows()) return;
        try
        {
            var handle = Native.Kernel32.GetStdHandle(Native.Kernel32.STD_OUTPUT_HANDLE);
            if (handle != IntPtr.Zero && Native.Kernel32.GetConsoleMode(handle, out var mode))
                AnsiEnabled = Native.Kernel32.SetConsoleMode(handle, mode | Native.Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING);
        }
        catch { /* ignore */ }
    }

    public static void WriteColor(string text, int colorCode)
    {
        if (AnsiEnabled)
            Console.Write($"\x1b[38;5;{colorCode}m{text}\x1b[0m");
        else
            Console.Write(text);
    }

    public static void WriteLine()
    {
        Console.WriteLine();
    }
}
