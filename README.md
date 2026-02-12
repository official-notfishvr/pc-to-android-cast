# PC to Android Screen Cast

Cast your Windows PC screen to an Android phone over your local network using **WebSocket**. Built with **C#** (PC server) and **Kotlin** (Android client).

## Quick Start

### 1. Run the PC Server

```powershell
cd pc-server
dotnet run
```

Use one of the shown IPs and port (default 9090). Example: `ws://10.0.0.10:9090`

### 2. Android App

1. Open `android-app` in Android Studio
2. Run on your phone
3. Enter PC IP and port (e.g. `10.0.0.10` and `9090`)
4. Tap **Connect**

## Options

```powershell
dotnet run -- --port 9090 --quality 75 --fps 20 --scale 0.75
```

| Option | Default | Description |
|--------|---------|-------------|
| `--port` | 9090 | WebSocket port |
| `--quality` | 75 | JPEG quality (1–100) |
| `--fps` | 20 | Frames per second |
| `--scale` | 0.75 | Resolution scale (0.25–1.0) |

## Hotspot Mode

1. Turn on Windows Mobile Hotspot
2. Connect your phone to it
3. Use IP `192.168.137.1` and port `9090`

## Firewall

Run the server once as **Administrator** to add the firewall rule, or allow inbound TCP on port 9090 manually.
