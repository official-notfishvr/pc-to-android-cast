namespace PcScreenCast;

internal sealed class ViewportState
{
    public readonly object Lock = new();
    public int X, Y, W, H;
}
