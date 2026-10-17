# Proposed TODO

This is a proposed implementation sequence, including user-proposed additions, not a commitment to every feature. Agreed direction: emulator-first development of a live flow/pressure dashboard; physical-phone testing for the real Bluetooth connection. The app is live-only. Recording, replay, historical downloads, session browsing, and data export are not planned.

## 1. Separate samples from the dashboard — recommended next

- [ ] Define a timestamped sample/batch model with explicit units and data-source identity.
- [ ] Introduce a source interface for demo and eventual Bluetooth data.
- [ ] Move simulation and buffering out of the composable; retain a bounded window in lifecycle-aware state.
- [ ] Use a monotonic clock for pacing and retain original device timestamps separately.
- [ ] Keep UI refresh independent of packet arrival and sampling frequency.

Acceptance: the dashboard works unchanged with a deterministic fake source, preserves its state across rotation, and does not grow memory indefinitely.

## 2. Make the charts trustworthy and comfortable

- [ ] **User-proposed:** add 15-minute and 60-minute windows alongside 30 and 60 seconds.
- [ ] Retain up to 60 minutes of incoming live data in a bounded in-memory buffer; show only available coverage while it fills, without persistence or historical backfill.
- [ ] Keep long-window rendering responsive with display aggregation that preserves peaks and visible gaps; retain channel-specific sample timing.
- [ ] **User-proposed:** graph additional available live channels, including leak, alongside flow and mask pressure. Confirm each channel's availability, units, and sample rate before presenting it as live data.

- [ ] Render gaps as breaks; distinguish stale data, disconnected, reconnecting, demo, and frozen-view states.
- [ ] Preserve a shared time axis and synchronized frozen snapshots across all charts/readings.
- [ ] Handle values outside fixed scales without drawing outside the plot; choose readable scale behavior.
- [ ] Check compact screens, landscape, large fonts, contrast, and accessibility descriptions.
- [ ] Add focused UI tests for window selection and freeze/resume behavior.

Acceptance: a stalled source cannot look like live data, missing intervals are visible, and controls remain usable at large font sizes.

## 3. Connect a physical phone

- [ ] Review airbreak-plus protocol code and license before porting or reusing implementation.
- [ ] Implement Android BLE permissions, discovery, device selection, and displayed-code pairing.
- [ ] Implement authenticated sessions, framing, fragmentation, notifications, and protocol error handling.
- [ ] Store pairing secrets securely and exclude them from logs and backups.
- [ ] Decode sample batches, confirm units, and explicitly convert flow to L/min for display.
- [ ] Test sample counts, timestamps, values, and gaps with synthetic protocol fixtures.
- [ ] Subscribe to flow and mask pressure; cleanly unsubscribe on explicit stop.
- [ ] Add reconnect behavior and prevent duplicate subscriptions/sample delivery.
- [ ] Validate units, cadence, timestamps, gaps, and waveform agreement against the Mac client or simultaneous SD recording.

Acceptance: repeated pair/connect/stream/disconnect cycles work on the phone, and a longer therapy run remains responsive with bounded memory. Bluetooth transport alone does not establish waveform accuracy.

## 4. Validate live-view lifecycle

- [ ] Define and implement subscription behavior when the app leaves the foreground or the screen locks.
- [ ] Resume the live view cleanly on return, clearly marking stale data and connection gaps.
- [ ] Test screen lock, app switching, Bluetooth toggles, out-of-range recovery, and battery impact on a phone.
- [ ] Keep samples bounded in memory; do not persist or backfill sessions.

Acceptance: the app accurately communicates whether the live stream is active or interrupted, and never presents old samples as current readings.

## Later / optional

- [ ] Event overlays after checking live event availability and timing.

## Out of scope

- Recording, replay, historical downloads, session browsing, and data export.

- Therapy-setting changes, firmware updates, or mode unlocking.
- Clinical recommendations or diagnostic interpretation.
- Cloud accounts, uploads, or synchronization.

## Bluetooth implementation progress

First-pass scan, connect, in-memory pairing, encrypted RPC, and live subscription code is implemented and compiles. Protocol unit tests pass. Taso reported successful physical-phone pairing and live data reception on October 16, 2026. Initial end-to-end connectivity is demonstrated; repeated connection cycles, sustained streaming, waveform accuracy, and lifecycle acceptance checks remain open. Encrypted persisted pairing, saved-session authentication, bounded reconnect retries, and activity-scoped connection/buffer state are now implemented. Emulator recreation/storage tests pass; Taso confirmed remembered pairing, reconnect, and rotation on the phone on October 16, 2026. Radio-interruption recovery and cold-process restart remain unconfirmed. No recording has been added.
