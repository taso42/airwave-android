# Recent Bluetooth history investigation (#23)

Status, October 17, 2026: **offline prototype; no automatic download or chart integration**. [Issue #23](https://github.com/taso42/airwave-android/issues/23) remains open. Existing live behavior is unchanged. No phone or AirSense connections were made for this investigation.

## Supported first slice

| Chart | Archive selector | Original cadence | Prototype units |
|---|---|---|---|
| Flow | `RespiratoryFlow6p25Hz` | 160 ms / 6.25 Hz | RC03 physical value × 60 → L/min |
| Mask pressure | `MaskPressure6p25Hz` | 160 ms / 6.25 Hz | RC03 physical value → cmH₂O |
| Leak | `Leak0p5Hz` | 2,000 ms / 0.5 Hz | RC03 physical value × 60 → L/min |

`InspiratoryPressure0p5Hz` is excluded: it must not silently replace either mask pressure or the live therapy-pressure reading. `TherapyOneMinutePeriodic` has candidate respiratory-rate and minute-ventilation fields, but these are separately aggregated/quantized measurements and need independent verification. No supported tidal-volume history is established here. The prototype does not fill unavailable channels or upsample history to 25 Hz. Scaling follows the existing upstream decoder; decoder parity is not independent physical calibration.

## Implemented and verified offline

- A bounded DataDelivery protobuf / RC03 decoder returns separate channel blocks with original device timestamps and cadence. It rejects wrong channels, unsupported cadence, malformed/truncated payloads, and oversized rounds/sample counts. It preserves block boundaries and does not join or interpolate across gaps.
- A transport-independent coordinator requests one channel at a time, clips to an exclusive device-time endpoint and the preceding hour, verifies contiguous fragment sequence numbers and SHA-256, follows strictly advancing cursors, and retains earlier verified results if a later round fails.
- One coordinator instance permits only one active download. Each channel has a 64-round / 60-second budget; each round has a 4,096-byte / 32-fragment limit. Parent cancellation and timeout propagate and release the single-download guard. The coordinator has no Android/Bluetooth adapter and no persistent storage.
- Synthetic JVM tests cover all three channels, scaling, single seed / Rice reconstruction, disjoint blocks, truncation, byte limits, wrong channels, empty history, sequence/hash failures, exact window clipping, partial failure, repeated cursors, bounded continuation, cancellation, and overlapping calls.
- Local comparison with the pinned Python decoder passed for the first stored chunk of each supported channel: 2,841 flow, 2,701 pressure, and 5,026 leak samples (10,568 total). Neither those private captures nor their temporary parity harness are included in the repository.

## Why the automatic feature is not enabled yet

The existing Mac exporter replaces the notification handler while downloading, so its successful history downloads do **not** prove concurrent live/spool delivery. The Android receiver currently handles only `StreamData`; it needs independent `SpoolFragment` routing without changing packet freshness or retrying a healthy live connection when backfill fails. RPC writes must be serialized, and fragment queues and allocations must be bounded at ingress, not only after reception. Unknown terminal statuses must fail the history operation. Both spool RPCs use JSON-RPC `1.0`; the current Android RPC helper defaults non-stream methods to `2.0`.

Earlier offline comparisons against SD data found block-dependent alignment offsets, approximately 0–1.2 seconds. This does not establish a Bluetooth live-versus-history offset, and is not a basis for applying a fixed correction. A shared UTC timestamp representation alone does not prove matching waveform phase. Original timestamps should be retained, and history/live transitions should be explicit segment boundaries until verified.

`Sample` currently requires both flow and mask pressure at each timestamp; archive selectors return independently bounded records and leak has a different cadence. Pairing by index, carrying the last leak value indefinitely, or generating synthetic 25 Hz historical samples would misrepresent data. `chartPoints` also treats differences above 120 ms as outages, so all 160 ms archive samples would currently become separate segments. This must be changed through a channel-aware model, not by merely widening the threshold globally and hiding real live gaps.

## Planned integration after the device probe

1. Start the live stream first. Anchor the hour query to a fresh **device** timestamp, not phone wall time. Use one cancellable job per connection generation; cancel and discard late results on disconnect, device switch, simulation, or device-clock reset. Reconnect can requery the bounded hour and deduplicate it rather than assuming the prior transfer finished.
2. Route spool notifications by spool ID while live notifications continue normally. Pace fragment pulls and observe packet arrival delays. Stop optional history acquisition if it interferes with live acquisition; preserve the live stream and any verified partial history. There is no verified spool-stop RPC here: bounded pulls plus local cancellation must be checked on device.
3. Retain per-channel observations keyed by device identity, device timestamp, and acquisition kind/cadence. Merge sorted data deterministically and deduplicate repeated archive records. Prefer live **coverage intervals**, not just exactly equal timestamps, because 25 Hz and 6.25 Hz grids can be offset. Do not bridge source transitions, original archive block gaps, or missing/null data. A gap is not proof of therapy-off state.
4. Keep a stable device-time origin when older samples arrive. Maintain the rolling one-hour bound as live time advances, including a count cap sized for mixed cadence. The prototype only bounds acquisition and returns blocks; it does not yet own this retained chart model. Immutable frozen snapshots must remain unchanged while backfill arrives; resume should reveal the merged present view. History must never update live numeric cards, connection freshness, or the “25 Hz” live-status measurement. Historical portions need an explicit lower-rate/source cue.
5. Add merge/render tests for offset sample grids, overlapping retries, live precedence, source changes, clock jumps, mixed cadence and missing-channel segments, and frozen snapshots before enabling automatic acquisition.

## Focused device probe still needed

With Airwave/MyAir disconnected and permission to use the machine:

- Establish live delivery, then request one bounded 4 KiB spool round with paced fragment pulls. Measure live packet arrival intervals before/during/after; verify the live stream survives success, failure, and cancellation.
- Verify `fromDateTime` selection when the requested hour starts inside an archive block. The documentation says older records are skipped; a prefix hole may require a bounded lookback, not fabricated samples. Verify the returned continuation advances on this firmware.
- Compare overlapping live/history timestamps and values for each channel during therapy; check whether the current unfinished archive block is available or only completed blocks are returned.
- Disconnect mid-transfer, reconnect, and check cancellation, spool lifetime, retry overlap, and that fresh live data remains available even when history does not.

These checks are separate from #9's session-start recovery; this feature adds recent in-memory chart context only, not recording, export, or a historical browser.

## Sources

Protocol and decoder reference: [airbreak-plus at d77c727](https://github.com/m-kozlowski/airbreak-plus/tree/d77c727a5d38649da2853f3e1bc91a538204171f), especially `python/lib/as11_spool.py`, `docs/as11/rpc_spools.md`, and `docs/as11/rpc_protocol.md`. Local prior exporter: `airsense11/history_export.py` in the separate Mac exploration project. Third-party attribution is retained in `THIRD_PARTY_NOTICES.md`.
