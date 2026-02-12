namespace PcScreenCast;

internal sealed class ServerOptions
{
    public int Port { get; set; } = 9090;
    public int Quality { get; set; } = 75;
    public int Fps { get; set; } = 20;
    public double Scale { get; set; } = 1.0;

    public TimeSpan FrameInterval => TimeSpan.FromMilliseconds(1000.0 / Fps);

    public static ServerOptions Parse(string[] args)
    {
        var opts = new ServerOptions();
        for (var i = 0; i < args.Length - 1; i++)
        {
            if (args[i] == "--port" && int.TryParse(args[i + 1], out var p)) opts.Port = p;
            if (args[i] == "--quality" && int.TryParse(args[i + 1], out var q)) opts.Quality = Math.Clamp(q, 1, 100);
            if (args[i] == "--fps" && int.TryParse(args[i + 1], out var f)) opts.Fps = Math.Clamp(f, 1, 60);
            if (args[i] == "--scale" && double.TryParse(args[i + 1].Replace(",", "."), out var s)) opts.Scale = Math.Clamp(s, 0.25, 1.0);
        }
        return opts;
    }
}
