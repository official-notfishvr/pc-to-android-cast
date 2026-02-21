namespace PcScreenCast;

internal sealed class AuthState
{
    public readonly object Lock = new();
    public bool IsAuthed;
    public string? DeviceId;
    public string? DeviceName;
}
