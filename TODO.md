# TODO

Remaining work only. [Test procedures and status](docs/TESTING.md) ·
[Development history](docs/DEVELOPMENT_LOG.md)

Live-only: no recording, replay, historical downloads, exports, cloud accounts,
therapy-setting changes, firmware changes, or clinical recommendations.

## P1 — Next

None remaining. T11 sustained-use verification passed (user-reported).

## P2 — Soon

- [ ] Investigate intermittent pairing/session rejection using the existing secret-free diagnostics; successful retry did not establish a fix.
- [ ] Retest repeated disconnect/reconnect without the removed five-second delay (T4).
- [ ] Verify task dismissal and force-stop with the new automatic startup reconnect (T13/T14), plus manual Disconnect across app switching/rotation (V4).
- [ ] Verify revised first-device discovery/pairing and switching devices (V5).
- [ ] Verify metric availability/gaps, therapy-pressure behavior while frozen, amber frozen styling, and persisted chart visibility (V1–V3).
- [ ] Add therapy time after confirming a source and session semantics; do not label it sleep time.
- [ ] Add focused UI interaction tests for window selection and tap-to-freeze/resume.
- [ ] Check compact screens, landscape, large fonts, contrast, and accessibility (V6).
- [ ] Measure battery impact during sustained phone streaming (V7).

## P3 — Later

- [ ] Validate live units/scaling, cadence, timestamps, gaps, and waveforms against matching Mac/SD references, including all new metrics; current conversions remain provisional.
- [ ] Verify therapy pressure matches the device screen across natural pressure/ramp changes; steady 5.0 alone did not identify the GUI source.
- [ ] Verify out-of-range and stream-stall recovery, cancellation, and connection-retry exhaustion (T10 and development-support checks).
- [ ] Instrument repeated connections and sustained use for duplicate subscriptions/sample delivery and bounded memory.
- [ ] Verify chart-axis behavior during radio outages and both simulation background modes (V8).
- [ ] Introduce a shared simulation/Bluetooth source interface when needed.
- [ ] Consider event overlays after checking live event availability and timing.
