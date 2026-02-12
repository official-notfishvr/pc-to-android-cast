namespace PcScreenCast;

/// <summary>
/// Per-connection stream settings. Can be updated by the client via "config" message.
/// </summary>
internal sealed class StreamConfig
{
    private readonly object _lock = new();
    private double _scale;
    private int _quality;
    private int _fps;
    private int? _qualityZoomed;
    private int? _fpsZoomed;

    public StreamConfig(ServerOptions options)
    {
        _scale = options.Scale;
        _quality = options.Quality;
        _fps = options.Fps;
        _qualityZoomed = null;
        _fpsZoomed = null;
    }

    public double Scale { get { lock (_lock) return _scale; } set { lock (_lock) _scale = value; } }
    public int Quality { get { lock (_lock) return _quality; } set { lock (_lock) _quality = value; } }
    public int Fps { get { lock (_lock) return _fps; } set { lock (_lock) _fps = value; } }
    public int? QualityZoomed { get { lock (_lock) return _qualityZoomed; } set { lock (_lock) _qualityZoomed = value; } }
    public int? FpsZoomed { get { lock (_lock) return _fpsZoomed; } set { lock (_lock) _fpsZoomed = value; } }

    public void GetForZoomed(bool isZoomed, out int quality, out int fps)
    {
        lock (_lock)
        {
            quality = isZoomed && _qualityZoomed.HasValue ? _qualityZoomed.Value : _quality;
            fps = isZoomed && _fpsZoomed.HasValue ? _fpsZoomed.Value : _fps;
        }
    }
}
