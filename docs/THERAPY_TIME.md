# Therapy time: source investigation

Status: investigation only, October 17, 2026. [Issue #9](https://github.com/taso42/airwave-android/issues/9) remains open. No timer or device subscription has been added, and no live probe was performed for this investigation.

## Findings

Airwave currently requests seven sampled channels: `_RFL`, `_MKP`, `_LKF`, `_RR2`, `_TD2`, `_MV2`, and `_INP`. None supplies elapsed therapy time. The receive path handles `StreamData`; adding event subscriptions requires separate event parsing and state handling.

The locally pinned airbreak-plus source provides promising **candidates**, not confirmation on this AirSense firmware:

| Candidate | Evidence | Limitation |
|---|---|---|
| `SubscribeEvent`: `UsageEvents-TherapyStatusEvents` | Upstream lists `TherapyStart`, `TherapyStop`, `MaskOn`, `MaskOff`, `PowerOff`, `MaskFitStart/Stop`, and `LearnTargetsStart/Stop`. | The reference identifies firmware 15.8.4.0; this project's reported AirSense application version is 18.8.7.0. Subscription acceptance, actual payloads, timestamps, and delivery guarantees need a device check. |
| `Get` / DataItem subscription: `FGState` (`_ZRM`), `StatusEvent` (`_THS`) | Names appear in the upstream RPC variable registry. DataItem subscriptions can return an initial value followed by changes. | Current-state meanings, freshness, and availability on this firmware remain unverified. A current therapy state alone does not recover the session start. |
| Internal tags `THD` / `OND` | Upstream's cross-device variable reference describes Therapy Duration / Mask On Duration and EDF summary mappings. | These are not proof of AirSense 11 live RPC selectors, units, or current-session counters. Do not invent `_THD` / `_OND` stream support from those names. |
| Last therapy use / summary statistics | Upstream includes `LastTherapyUseDateTime` and summary streaming IDs. | A last-use timestamp or summary is not a verified current-session start or elapsed-time counter. Historical spooling is outside this app's live-only scope. |

A BLE connection, packet timestamp, accumulated sample count, or nonzero flow is **not** a therapy clock. The standby investigation observed advancing packets carrying constant flow. Counting connection time would therefore include standby; counting received samples would undercount outages and background pauses.

## Proposed display semantics (pending source validation)

“Therapy time” means elapsed time in the **current uninterrupted therapy run**, not sleep time, daily usage, mask-on time, or time connected to Airwave. Validate this definition against observed start/stop behavior before implementing it.

- Prefer a device-provided elapsed counter or authoritative current-session start/state pair, if discovered and verified. A reconnect can resume only when that source establishes the same session.
- With events alone, an observed `TherapyStart` establishes a known start. An observed `TherapyStop` ends that run; the next confirmed start resets it. Duplicate start events must not reset a running timer. A conflicting/out-of-order event makes state uncertain until reconciled.
- Joining mid-therapy without an authoritative start/counter shows unavailable, not `00:00` or time since connection. Missing the start is not zero duration.
- After a disconnect, app background pause, process restart, or missed-event interval, do not assume therapy continued or that no stop/start occurred. Mark unavailable until a verified current-session source reconciles continuity or a new start is observed. Persisting a phone start timestamp cannot establish continuity.
- Standby, mask fit, warmup, and learn-targets modes do not start the timer. Determine how `MaskOff` relates to `TherapyStop`; do not silently equate mask-off with therapy-off.
- Use device time for session identity/boundaries when validated and monotonic phone time for interpolation between verified updates. Clock changes, counter decreases, and device reboot require reconciliation. A date/noon boundary must not reset a current-run timer unless device evidence establishes that behavior.
- Keep only current live state in memory. Do not introduce session history, recording, or retrospective spool downloads. Chart freeze must not alter session accounting; whether the displayed timer freezes with cards is a separate presentation choice.

If events cannot establish a truthful duration after ordinary reconnects, defer the display instead of substituting a silently incomplete stopwatch.

## Next read-only device investigation

Run only one BLE client at a time, with the user's normal machine setup. Start/stop and mask-fit actions below are manual on-device actions, never remote therapy-setting commands. Keep raw captures in ignored private storage; publish only sanitized findings and synthetic fixtures.

1. Record firmware version. Subscribe to `UsageEvents-TherapyStatusEvents` and require its `valid: true` response. Capture event method, label, device timestamp, and monotonic arrival time; establish the actual payload schema rather than assuming it matches `StreamData`.
2. Read `FGState` and `StatusEvent` independently; record rejection/null as an unsupported candidate. If supported, inspect their DataItem initial notifications and changes. Determine whether either provides session identity/start information or only state.
3. Observe standby → normal therapy → stop → second therapy start. Compare elapsed time against a stopwatch; determine units, event boundaries, duplicates, ordering, and whether any counter resets per run or therapy day.
4. Join an already-running therapy session. Determine whether initial state includes a recoverable start/counter; event-only notification of future changes is insufficient.
5. Disconnect/reconnect while therapy continues, then repeat with a stop/start during the disconnected interval. Verify the client can distinguish these cases before permitting timer continuation.
6. Separately observe mask fit, mask removal, and machine restart when convenient. Record whether therapy events/state actually distinguish them. Do not infer sleep or mask use from waveform values.
7. Only after event support is confirmed, validate coexistence with the existing seven-channel stream and re-subscription on each new RPC connection. Unsupported timer features must not prevent the existing dashboard from streaming.

The upstream CLI supports `subscribe UsageEvents-TherapyStatusEvents --duration 60`. The project's restricted Mac launcher does **not** expose a subscribe action; a follow-up probe should extend that launcher or deliberately reuse its private credential configuration, rather than bypassing it and creating a second credential store.

Before an implementation PR can close #9, record actual firmware/source evidence and add synthetic tests for normal start/stop, duplicate/out-of-order events, joining mid-run, reconnect with and without an intervening restart, background pauses, process recreation, unsupported source, and device clock/counter discontinuities. No Android build is required for this documentation-only investigation.

## Sources inspected

All upstream references below are pinned to the Mac exploration project's existing airbreak-plus commit `d77c727a5d38649da2853f3e1bc91a538204171f`; no upstream dependency was changed.

- [Event selectors and firmware scope](https://github.com/m-kozlowski/airbreak-plus/blob/d77c727a5d38649da2853f3e1bc91a538204171f/docs/as11/rpc_events.md)
- [RPC registry: variables, stream IDs, and event labels](https://github.com/m-kozlowski/airbreak-plus/blob/d77c727a5d38649da2853f3e1bc91a538204171f/python/lib/as11_rpc_vars.py)
- [Subscription CLI and connection lifetime](https://github.com/m-kozlowski/airbreak-plus/blob/d77c727a5d38649da2853f3e1bc91a538204171f/docs/tools/as11_config.md)
- [Cross-device variable reference](https://github.com/m-kozlowski/airbreak-plus/blob/d77c727a5d38649da2853f3e1bc91a538204171f/docs/var_reference.tsv)
- [Current Android subscription and notification routing](../app/src/main/java/com/vibex/airwave/ble/AirSenseClient.kt)
