# Testing

Use [GitHub Issues](https://github.com/taso42/airwave-android/issues) for priorities and [Development log](DEVELOPMENT_LOG.md)
for investigation history. Check a procedure only after its expected result is
observed. A prior pass is evidence for that build, not a blanket guarantee for
all later builds. Never record pairing codes or credentials.

## Latest verification summary

| Area | Latest evidence / remaining check |
|---|---|
| Short radio interruption, cancel, timeout | T1–T3 passed on phone |
| Repeated connections | T4 passed with the old five-second delay; full sequence pending after removal |
| Background off/on, lock and app switch | T5–T8 passed; simulation background behavior also passed |
| Foreground notification | T9 passed |
| Out of range | T10 skipped; P3 |
| Sustained use | T11 passed, user-reported October 17, 2026 |
| Task dismissal / force-stop | Original T13/T14 passed; procedures below updated for automatic reconnect |
| Fresh-launch reconnect | User confirmed pass on October 17, 2026 |
| Pairing | Latest pairing and reconnect succeeded; intermittent rejection remains unexplained |
| Charts | 5/15/60-minute windows and tap-to-freeze/resume confirmed; metric null gaps pending |
| Appearance | New icon and metric cards approved; amber frozen styling awaits explicit confirmation |
| Therapy pressure | Mac probe matched steady 5.0; status stays live by design, phone freeze check pending |
| Chart preferences | Persistence implemented; fresh-launch persistence check pending |
| Sample model and independent refresh | Combined merged build: user reported things look fine on Pixel 9a, October 17, 2026. General phone smoke check passed; specific outstanding procedures below remain unverified. |
| Automated checks | Builds/unit tests passed; parser, lifecycle, service and UI tests run during development; see log for scope |

## Connection and lifecycle procedures


These are the manual acceptance steps for the open items above. Check an item only after its expected result is observed; record failures below it. Visual checks do not establish sample-level correctness or bounded memory.

### Setup

Turn simulation off, connect to the powered AirSense, select **60 sec**, and leave the chart unfrozen. Enable **Keep streaming in background** for radio tests so opening Android controls does not intentionally pause the connection. A powered machine is enough for connection checks; use your normal mask setup when evaluating breathing waveforms. Return to this starting state before each independent test.

- [x] **P1 · T1 — Short Bluetooth interruption:** Turn the phone's Bluetooth off for about 5 seconds, then on. Expect Disconnected, automatic recovery without another pairing code, then Live. Settings should show reconnect progress. New samples should reveal a gap if the earlier samples are still within the displayed window. Allow up to a minute for recovery; record elapsed time and any error.
- [x] **P1 · T2 — Cancel recovery:** While connected, turn Bluetooth off. In Airwave Settings, tap Cancel while reconnecting. Restore Bluetooth and wait 30 seconds. Expect no automatic reconnection or incoming samples. Tap Connect; expect recovery without a pairing code.
- [x] **P1 · T3 — Radio-off recovery timeout:** Connect, then disable Bluetooth for 90 seconds. Expect waiting to stop after about 60 seconds; inspect Settings to confirm rather than relying only on elapsed time. Restore Bluetooth. Expect it to remain disconnected until Connect is tapped, then recover without a pairing code.
- [ ] **P2 · T4 — Repeated connections (retest after delay removal):** Perform Disconnect → Connect five times. Each Disconnect should stop updates; each Connect should resume without a code. Watch for errors or visibly accelerated/duplicated waveforms. Duplicate sample delivery still needs instrumented verification beyond this visual check.
- [x] **P1 · T5 — Screen lock, background off:** Disable background streaming, connect, lock for 15 seconds, then return. Expect reconnection and a visible gap after new samples arrive.
- [x] **P1 · T6 — App switching, background off:** With background streaming off, connect, switch to another app for 15 seconds, then return. Expect reconnection and a gap.
- [x] **P1 · T7 — Screen lock, background on:** Enable background streaming, connect, lock for 15 seconds, then return. Expect continued streaming without an intentional pause or reconnection gap.
- [x] **P1 · T8 — App switching, background on:** With background streaming on, connect, use another app for 15 seconds, then return. Expect continued streaming without an intentional pause or reconnection gap.
- [x] **P1 · T9 — Notification visibility:** Check Airwave's notification permission and channel settings in Android app settings. Enable notifications, toggle background streaming off/on, and start simulation or connect. Expect an ongoing notification in the notification shade; a sound or banner is not required. If permissions/channels are enabled but no notification appears, record it as a failure.
- [ ] **P3 · T10 — Out-of-range recovery (skipped for now):** While connected with background streaming on, walk away with the phone until the link actually drops. Return within range before retries are exhausted. Expect automatic reconnection without a code and a gap. If retries have stopped, use Connect. Record which path occurred; do not mark automatic recovery verified if only manual Connect worked.
- [x] **P1 · T11 — Sustained normal use:** During normal use, stream for 30–60 minutes. Check connection status periodically and confirm charts and Settings remain responsive. Record duration, interruptions, and approximate battery change. Instrumented memory checks are tracked separately at P3. Passed, user-reported October 17, 2026; exact duration and battery change were not supplied.

### Additional lifecycle checks

- [ ] **P2 · T13 — Task dismissal (updated startup behavior):** With background streaming on and the device connected, swipe Airwave away from Android's recent-apps screen. Expect the service/notification to stop. Reopen Airwave; expect automatic reconnect with saved pairing, without another code. The original stop/service behavior passed; this combined sequence needs retest after startup reconnect was added. Confirm actual Bluetooth shutdown separately if UI observations are inconclusive.
- [ ] **P2 · T14 — Cold restart (updated startup behavior):** While disconnected, force-stop Airwave through Android app settings, then reopen it. Expect retained pairing and automatic reconnect without another code. The earlier manual-connect variant passed; force-stop with automatic reconnect needs verification. This checks a cold restart; it does not simulate every OS process-death condition.

### Checks to run with development support

- [ ] **P3 — Stream-stall recovery:** Use a controlled test to stop sample delivery while preserving the link. Verify the watchdog and retry limit, plus cancellation during recovery. Bluetooth-off testing alone does not cover this condition.
- [ ] **P3 — Sample delivery and memory:** Instrument repeated connection cycles and a sustained run to verify no duplicate delivery and bounded memory.
- [ ] **P3 — Waveform comparison:** Arrange live Android data and a matching Mac or SD reference; compare units, cadence, timestamps, gaps, and waveforms. Existing historical SD comparisons do not validate this Android live path.


## Current display and settings checks

- [ ] **V1 — Metric availability and gaps:** During normal therapy, confirm readings and graphs appear. In standby or during missing data, expect unavailable readings and breaks rather than fabricated zeros. Disconnect and confirm the time axis continues scrolling retained history.
- [ ] **V2 — Frozen presentation:** Tap a chart/card. All graphs and cards should freeze with amber outlines and pause icons. Therapy pressure in live status should keep updating. Tap again to catch up without an artificial gap. Repeat with another window selected.
- [ ] **V3 — Saved chart visibility:** Disable an additional chart in Settings. Close and reopen the app; expect that chart to remain hidden. Re-enable it and repeat. Fresh defaults are all four enabled.
- [ ] **V4 — Manual Disconnect:** Disconnect, switch apps, return, then rotate. Expect no reconnection. A fresh app launch should reconnect once using saved pairing.
- [ ] **V5 — Discovery and device switching:** Verify the revised first-device flow and switching to a different device on a phone; prior pairing success alone does not cover the full flow.
- [ ] **V6 — Layout/accessibility:** Check compact screens, landscape, large fonts, contrast, and announced names/actions.
- [ ] **V7 — Battery:** Record approximate battery change over a sustained run with duration and background mode.
- [ ] **V8 — Axis variations (P3):** Check radio outages and both simulation background modes; gaps should scroll with elapsed time.

T12 was deliberately removed; do not treat its absence as a pass. Exact metric
scaling and screen-pressure equivalence require matched reference observations
(P3), not visual plausibility alone. Record new results here concisely; retain
investigation narratives in the development log.

### V6 partial verification — 2026-10-17

Issue #11 accessibility branch, emulator API 37.2:

- Debug build and unit tests pass; two Compose accessibility tests pass for named,
  toggleable Settings switches and the reading's frozen state/resume action.
- Inspected 320 × 640 dp portrait and 640 × 320 dp landscape at 200% font scale.
  Readings stack in portrait; values/units remain readable, Settings labels wrap
  without displacing switches, and charts/time-window controls remain reachable
  by scrolling. Restored emulator dimensions, density, font scale and rotation.
- Calculated contrast against the actual dark backgrounds: mint readings 9.29:1,
  blue readings 7.79:1, chart axis labels 10.34:1, disconnected status 6.47:1.
  This is a targeted palette check, not a full accessibility certification.
- V6 remains open for hands-on TalkBack announcement/navigation verification and
  real-phone large-font review. No Bluetooth or physical-phone testing performed.
