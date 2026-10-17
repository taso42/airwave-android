# Proposed TODO

This is a proposed implementation sequence, including user-proposed additions, not a commitment to every feature. Agreed direction: emulator-first development of a live flow/pressure dashboard; physical-phone testing for the real Bluetooth connection. The app is live-only. Recording, replay, historical downloads, session browsing, and data export are not planned.

Checked implementation items mean the code exists; separate unchecked verification items identify behavior that still needs testing. Phone confirmations below were reported by Taso on October 16, 2026.

## Priorities

- **P1 — Next:** establish live-data correctness and dependable connection behavior before expanding the app.
- **P2 — Soon:** usability, requested features, supporting structure, and additional verification after the core is dependable.
- **P3 — Later:** optional features or abstractions that can wait until a concrete need arises.

Priorities apply to unchecked work; completed items keep their checked status. Remaining P1 work is sustained-use acceptance (T11). Live-data accuracy validation is P3. Additional stall/recovery, duplicate-delivery, memory-stress, and chart-variation checks are P3 hardening. Treat longer windows, bounded buffering, and rendering aggregation as one P2 feature so performance work accompanies the larger windows.

## 1. Separate samples from the dashboard

- [ ] **P2** — Define a timestamped sample/batch model with explicit units and data-source identity.
- [ ] **P3** — Introduce a source interface for simulation and Bluetooth data.
- [x] Move simulation and buffering out of the composable; retain a bounded window in lifecycle-aware state.
- [ ] **P2** — Use a monotonic clock for pacing and retain original device timestamps separately.
- [ ] **P2** — Keep UI refresh independent of packet arrival and sampling frequency.

Acceptance: the dashboard works unchanged with a deterministic fake source, preserves its state across rotation, and does not grow memory indefinitely.

## 2. Make the charts trustworthy and comfortable

- [x] **User-proposed:** add 15-minute and 60-minute windows alongside 30 and 60 seconds.
- [x] Add a 5-minute chart window; phone-verified by Taso on October 17, 2026.
- [x] Retain up to 60 minutes of incoming live data in a bounded in-memory buffer; show only available coverage while it fills, without persistence or historical backfill.
- [x] Keep long-window rendering responsive with display aggregation that preserves peaks and visible gaps; retain channel-specific sample timing.
- [ ] **P2** — **User-proposed:** display therapy time. Confirm the available source and session semantics; label it therapy time, not sleep time.
- [x] **User-proposed:** display leak as a live numeric reading.
- [x] **User-proposed:** display respiratory rate as a live numeric reading.
- [x] **User-proposed:** display tidal volume as a live numeric reading.
- [x] **User-proposed:** display minute ventilation as a live numeric reading.

For these metrics, decide presentation at implementation time (line charts, numerical indicators, or another appropriate view). Confirm device availability, units, and update cadence before presenting values as live data.

- [x] Render gaps as breaks; distinguish stale data, disconnected, reconnecting, demo, and frozen-view states.
- [x] Preserve a shared time axis and synchronized frozen snapshots across all charts/readings.
- [x] Advance the chart axis from monotonic elapsed time during outages; filter expired samples and freeze the axis only with Freeze view. Automated clock tests cover scrolling, expiry, and resumption.
- [x] Phone-verify manual Disconnect preserves samples and lets the chart scroll them out — confirmed by Taso.
- [x] Phone-verify Freeze/Resume: display stays fixed while sampling continues; Resume catches up without an artificial gap.
- [ ] **P3** — Improve the visual design language of Freeze/Resume controls.
- [ ] **P3** — Phone-verify the updated axis with radio outages and both simulation background modes.
- [x] Expand chart scales to include visible values and clip drawing to the plot bounds.
- [ ] **P2** — Check compact screens, landscape, large fonts, contrast, and accessibility descriptions.
- [ ] **P2** — Add focused UI tests for window selection and freeze/resume behavior.

Acceptance: a stalled source cannot look like live data, missing intervals are visible, and controls remain usable at large font sizes.

## 3. Bluetooth implementation and verification

### Implemented

- [x] Review airbreak-plus protocol and license; include attribution and upstream license.
- [x] Implement BLE permissions, discovery, device selection, and displayed-code pairing.
- [x] Implement authenticated sessions, framing, fragmentation, notifications, and protocol error handling.
- [x] Encrypt saved pairing credentials with Android Keystore; exclude them from backups and avoid logging secrets.
- [x] Decode flow/pressure batches and convert expected flow units from L/s to L/min. Calibration remains unverified below.
- [x] Subscribe to flow and mask pressure; close the Bluetooth connection on explicit Disconnect.
- [x] Implement saved-session reconnect, bounded automatic retries, and a stream-stall watchdog.
- [x] Add protocol tests covering reference SRP/framing vectors, encryption, corrupted frames, and server-proof rejection.

### Verified

- [x] Pair and receive live flow/pressure on a physical phone — confirmed by Taso.
- [x] Remember pairing and reconnect without another code — confirmed by Taso.
- [x] Pass protocol unit tests and emulator encrypted-pairing storage/deletion tests.

### Still unverified / remaining tests

- [x] Test decoded batch conversion, sample counts/cadence, units, gaps, invalid timestamps, intervals, and non-finite values with synthetic fixtures.
- [x] Add StreamData JSON fixtures covering channel order, missing/duplicate channels, and conversion into chart samples.
- [ ] **P3** — Verify repeated connect/disconnect cycles without duplicate subscriptions or sample delivery.
- [x] Verify explicit disconnect stops delivery and cancels pending automatic retries on the phone — T2 and T4 pass.
- [ ] **P3** — Verify recovery after Bluetooth toggles, out-of-range interruptions, and stream stalls; check retry exhaustion.
- [ ] **P3** — Validate units, cadence, timestamps, gaps, and waveforms against the Mac client or simultaneous SD recording.
- [ ] **P1** — Verify a sustained therapy stream stays connected and responsive (T11); instrumented memory checks are P3.

Acceptance: repeated pair/connect/stream/disconnect cycles work on the phone, and a longer therapy run remains responsive with bounded memory. Bluetooth transport alone does not establish waveform accuracy.

## 4. Live-view lifecycle

- [x] Keep connection and chart state in an activity-scoped ViewModel across rotation.
- [x] Verify rotation on the phone; pass emulator tests retaining the client, buffer, frozen view, and window.
- [x] Keep samples bounded in memory; do not persist or backfill sessions.
- [x] Mark the dashboard disconnected after two seconds without packets; show connection details on Connections.
- [x] Add persistent Keep streaming in background setting, default off. Pause/disconnect on leaving and reconnect on return when off; use a connected-device foreground service when on.
- [x] Verify both background modes on a connected phone, including task dismissal — T5–T8 and T13 pass.
- [x] Verify screen lock, app switching, and clean return to the live view — T5–T8 pass on the phone for both background settings.
- [x] Verify saved-session reconnect after force-stop and a cold app restart — T14 passes; arbitrary OS process-death conditions are not separately verified.
- [ ] **P2** — Measure battery impact during sustained streaming on a phone.

Acceptance: the app accurately communicates whether the live stream is active or interrupted, and never presents old samples as current readings.

## Later / optional

- [ ] **P3** — Event overlays after checking live event availability and timing.

## Out of scope

- Recording, replay, historical downloads, session browsing, and data export.

- Therapy-setting changes, firmware updates, or mode unlocking.
- Clinical recommendations or diagnostic interpretation.
- Cloud accounts, uploads, or synchronization.

## Settings screen

- [x] **User-proposed:** remove the fixed five-second Disconnecting delay; retain callback-based GATT cleanup with a two-second timeout.
- [ ] **P2** — Phone-verify repeated disconnect/reconnect after removing the delay.

- [x] Separate quick connection from Settings navigation: tapping the Disconnected status chip connects to the saved device; show Connecting with tap-to-cancel while connecting. Keep Live as a status indicator and Disconnect in Settings. Add a permanent gear icon with an accessible Settings label. With no saved device, show Connect device and open setup. Replace the status chip’s navigation chevron with a retry/connect affordance where applicable.

- [x] Replace the generic notification icon with a recognizable Airwave notification icon.

- [x] Move Bluetooth management to a separate Connections screen, opened through a clickable status chip.
- [x] Make simulation an off-by-default toggle; show Simulated status when enabled.
- [x] Clear buffered/frozen samples when switching sources; disconnect Bluetooth when enabling simulation.
- [x] Pass emulator tests for simulation defaults, switching, and state retention across rotation.
- [x] Implement state-based Connect / Cancel / Disconnect actions, automatic first-device discovery, Search again, Find another device, and Forget device.
- [x] Make discovered devices distinct selectable cards and add a back-navigation arrow.
- [x] Receive Taso's phone confirmation of the connection screen, simulation behavior, and subsequent visual refinements.
- [ ] **P2** — Verify the full revised first-device discovery/pairing flow and switching to another device on a phone.

Prior progress: initial physical-phone pairing and live reception, then remembered pairing, reconnect, and rotation were confirmed on October 16, 2026. The connection-screen refinements were subsequently approved on the phone. These confirmations do not establish sustained streaming, radio-interruption recovery, or waveform accuracy. No recording has been added.

- [x] Make simulation honor the background setting using elapsed time; test paused gaps and bounded continuous sampling.
- [x] Verify simulation background on/off behavior on the phone — Taso reports both work.
- [x] Verify foreground-service notification visibility — T9 passed on the phone.

## Phone test checklist

These are the manual acceptance steps for the open items above. Check an item only after its expected result is observed; record failures below it. Start with T1 and T2. Visual checks do not establish sample-level correctness or bounded memory.

### Setup

Turn simulation off, connect to the powered AirSense, select **60 sec**, and leave the chart unfrozen. Enable **Keep streaming in background** for radio tests so opening Android controls does not intentionally pause the connection. A powered machine is enough for connection checks; use your normal mask setup when evaluating breathing waveforms. Return to this starting state before each independent test.

- [x] **P1 · T1 — Short Bluetooth interruption:** Turn the phone's Bluetooth off for about 5 seconds, then on. Expect Disconnected, automatic recovery without another pairing code, then Live. Settings should show reconnect progress. New samples should reveal a gap if the earlier samples are still within the displayed window. Allow up to a minute for recovery; record elapsed time and any error.
- [x] **P1 · T2 — Cancel recovery:** While connected, turn Bluetooth off. In Airwave Settings, tap Cancel while reconnecting. Restore Bluetooth and wait 30 seconds. Expect no automatic reconnection or incoming samples. Tap Connect; expect recovery without a pairing code.
- [x] **P1 · T3 — Radio-off recovery timeout:** Connect, then disable Bluetooth for 90 seconds. Expect waiting to stop after about 60 seconds; inspect Settings to confirm rather than relying only on elapsed time. Restore Bluetooth. Expect it to remain disconnected until Connect is tapped, then recover without a pairing code.
- [x] **P1 · T4 — Repeated connections:** Perform Disconnect → Connect five times. Each Disconnect should stop updates; each Connect should resume without a code. Watch for errors or visibly accelerated/duplicated waveforms. Duplicate sample delivery still needs instrumented verification beyond this visual check.
- [x] **P1 · T5 — Screen lock, background off:** Disable background streaming, connect, lock for 15 seconds, then return. Expect reconnection and a visible gap after new samples arrive.
- [x] **P1 · T6 — App switching, background off:** With background streaming off, connect, switch to another app for 15 seconds, then return. Expect reconnection and a gap.
- [x] **P1 · T7 — Screen lock, background on:** Enable background streaming, connect, lock for 15 seconds, then return. Expect continued streaming without an intentional pause or reconnection gap.
- [x] **P1 · T8 — App switching, background on:** With background streaming on, connect, use another app for 15 seconds, then return. Expect continued streaming without an intentional pause or reconnection gap.
- [x] **P1 · T9 — Notification visibility:** Check Airwave's notification permission and channel settings in Android app settings. Enable notifications, toggle background streaming off/on, and start simulation or connect. Expect an ongoing notification in the notification shade; a sound or banner is not required. If permissions/channels are enabled but no notification appears, record it as a failure.
- [ ] **P3 · T10 — Out-of-range recovery (skipped for now):** While connected with background streaming on, walk away with the phone until the link actually drops. Return within range before retries are exhausted. Expect automatic reconnection without a code and a gap. If retries have stopped, use Connect. Record which path occurred; do not mark automatic recovery verified if only manual Connect worked.
- [ ] **P1 · T11 — Sustained normal use:** During normal use, stream for 30–60 minutes. Check connection status periodically and confirm charts and Settings remain responsive. Record duration, interruptions, and approximate battery change. Instrumented memory checks are tracked separately at P3.

### Additional lifecycle checks

- [x] **P1 · T13 — Task dismissal:** With background streaming on and the device connected, swipe Airwave away from Android's recent-apps screen. Expect the service/notification to stop. Reopen Airwave; expect Disconnected with saved pairing available, then Connect without another code. Confirm actual Bluetooth shutdown separately if UI observations are inconclusive.
- [x] **P2 · T14 — Cold restart:** While disconnected, force-stop Airwave through Android app settings, then reopen it. Expect Disconnected, retained pairing, and successful Connect without another code. This checks a cold restart; it does not simulate every OS process-death condition.

### Checks to run with development support

- [ ] **P3 — Stream-stall recovery:** Use a controlled test to stop sample delivery while preserving the link. Verify the watchdog and retry limit, plus cancellation during recovery. Bluetooth-off testing alone does not cover this condition.
- [ ] **P3 — Sample delivery and memory:** Instrument repeated connection cycles and a sustained run to verify no duplicate delivery and bounded memory.
- [ ] **P3 — Waveform comparison:** Arrange live Android data and a matching Mac or SD reference; compare units, cadence, timestamps, gaps, and waveforms. Existing historical SD comparisons do not validate this Android live path.

### Test results

Record each result as: **test ID — pass/fail/inconclusive — observed behavior — recovery time/error text**. Include the background setting and whether simulation was enabled when relevant. Do not include pairing codes or secrets.

T1 — **failed on phone**: restoring Bluetooth left Airwave disconnected; manual Connect was required. Added explicit adapter-state monitoring, a cancellable 60-second wait while Bluetooth is off, and immediate reconnect on Bluetooth ON while recovery is still requested. Taso subsequently reported that T1 passes on the phone after this fix. Taso subsequently confirmed T2 passes as well. After 60 seconds with the radio off, automatic recovery stops (T3).

T1 retest — **pass reported by Taso** after the adapter-state recovery fix. Exact recovery time was not reported. This verifies the short Bluetooth toggle case only; cancellation, retry exhaustion, and out-of-range recovery remain open.

T2 — **pass reported by Taso**: cancellation prevents automatic recovery when Bluetooth returns; manual Connect succeeds without another pairing code.

T3 — **pass reported by Taso**: radio-off recovery times out, restoring Bluetooth does not automatically reconnect afterward, and manual Connect succeeds. This verifies the radio-off timeout; exhaustion of connection retries while the radio stays on remains separate.

T4 — **failed on phone**: app crashed. Crash log identifies ForegroundServiceDidNotStartInTimeException. Service start/stop now uses request identities and defers cancellation of pending starts until foreground promotion; stale destruction callbacks cannot disconnect a newer session. T4 remains unchecked pending phone retest.

T4 fix validation — build and unit tests pass; rapid foreground-service start/cancel/restart regression passes on emulator and physical phone. Actual repeated Bluetooth connection cycles still require T4 retest.

T4 retest — **intermittent failure reported by Taso**: device returned VerificationFailure with advice to forget pairing, but another Connect succeeded with the existing credentials. Saved pairing is therefore not established as invalid. Removed generic forget-pairing advice and included the rejected RPC method in the error to identify the failing stage on recurrence. Root cause of the intermittent rejection remains unconfirmed; T4 stays open.

T4 diagnosis update — repeated RequestSession / VerificationFailure reported. Upstream Mac client awaits disconnect; Android previously disconnected and closed immediately. Android now waits for disconnect (bounded at two seconds) before starting another link, and retries only RequestSession / VerificationFailure up to three times with backoff. This is a mitigation for the observed transient behavior, not confirmation of the device-side cause. Integrity-proof rejection and other RPC errors remain terminal. T4 retest remains required.

T4 latest retest — **automatic recovery observed, delays unresolved**: Taso reports pauses at each displayed x/5 retry step followed by successful connection. Exact retry count, accompanying error, elapsed time, and whether all five manual cycles completed are not yet established. T4 remains open; do not infer every retry was RequestSession / VerificationFailure from the counter alone.

T4 diagnostics — Taso observed both VerificationFailure and Bluetooth disconnected (147). App-scoped Android logs confirm some status-147 drops immediately after connection, before service discovery completes. Added connection-stage/RPC-method/error-code logs without credentials, device identifiers, or sample payloads. Device-side cause remains unconfirmed. Next controlled check: Disconnect, wait 15 seconds, then Connect; compare with rapid reconnect.

T4 controlled-delay result — Taso reports immediate connection after the requested 15-second pause between Disconnect and Connect. This supports a reconnect-timing dependency but does not establish its cause, the minimum delay, or the number of successful repetitions. Next check: repeat with a five-second pause.

T4 controlled-delay follow-up — Taso confirms all three reconnects succeeded immediately after a five-second manual pause. Added a cancellable five-second settling interval after GATT closure, counting elapsed idle time, with Waiting to reconnect status. This is an empirically supported workaround, not a confirmed device-side root cause. T4 remains open pending five cycles without manual pauses on this build.

Disconnect UX — settling now occurs in an explicit Disconnecting state with progress; Connect and device-management actions are hidden until cleanup completes. Coroutine waits do not block the UI thread. This changes presentation, not the unresolved device-session timing workaround.

- [x] Show amber progress in the tappable dashboard status chip while connecting, reconnecting, waiting for Bluetooth, or disconnecting; show Pairing required when code entry is needed.

T4 final phone retest — **pass reported by Taso on October 17, 2026** with the disconnect-settling workaround and progress indicator. Earlier failures above are retained as history. The device-side timing cause remains unresolved; instrumented duplicate-delivery verification remains open.

T5 — **pass reported by Taso on October 17, 2026**: screen lock with background streaming off reconnects on return and shows a gap.

T6 — **pass reported by Taso on October 17, 2026**: app switching with background streaming off reconnects on return and shows a gap.

T7 — **pass reported by Taso on October 17, 2026**: screen lock with background streaming on maintains live streaming without a reconnection gap.

T8 — **pass reported by Taso on October 17, 2026**: app switching with background streaming on maintains live streaming without a reconnection gap. T5–T8 complete the short screen-lock/app-switching acceptance checks; sustained streaming remains separate.

T9 — **pass reported by Taso on October 17, 2026**: notification is visible.


T13 — **pass reported by Taso on October 17, 2026**: task dismissal removes the notification; reopening shows Disconnected and saved pairing reconnects without another code.

T14 — **pass reported by Taso on October 17, 2026**: force-stop/cold restart retains pairing and Connect succeeds without another code.

Chart-axis phone failure — manual Disconnect cleared the buffer, and preparing manual Connect reset it again. Both actions now preserve samples and the moving axis; changing source/device still clears the display. Added a regression check for retained samples and advancing time through disconnect/reconnect preparation. Phone retest remains open.

Chart-axis retest — **pass reported by Taso on October 17, 2026** after removing sample clearing from manual disconnect/reconnect. Broader axis checks remain listed separately.

Freeze/Resume — **pass confirmed by Taso on October 17, 2026**: the display stayed fixed and caught up on Resume. Sampling continues while frozen, so freezing alone should not create a gap.

Quick connection UI implemented: permanent accessible Settings gear; saved-device Connect and connecting Cancel on the status chip; Live/Simulated are status-only. No saved device opens setup. Build verification does not replace phone acceptance of the new interaction.

Parallel P2 implementation — notification icon and longer chart windows were implemented by separate agents with disjoint file ownership. Long windows use a 90,001-sample / one-hour ring and 4 Hz peak/gap-preserving render snapshots for the current 25 Hz flow/pressure stream. No recording or historical backfill added. Larger windows fill from incoming data; simulation retains its existing one-minute prefill.

Parallel feature validation — combined build/JVM tests and nine emulator instrumentation tests passed. Four window controls visually checked on the emulator. New notification icon and long-window behavior still need phone acceptance; no hour-long phone run was claimed.

Live-metric research (local airbreak sources) — rpc_streams.md documents StartStream IDs `_LKF`/`Leak`, `_RR2`, `_TD2`, `_MV2` at native two-second cadence. EDF references suggest L/s, breaths/min, L, and L/min respectively, but actual RPC scaling and acceptance on this device remain unverified. Probe these before implementing trusted live readings; 40ms requests repeat low-rate values. No elapsed therapy-duration channel was established; `_CUD` is a last-use timestamp, not therapy duration.

Long-window phone feedback — Taso reports 15/60-minute views work, but compression limits their usefulness; requested a five-minute option (added). Freeze, change window, and Resume acceptance passed. Existing 15/60-minute options retained.

Icon phone feedback — user still sees a green Android icon. Notification small icon was changed, but launcher/adaptive app assets remained the Android starter icon; replacing app-icon assets with matching Airwave artwork. Visual acceptance remains open.

Live-metric device probe — October 17, 2026: all four IDs (`_LKF`, `_RR2`, `_TD2`, `_MV2`) accepted. Mac capture `private/live/metrics-agent-20261017-005304.jsonl` in the airsense11 project contains 60 StreamData packets with declared 2000 ms intervals and exactly 2-second device timestamp increments. Each channel has 29 null samples while the user reported standby, followed by 31 numeric samples after therapy started. Raw ranges: Leak 0–0.16, respiratory rate 0–13, tidal volume 0–0.78, minute ventilation 0–8.4 (including startup zeros). Availability and two-second delivery are verified; units/scaling, native update cadence, and Android presentation remain pending. Null must remain unavailable rather than being converted to zero. Concurrent Mac probes may have contributed to earlier session failures; cause unconfirmed.

Additional metric displays — added live numeric cards for leak (RPC value ×60, L/min), respiratory rate (unchanged, breaths/min), tidal volume (×1000, mL), and minute ventilation (unchanged, L/min). Same stream as flow/pressure; 40 ms subscription does not increase these channels’ native update rate. Null/missing readings remain unavailable; frozen cards use the chart snapshot. Simulation supplies illustrative readings.

- [ ] **P2** — Phone-verify the four new readings during therapy, unavailable values in standby/disconnection, and Freeze/Resume.
- [ ] **P3** — Independently validate live metric units/scaling against a matching reference capture; conversions are provisional and approved for implementation.

Pressure-screen investigation — October 17, 2026: screen reported 5.0 during a one-minute Mac probe. `_INP`, `_MKI`, `_ISP`, `_CSP`, and `_AT5` all remained 5.0; `_MKE` remained 4.0 while instantaneous `_MKP` varied 3.2–5.7. `_INP`/`_MKI` are candidates for a steady treatment-pressure display, but exact GUI-source equivalence remains unresolved because five candidates coincided. Evidence in airsense11 `private/live/pressure-agent-20261017-010354.jsonl`.

Session-rejection investigation — Android and Mac both save the device-returned clientId and SRP master key after server-proof verification, then send the saved clientId unchanged in RequestSession. Android encrypted-store round-trip coverage verifies both fields. Phone logs show RequestSession rejection before the master-key proof or metric subscription; the new channels cannot cause this stage’s error. Available protocol documentation does not establish pairing-slot limits or why this client ID is rejected. No existing credentials were deleted or replaced. Removed fixed settling waits; connection/retry status now takes precedence over overlapping GATT cleanup in the UI.

Pairing diagnostics — added secret-free request IDs, response-lane/pending/duplicate metadata, field types/lengths/hex-format flags, frame/chunk counts, completed writes, elapsed times, and pairing-exchange age. No codes, key/proof values, credential fingerprints, device identifiers, or sample payloads are logged by these diagnostics. Kotlin public-key padding and leading-zero code proof match Python reference tests. Root cause of the observed ConfirmKeyExchange rejection followed by StartKeyExchange Invalid Params remains unresolved; one instrumented phone pairing attempt is required to test request shape, delivery, response matching, and exchange timing.

- [x] Add Therapy pressure numeric reading from `_INP` / InspiratoryPressure, unchanged in cmH₂O, with unavailable handling, frozen snapshots, and simulation.
- [ ] **P2** — Phone-verify Therapy pressure during therapy and Freeze/Resume.
- [ ] **P3** — Validate Therapy pressure matches the device screen across natural pressure/ramp changes; the observed steady 5.0 match did not uniquely identify the GUI source.

Phone feedback — four additional metric displays look good; user also reports fresh pairing and saved-session reconnect succeeded before the diagnostic update. Intermittent pairing/session rejection remains unresolved.

Therapy pressure layout — moved from its dedicated card into the Live/Simulated status line, with an accessibility label naming Therapy pressure. It follows the latest sample even while charts are frozen and is hidden when unavailable/disconnected. Phone layout and frozen-view acceptance remain to be checked.
