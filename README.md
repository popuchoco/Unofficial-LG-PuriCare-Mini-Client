# Unofficial LG PuriCare Mini Client

[English](README.md) | [繁體中文](README-zh-tw.md)

An unofficial Android client for the LG PuriCare Mini portable air purifier, supporting Android 12 and later.

The original app is no longer maintained, but existing devices can still be used. This project connects to the device directly over Bluetooth Low Energy (BLE), letting you check air quality, battery and filter status, and control power, fan speed, Turbo, indicator brightness, and sensor behavior—without an LG account or cloud service.

> This project is not affiliated with LG Electronics. LG, PuriCare, and related product names and trademarks belong to their respective owners.

## Device and platform

- **Supported device:** LG PuriCare Mini.
- **Supported platform:** Android 12 (API 31) or later.
- **Connection:** Direct Bluetooth Low Energy (BLE).
- **Account and network:** No LG account is required; core features do not require internet access.

## Project status

This is an installable alpha version that has been exercised with physical devices. Scanning, connection, live readings, device controls, diagnostics export, background connectivity, and light/dark themes are implemented. Behavior involving background restrictions across phone models, proximity-based automation, and device power-off scenarios is still being validated on real hardware.

Current validation focus:

- Background connection lifecycle on Android 12 and Android 14.
- Bounded reconnect backoff and notification state after the device loses power.
- Range and battery impact of automatically switching the device based on Bluetooth connection state.

## Architecture

```text
LG PuriCare Mini
      │ Bluetooth Low Energy
      ▼
Android Client
      ├── Scanning and connection lifecycle
      ├── GATT operation queue and timeout recovery
      ├── Device state parsing and controls
      ├── Foreground service for background connectivity
      └── Local diagnostics and JSON export
```

- [System architecture](docs/ARCHITECTURE.md)
- [Design decisions](docs/DESIGN_DECISIONS.md)
- [Software design](docs/SOFTWARE_DESIGN.md)
- [Feature compatibility](docs/FEATURE_COMPATIBILITY.md)
- [Protocol](docs/PROTOCOL.md)
- [Extensibility](docs/EXTENSIBILITY.md)

## Development

Requirements:

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.7.2
- Kotlin 2.0.21
- Gradle Wrapper 8.9

Configure the Android SDK for your environment, then run:

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease --no-daemon
```

On Windows, use:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release builds enable R8 and resource shrinking. A release APK must be signed with your own signing key before distribution.

## CI/CD and releases

- On pull requests, pushes to `main`, and manual runs, GitHub Actions runs unit tests and builds Debug and Release APKs.
- Pushing a `v*` tag (for example, `v0.2.8`) runs tests, builds and verifies a signed APK, and creates a GitHub Release. Tags containing `-` are marked as pre-releases.
- Before publishing, configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` under **Settings → Secrets and variables → Actions** in the GitHub repository. Keep a secure backup of the signing key. Future updates to the same Android package must use the same key.
- Increase `versionCode` for every release. The release workflow sets the APK `versionName` from the tag after removing its leading `v`.

## Features

### Overview and device controls

| Area | What you can do | Status |
| --- | --- | --- |
| Overview | View PM1.0, PM2.5, PM10, battery, filter life, and current fan speed | Available |
| Device connection | Scan for, select, and connect to a PuriCare Mini | Available |
| Device controls | Power, Auto, Turbo, fan speed, five indicator brightness levels, and sensor timing | Available |
| Filter management | View remaining life as a percentage and hours; configure replacement reminders at 3%, 5%, 10%, or 20% | Available |
| Background connection | Maintain a connection with a foreground service and retry a limited number of times after disconnects | Testing |
| Proximity-based automation | Automatically switch the device based on Bluetooth connection state | More real-device testing needed |
| Appearance and information | Follow system theme or choose light/dark mode; view device version and connection information | Available |
| Diagnostics export | User-initiated export of status and connection logs as JSON | Available |

Additional overview and control details:

- View PM1.0, PM2.5, PM10, battery/charging state, and filter life as a percentage and in hours.
- Set filter replacement reminders for 3%, 5%, 10%, or 20% remaining life.
- Filter reminders are off by default. Each filter cycle triggers a reminder only once; an increase in remaining life resets it automatically.
- If the device does not report total filter hours, the app says so and temporarily uses 2,000 hours for its estimate.
- Distinguish the current fan speed, Auto, and the independent Turbo state.
- Refresh device state automatically after a control command.
- Ask for confirmation before powering off, reducing the chance of accidentally making the device impossible to wake from the app.
- Set the air-quality sensor to run only while the product is on or to remain on at all times.

#### Battery and charging state

The device's main status reports both a **base battery level** and a **charging state**; interpret them together. The raw base-battery ranges are:

| Base battery level | Device-reported level |
| ---: | --- |
| 0–14% | Level 1 (low) |
| 15–69% | Level 2 (medium) |
| 70–99% | Level 3 (high) |
| 100% | Level 4 (full) |

Charging state `0` means not charging; the app displays the reported base battery level. State `1` means charging; the app displays “Charging” along with the current base level. State `2` means the device has confirmed a full charge; the app displays “Fully charged, 100%.” For example, the device may report a base level of 80%, then report charging state `2`; the app displays 100% because the confirmed-full state overrides the display. It does not proportionally convert 80% to 100%. The diagnostics export retains the displayed battery, raw base level, and charging state separately for verification.

### Connection and background operation

- Scan for nearby devices, with likely PuriCare Mini devices highlighted.
- Ask for confirmation before connecting to a device that has not been identified.
- Serialize GATT operations through a queue. On timeout, terminate that connection to prevent overlapping operations.
- After background disconnects, retry after 3, 6, 15, 30, and 60 seconds, then pause after the retry limit is reached.
- A persistent notification shows whether the app is connected, retrying, or paused, and can be tapped to return to the app.
- Background connection and proximity-based automation are mutually exclusive; enabling one disables the other.

### Appearance, information, and privacy

- Follow the system theme or select light or dark mode; the preference is remembered.
- View the device name, version supplied by the device, and app version.
- If the device does not provide a version field, the app says “Not provided by device.”
- Connection logs are held in memory only, with up to the most recent 500 entries.
- JSON is written only after the user explicitly chooses a destination.

## Testing on a phone

1. Close any other app that may be connected to the PuriCare Mini.
2. Install the Debug APK built from this project.
3. Grant the Nearby devices permission.
4. Open the **Device** tab and tap **Scan for nearby devices**.
5. Select the PuriCare Mini and wait for the status to show **Connected**.
6. Confirm the overview readings, then test fan speed, Auto, Turbo, and all five indicator brightness levels.
7. To report an issue, export JSON from the **Information** tab and review it before sharing.

## Troubleshooting

If scanning, connection, or status updates do not work as expected, try these steps in order:

1. Make sure the PuriCare Mini is powered on and is not connected to another phone or app.
2. Toggle Bluetooth off and back on.
3. Restart the device and scan again.
4. Confirm that the app has the Nearby devices permission. Background connection also requires notification permission.
5. If the issue persists, export the diagnostics JSON to help investigate the connection flow.

## Known limitations

- Background connectivity may be affected by power-saving policies from individual phone manufacturers.
- Proximity-based automation still needs validation across more phones, distances, and device power-off scenarios.
- The app does not provide device firmware updates.
- An unsigned Release APK cannot be distributed as an official release.

## Extensibility

The app currently provides BLE connectivity, a GATT FIFO queue, structured device snapshots, and background connectivity. A local history database, transactional outbox, external API, and Smart Home bridge have not been implemented and are not committed roadmap items. The open-source community can choose extensions to fit its IoT, Smart Home, or research needs.

| Extension area | Suggested foundation | Possible use cases |
| --- | --- | --- |
| Local database | Room/SQLite, migrations, retention policy | History, trend analysis, offline persistence |
| Transactional outbox | Store state and events in one transaction; idempotent synchronization | Self-hosted services, reliable data forwarding |
| Protected read-only API | HTTPS, read-only tokens, rate limiting | Home Assistant, agents, research tools |
| Home Assistant | RESTful sensors, MQTT bridge, or custom integration | Air-quality and device sensors |
| Node-RED | Structured events and example flows | Alerts, cross-device rules, and data forwarding |
| Device and air-quality dashboard | Latest-state and history-query APIs | Trends, multiple devices, and filter status |
| Remote measurement trigger (Touch) API | Protected command queue, FIFO, expiry, and execution status | Request an immediate state refresh on the phone |

Touch and any interface with side effects must be separate from the read-only API, disabled by default, and protected with separate permissions. See [Extensibility](docs/EXTENSIBILITY.md) for the full data model, API boundaries, security baseline, and suggested implementation order.

## Data and privacy

- The app does not connect to cloud services by default.
- It does not require location permission and does not read or store GPS coordinates.
- The last saved device information is stored in the app's private preferences.
- Exports do not include the device address.
- Do not add private device data, credentials, or user exports to the repository.

## License

This project is licensed under the [MIT License](LICENSE).
