# Proposed TODO

This is a proposed implementation sequence, including user-proposed additions, not a commitment to every feature. Agreed direction: emulator-first development of a live flow/pressure dashboard; physical-phone testing for the real Bluetooth connection. The app is live-only. Recording, replay, historical downloads, session browsing, and data export are not planned.

Checked implementation items mean the code exists; separate unchecked verification items identify behavior that still needs testing. Phone confirmations below were reported by Taso on October 16, 2026.

## Priorities

- **P1 — Next:** establish live-data correctness and dependable connection behavior before expanding the app.
- **P2 — Soon:** usability, requested features, supporting structure, and additional verification after the core is dependable.
- **P3 — Later:** optional features or abstractions that can wait until a concrete need arises.

Priorities apply to unchecked work; completed items keep their checked status. Within P1, start with decoded-sample tests and waveform/unit validation, then connection/recovery and sustained-stream checks. Define background behavior before testing screen lock and return. Treat longer windows, bounded buffering, and rendering aggregation as one P2 feature so performance work accompanies the larger windows.

## 1. Separate samples from the dashboard

- [ ] **P2** — Define a timestamped sample/batch model with explicit units and data-source identity.
- [ ] **P3** — Introduce a source interface for simulation and Bluetooth data.
- [x] Move simulation and buffering out of the composable; retain a bounded window in lifecycle-aware state.
- [ ] **P2** — Use a monotonic clock for pacing and retain original device timestamps separately.
- [ ] **P2** — Keep UI refresh independent of packet arrival and sampling frequency.

Acceptance: the dashboard works unchanged with a deterministic fake source, preserves its state across rotation, and does not grow memory indefinitely.

## 2. Make the charts trustworthy and comfortable

- [ ] **P2** — **User-proposed:** add 15-minute and 60-minute windows alongside 30 and 60 seconds.
- [ ] **P2** — Retain up to 60 minutes of incoming live data in a bounded in-memory buffer; show only available coverage while it fills, without persistence or historical backfill.
- [ ] **P2** — Keep long-window rendering responsive with display aggregation that preserves peaks and visible gaps; retain channel-specific sample timing.
- [ ] **P2** — **User-proposed:** graph additional available live channels, including leak, alongside flow and mask pressure. Confirm each channel's availability, units, and sample rate before presenting it as live data.

- [x] Render gaps as breaks; distinguish stale data, disconnected, reconnecting, demo, and frozen-view states.
- [x] Preserve a shared time axis and synchronized frozen snapshots across all charts/readings.
- [ ] **P1** — Handle values outside fixed scales without drawing outside the plot; choose readable scale behavior.
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

- [ ] **P1** — Test decoded sample counts, timestamps, values, and gaps with synthetic batch fixtures.
- [ ] **P1** — Verify repeated connect/disconnect cycles without duplicate subscriptions or sample delivery.
- [ ] **P1** — Verify explicit disconnect stops delivery and cancels pending automatic retries on the phone.
- [ ] **P1** — Verify recovery after Bluetooth toggles, out-of-range interruptions, and stream stalls; check retry exhaustion.
- [ ] **P1** — Validate units, cadence, timestamps, gaps, and waveforms against the Mac client or simultaneous SD recording.
- [ ] **P1** — Verify a sustained therapy stream remains responsive with bounded memory.

Acceptance: repeated pair/connect/stream/disconnect cycles work on the phone, and a longer therapy run remains responsive with bounded memory. Bluetooth transport alone does not establish waveform accuracy.

## 4. Live-view lifecycle

- [x] Keep connection and chart state in an activity-scoped ViewModel across rotation.
- [x] Verify rotation on the phone; pass emulator tests retaining the client, buffer, frozen view, and window.
- [x] Keep samples bounded in memory; do not persist or backfill sessions.
- [x] Mark the dashboard disconnected after two seconds without packets; show connection details on Connections.
- [ ] **P1** — Define and implement intended subscription behavior when the app leaves the foreground or the screen locks. No foreground service is implemented.
- [ ] **P1** — Verify screen lock, app switching, and clean return to the live view, including stale indicators and gaps.
- [ ] **P2** — Verify saved-session reconnect after process death and a cold app restart.
- [ ] **P2** — Measure battery impact during sustained streaming on a phone.

Acceptance: the app accurately communicates whether the live stream is active or interrupted, and never presents old samples as current readings.

## Later / optional

- [ ] **P3** — Event overlays after checking live event availability and timing.

## Out of scope

- Recording, replay, historical downloads, session browsing, and data export.

- Therapy-setting changes, firmware updates, or mode unlocking.
- Clinical recommendations or diagnostic interpretation.
- Cloud accounts, uploads, or synchronization.

## Connection screen

- [x] Move Bluetooth management to a separate Connections screen, opened through a clickable status chip.
- [x] Make simulation an off-by-default toggle; show Simulated status when enabled.
- [x] Clear buffered/frozen samples when switching sources; disconnect Bluetooth when enabling simulation.
- [x] Pass emulator tests for simulation defaults, switching, and state retention across rotation.
- [x] Implement state-based Connect / Cancel / Disconnect actions, automatic first-device discovery, Search again, Find another device, and Forget device.
- [x] Make discovered devices distinct selectable cards and add a back-navigation arrow.
- [x] Receive Taso's phone confirmation of the connection screen, simulation behavior, and subsequent visual refinements.
- [ ] **P2** — Verify the full revised first-device discovery/pairing flow and switching to another device on a phone.

Prior progress: initial physical-phone pairing and live reception, then remembered pairing, reconnect, and rotation were confirmed on October 16, 2026. The connection-screen refinements were subsequently approved on the phone. These confirmations do not establish sustained streaming, radio-interruption recovery, or waveform accuracy. No recording has been added.
