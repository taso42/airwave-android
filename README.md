# Airwave

An Android dashboard for viewing live ResMed AirSense 11 breathing data. The app starts disconnected with empty charts; simulated data is available through an optional toggle on the Settings screen. The first Android Bluetooth implementation successfully paired and received live data on a physical phone, as reported by Taso on October 16, 2026.

## Current features

- Scrolling flow and mask-pressure charts with a shared 30-second, 60-second, 5-minute, 15-minute, or 60-minute window.
- Current displayed readings in L/min and cmH₂O.
- Freeze/resume view; demo sampling continues while the view and readings are frozen.
- Separate Settings screen for Bluetooth controls and an off-by-default simulated-data toggle.
- Status row with a separate Connect/Cancel action and permanent Settings gear. Live/Simulated are status-only.

When enabled, the demo starts with one minute of synthetic history and uses a monotonic elapsed-time clock at a nominal 40 ms sample interval. This is a nominal 25 Hz source, not a validated realtime scheduler. Connection and display state live in an activity-scoped ViewModel and survive configuration recreation, including rotation. Process death still clears the in-memory chart buffer. Chart scales expand to include visible values. The Bluetooth path supports service-filtered discovery, GATT connection, displayed-code SRP pairing, encrypted RPC, and flow/pressure subscriptions. Samples are held only in memory for the live display.

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

- `app/src/main/java/com/vibex/airwave/MainActivity.kt`: dashboard, Settings screen, and chart rendering.
- `app/src/main/java/com/vibex/airwave/ui/theme/`: generated starter theme files; the dashboard currently defines its own palette.
- `app/src/test/` and `app/src/androidTest/`: protocol, sample conversion, chart-clock, simulation, pairing/lifecycle, and service-restart tests.
- `gradle/libs.versions.toml`: dependency versions.
- [TODO.md](TODO.md): proposed implementation sequence and acceptance checks.

The dashboard has been built, installed, and visually inspected on the emulator; subsequent phone acceptance is tracked in TODO.md.

## Related protocol exploration

The separate local project `../airsense11` contains the Mac Bluetooth exploration client, its pinned airbreak-plus dependency, exports, and offline SD comparisons. This Android repository does not include the Python dependency. Its Kotlin FIG/SRP implementation is informed by airbreak-plus; attribution and the upstream license are in THIRD_PARTY_NOTICES.md.

The Mac experiments received live flow and mask pressure with a declared 40 ms sample interval. Reliable live streaming is a working assumption for product development; sustained therapy streaming remains to be validated. Historical Bluetooth flow is lower resolution than SD flow and showed timing differences during comparison. Those experiments inform the protocol work but do not establish Android compatibility.

## Product direction

Start with a useful live view of flow and pressure. Keep the data source separate from presentation so simulation and Bluetooth can use the same dashboard. Add leak only after confirming its live channel and units. The app is live-only: no recording, replay, historical downloads, session browsing, or data export is planned. The bounded in-memory chart window and freeze-view snapshot support live inspection only.

The first Bluetooth milestone is pairing on Android and displaying received samples. Therapy-setting changes and firmware modification are outside this project's current scope.

## Local data

`.idea/`, build outputs, caches, `local.properties`, signing credentials, and `private/` are ignored by Git. Keep pairing material private; do not log or commit it. The app will not persist breathing samples. Use synthetic fixtures for shared tests. No cloud upload is implemented.

## Bluetooth phone test

1. Enable Bluetooth and bring the phone near the powered AirSense 11. Stop the Mac streaming client first.
2. Use **Set up** or the **Settings gear**, then grant the requested Bluetooth permissions (location on older Android versions).
3. Select the discovered ResMed device.
4. On the AirSense, open **Home → More → myAir**, then enter the displayed four-digit code in Airwave.
5. Tap **Pair and connect**. Successful authentication starts flow and mask-pressure streaming at a requested 40 ms interval.
6. Tap **Disconnect** to close the GATT connection.

Pairing credentials for one device are encrypted with an Android Keystore AES-GCM key and stored in the app’s no-backup directory. Reconnect uses the saved client ID and master key to authenticate a fresh session; no pairing code is needed unless the machine rejects the saved credentials. Use Forget device to remove the phone’s saved credentials. Link failures and a ten-second stream stall trigger up to five reconnect attempts with increasing delays (1, 2, 4, 8, 16 seconds). Explicit Disconnect cancels retries. A cold app launch offers Connect rather than silently connecting. Rotation preserves the client and buffer. Optional background streaming uses a foreground service; process termination still requires reconnection. With the option off, leaving pauses the connection and returning resumes it. Device timestamps pace chart positions; the dashboard shows Disconnected after two seconds without packets; connection details remain on the Settings screen. Flow is converted from the expected L/s protocol value to L/min; validate this with therapy data before treating the display as calibrated. No breathing data is recorded.

Validation: debug build and JVM unit tests pass, including Python-reference SRP/framing vectors, AES round-trip, corrupted-frame rejection, and server-proof rejection. Taso reported successful pairing and live data reception on the connected physical phone on October 16, 2026. This validates the initial end-to-end connection; sustained therapy and waveform accuracy remain to be validated. See TODO.md for the subsequently completed radio and lifecycle checks.

## Pairing and lifecycle validation

The updated debug build and JVM protocol tests pass. Two emulator instrumentation tests pass: activity recreation retains the same ViewModel/client, buffer, frozen view, and selected window; encrypted pairing survives storage recreation and can be deleted. These tests do not prove a live BLE connection survives radio interruption.

Phone acceptance sequence: pair once with this build; Disconnect → Connect without a code; rotate while streaming; briefly interrupt Bluetooth then restore it and check recovery; relaunch the app and use Connect. Longer interruptions may exhaust retries and require tapping Connect. Taso confirmed on October 16, 2026 that pairing is remembered, reconnect works, and rotation works on the phone. Subsequent phone checks confirmed short radio-off recovery, cancellation, radio-off timeout, cold restart, and task dismissal. Out-of-range and stream-stall testing remain separate.

Simulation is opt-in on each cold app launch. Switching sources clears the chart buffer and frozen view; enabling simulation disconnects Bluetooth. The dashboard status shows Simulated - 25Hz while simulation is enabled.

Settings searches automatically on first entry when no device is saved (unless simulation is active). Saved devices offer Connect; connecting offers Cancel; connected devices offer Disconnect. Find another device opens discovery, with Search again after a search completes. Forget device removes saved pairing and starts discovery.

## P1 reliability work

Settings now includes Keep streaming in background (default off). When off, leaving the activity disconnects and returning reconnects the previously active device; rotation preserves the session. When on, a connected-device foreground service supports streaming with a notification. Task dismissal stops the service; process death is not automatically restarted. Android 13+ requests notification permission when enabling this option; if denied, Android may show the foreground service only in its active-apps controls. Both modes passed short screen-lock and app-switching tests on the phone.

Live sample conversion now rejects invalid intervals, mismatched/empty channel arrays, non-finite values, and invalid timestamps. Synthetic conversion tests cover units, cadence, and gaps; they do not establish device calibration. Chart scales expand to include visible values, with drawing clipped to the plot.

Simulation also honors the background setting. Its monotonic 25 Hz clock skips paused time when the option is off, leaving a visible gap on return. When enabled, the foreground service supports continuous synthetic sampling with bounded catch-up after scheduling delays. This tests chart/lifecycle behavior, not Bluetooth recovery.

Reconnects wait until five seconds have elapsed after the previous GATT link closes. Three phone trials with a five-second manual pause connected immediately after rapid reconnects had intermittently failed; the enforced delay subsequently passed the repeated-connection phone test; its device-side cause remains unconfirmed. Cancel interrupts the wait; the initial connection has no settling delay.

The settling interval is presented as Disconnecting with progress. Connect becomes available after cleanup, so the next connect does not normally show a separate waiting phase. All waits suspend coroutines rather than blocking the UI thread.

The live chart axis advances using monotonic elapsed time even without incoming packets. Missing data creates empty space on the right; old samples leave the selected window. Freeze captures the axis endpoint and samples together. Source changes reset the timeline.

Dashboard navigation: the permanent gear opens Settings. Tap Disconnected / Connect to reconnect the saved device, or Cancel while connecting. With no saved device, Connect device opens setup. Live and Simulated are status indicators; Disconnect remains in Settings.

The dashboard connection control is a balanced row: status on the left, a separate Connect/Cancel action on the right, with no outer pill. Settings remains accessible through the gear.

## Longer live windows

The buffer retains up to one hour of the current 25 Hz stream (90,001 samples), only in memory. Long windows show available coverage while they fill; simulation starts with the same one-minute synthetic prefill. Drawing snapshots refresh at 4 Hz, preserving extrema from both channels and explicit outage boundaries while the elapsed-time axis continues scrolling. Freeze copies a stable snapshot; switching window sizes does not restart the data source. The notification now uses a monochrome breathing-wave Airwave icon.

### Additional live readings

The dashboard includes numeric readings for leak (L/min), respiratory rate
(breaths/min), tidal volume (mL), and minute ventilation (L/min). Bluetooth leak
values are multiplied by 60 and tidal volume by 1000; the other two are unchanged.
These provisional conversions are supported by channel definitions and observed
values; exact reference validation remains P3. Missing/null values display “—”,
not zero. Readings freeze with the chart snapshot. The shared 25 Hz subscription
does not make the underlying low-rate metrics update at 25 Hz.

Therapy pressure is displayed from `_INP` (`InspiratoryPressure`) in cmH₂O
without scaling, separately from the instantaneous mask-pressure waveform. It
matched the device’s steady 5.0 reading in the probe; exact screen equivalence
across pressure changes remains P3 validation. It appears in the live-status indicator and stays live while charts are frozen.
Unavailable/disconnected pressure is hidden; simulation also supplies this reading.
