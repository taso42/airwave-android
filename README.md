# Airwave

An Android dashboard for viewing live ResMed AirSense 11 breathing data. The app starts disconnected with empty charts; simulated data is available through an optional toggle on the Connections screen. The first Android Bluetooth implementation successfully paired and received live data on a physical phone, as reported by Taso on October 16, 2026.

## Current features

- Scrolling flow and mask-pressure charts with a shared 30- or 60-second window.
- Current displayed readings in L/min and cmH₂O.
- Freeze/resume view; demo sampling continues while the view and readings are frozen.
- Separate Connections screen for Bluetooth controls and an off-by-default simulated-data toggle.
- Clickable dashboard status opens Connections: Live with the sample rate, Simulated - 25Hz, or Disconnected in red.

When enabled, the demo starts with one minute of synthetic history and advances its simulated clock by 40 ms per update. This is a nominal 25 Hz source, not a validated realtime scheduler. Connection and display state live in an activity-scoped ViewModel and survive configuration recreation, including rotation. Process death still clears the in-memory chart buffer. Chart scales are fixed. The Bluetooth path supports service-filtered discovery, GATT connection, displayed-code SRP pairing, encrypted RPC, and flow/pressure subscriptions. Samples are held only in memory for the live display.

## Run

1. Open this directory in Android Studio and let Gradle sync.
2. Install the SDK/platform requested by the project (currently compile/target SDK 37; minimum SDK 24).
3. Start an Android virtual device from Device Manager.
4. Run the `app` configuration.

The emulator is the primary UI development environment. Physical-phone testing will be used for actual Bluetooth pairing, streaming, reconnect behavior, and Android lifecycle behavior.

For command-line builds on this Mac:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Structure

- `app/src/main/java/com/vibex/airwave/MainActivity.kt`: dashboard, Connections screen, and chart rendering.
- `app/src/main/java/com/vibex/airwave/ui/theme/`: generated starter theme files; the dashboard currently defines its own palette.
- `app/src/test/` and `app/src/androidTest/`: starter tests; dashboard-specific tests are still proposed.
- `gradle/libs.versions.toml`: dependency versions.
- [TODO.md](TODO.md): proposed implementation sequence and acceptance checks.

The initial dashboard was compiled, installed, and visually inspected on the emulator. The later header removal has not had a separate emulator verification.

## Related protocol exploration

The separate local project `../airsense11` contains the Mac Bluetooth exploration client, its pinned airbreak-plus dependency, exports, and offline SD comparisons. This Android repository does not include the Python dependency. Its Kotlin FIG/SRP implementation is informed by airbreak-plus; attribution and the upstream license are in THIRD_PARTY_NOTICES.md.

The Mac experiments received live flow and mask pressure with a declared 40 ms sample interval. Reliable live streaming is a working assumption for product development; sustained therapy streaming remains to be validated. Historical Bluetooth flow is lower resolution than SD flow and showed timing differences during comparison. Those experiments inform the protocol work but do not establish Android compatibility.

## Product direction

Start with a useful live view of flow and pressure. Keep the data source separate from presentation so simulation and Bluetooth can use the same dashboard. Add leak only after confirming its live channel and units. The app is live-only: no recording, replay, historical downloads, session browsing, or data export is planned. The short in-memory chart window and freeze-view snapshot support live inspection only.

The first Bluetooth milestone is pairing on Android and displaying received samples. Therapy-setting changes and firmware modification are outside this project's current scope.

## Local data

`.idea/`, build outputs, caches, `local.properties`, signing credentials, and `private/` are ignored by Git. Keep pairing material private; do not log or commit it. The app will not persist breathing samples. Use synthetic fixtures for shared tests. No cloud upload is implemented.

## Bluetooth phone test

1. Enable Bluetooth and bring the phone near the powered AirSense 11. Stop the Mac streaming client first.
2. Tap the dashboard status to open **Connections**, then grant the requested Bluetooth permissions (location on older Android versions).
3. Select the discovered ResMed device.
4. On the AirSense, open **Home → More → myAir**, then enter the displayed four-digit code in Airwave.
5. Tap **Pair and connect**. Successful authentication starts flow and mask-pressure streaming at a requested 40 ms interval.
6. Tap **Disconnect** to close the GATT connection.

Pairing credentials for one device are encrypted with an Android Keystore AES-GCM key and stored in the app’s no-backup directory. Reconnect uses the saved client ID and master key to authenticate a fresh session; no pairing code is needed unless the machine rejects the saved credentials. Use Forget device to remove the phone’s saved credentials. Link failures and a ten-second stream stall trigger up to five reconnect attempts with increasing delays (1, 2, 4, 8, 16 seconds). Explicit Disconnect cancels retries. A cold app launch offers Connect rather than silently connecting. Rotation preserves the client and buffer. This is not a foreground service: uninterrupted streaming while Android suspends or kills the app is not guaranteed; saved pairing supports reconnection afterward. Device timestamps pace chart positions; the dashboard shows Disconnected after two seconds without packets; connection details remain on the Connections screen. Flow is converted from the expected L/s protocol value to L/min; validate this with therapy data before treating the display as calibrated. No breathing data is recorded.

Validation: debug build and JVM unit tests pass, including Python-reference SRP/framing vectors, AES round-trip, corrupted-frame rejection, and server-proof rejection. Taso reported successful pairing and live data reception on the connected physical phone on October 16, 2026. This validates the initial end-to-end connection; sustained therapy waveform accuracy and radio-interruption recovery remain to be tested.

## Pairing and lifecycle validation

The updated debug build and JVM protocol tests pass. Two emulator instrumentation tests pass: activity recreation retains the same ViewModel/client, buffer, frozen view, and selected window; encrypted pairing survives storage recreation and can be deleted. These tests do not prove a live BLE connection survives radio interruption.

Phone acceptance sequence: pair once with this build; Disconnect → Connect without a code; rotate while streaming; briefly interrupt Bluetooth then restore it and check recovery; relaunch the app and use Connect. Longer interruptions may exhaust retries and require tapping Connect. Taso confirmed on October 16, 2026 that pairing is remembered, reconnect works, and rotation works on the phone. Radio-interruption recovery and cold-process restart remain separate checks; they were not explicitly confirmed.

Simulation is opt-in on each cold app launch. Switching sources clears the chart buffer and frozen view; enabling simulation disconnects Bluetooth. The dashboard status shows Simulated - 25Hz while simulation is enabled.

Connections searches automatically on first entry when no device is saved (unless simulation is active). Saved devices offer Connect; connecting offers Cancel; connected devices offer Disconnect. Find another device opens discovery, with Search again after a search completes. Forget device removes saved pairing and starts discovery.
