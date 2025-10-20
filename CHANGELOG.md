# Changelog

All notable changes to this project will be documented in this file. The format is based on Keep a Changelog, and this project adheres to Semantic Versioning as it evolves.

## [v0.5.0] - 2025-10-20
### Added
- Phase 4 complete: Dual‑engine reconciliation path (feature‑flagged via `stt.reconciliation.enabled`).
- Parallel STT execution service (Vosk + Whisper) with timeout and graceful partial results.
- Transcript reconciler strategies: `simple`, `confidence`, `overlap` (Jaccard). Configurable via `stt.reconciliation.strategy` and `stt.reconciliation.overlap-threshold`.
- Whisper JSON output toggle (`stt.whisper.output=json`) with safe JSON parsing and token extraction for improved overlap‑based reconciliation. Default remains `text`.
- PII‑safe metrics (Micrometer):
  - `stt.engine.latency.ms{engine=}` timer
  - `stt.engine.success_total{engine=}`, `stt.engine.failure_total{engine=,reason=}` counters
  - `stt.reconcile.strategy_total{strategy=}`, `stt.reconcile.selected_total{engine=}` counters
- Production profile limiting Actuator exposure to `health,info` only.
- Phase 5 documentation set (initial): User, Operator, Developer, Reconciliation guides; runbooks for engine failures and permissions/hotkeys.

### Changed
- README updated to reflect Phases 0–4 completion, current capabilities, and pointers to the new docs.

### Notes
- Default behavior remains single‑engine routing (watchdog‑aware). Enable reconciliation explicitly to use dual‑engine path.
- Logs and metrics avoid PII by design: INFO logs include only durations/character counts; DEBUG may include truncated previews.

[v0.5.0]: https://github.com/your-org/speakToMack/releases/tag/v0.5.0