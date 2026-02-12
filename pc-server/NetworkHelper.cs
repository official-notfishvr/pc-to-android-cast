using System.Diagnostics;
using System.Net.NetworkInformation;

namespace PcScreenCast;

internal static class NetworkHelper
{
    public static IReadOnlyList<string> GetAllLocalIpAddresses()
    {
        var list = new List<string>();
        try
        {
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up ||
                    ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                foreach (var ip in ni.GetIPProperties().UnicastAddresses)
                {
                    if (ip.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork &&
                        !System.Net.IPAddress.IsLoopback(ip.Address))
                        list.Add(ip.Address.ToString());
                }
            }
        }
        catch { /* ignore */ }
        return list.Count > 0 ? list : new List<string> { "127.0.0.1" };
    }

    public static void TryAddFirewallRule(int port)
    {
        const string ruleName = "PC Screen Cast";
        try
        {
            RunNetsh($"advfirewall firewall delete rule name=\"{ruleName}\"");
            RunNetsh($"advfirewall firewall add rule name=\"{ruleName}\" dir=in action=allow protocol=tcp localport={port}");
        }
        catch { /* ignore */ }
    }

    private static void RunNetsh(string args)
    {
        using var p = Process.Start(new ProcessStartInfo
        {
            FileName = "netsh",
            Arguments = args,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardError = true,
            RedirectStandardOutput = true
        });
        p?.WaitForExit(3000);
    }
}
