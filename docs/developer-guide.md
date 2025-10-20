# Developer Guide

This guide helps contributors understand the architecture, patterns, testing strategy, and contribution flow for speakToMack.

## Architecture Overview
- 3‑tier layering: Presentation → Service → Data (no reverse deps)
- Event-driven orchestration (Spring ApplicationEvents)
- Strategy/Factory/Adapter patterns:
  - Strategy: TranscriptReconciler, HotkeyTrigger, TypingAdapter
  - Factory: HotkeyTriggerFactory
  - Adapter: VoskSttEngine (JNI), WhisperSttEngine (process)
  - Observer: Hotkey/Typing/Error/Watchdog events

See diagrams: `docs/diagrams/architecture-overview.md`.

## Key Components
- Hotkeys: `service.hotkey.*`
- Capture: `service.audio.capture.*`
- Engines: `service.stt.*` (vosk/whisper)
- Parallel execution: `service.stt.parallel.*`
- Reconciliation: `service.reconcile.*`
- Orchestrator: `service.orchestration.DualEngineOrchestrator`
- Fallback typing: `service.fallback.*`
- Watchdog: `service.stt.watchdog.*`

## Configuration Properties (typed)
- `config.audio.AudioCaptureProperties`
- `config.validation.AudioValidationProperties`
- `config.hotkey.HotkeyProperties` (+ `TriggerType`)
- `config.typing.TypingProperties`
- `config.stt.{VoskConfig, WhisperConfig, SttConcurrencyProperties, SttWatchdogProperties}`
- `config.orchestration.OrchestrationProperties`
- `config.reconcile.ReconciliationProperties`

## Testing Strategy
- Hermetic by default: all OS / native integrations behind seams
  - GlobalKeyHook → tests inject fake implementation
  - Java Sound DataLineProvider → fake TargetDataLine
  - Whisper ProcessFactory → fake Process with controlled stdout/stderr/exit
  - Robot/Clipboard facades → fakes in unit tests
- Gated local tests: real models/binary can be run locally via system properties/tags (keep off in CI)
- Examples:
  - `HotkeyTriggerTests`, `HotkeyManagerTest`
  - `JavaSoundAudioCaptureServiceTest`, `PcmRingBufferTest`
  - `DefaultParallelSttServiceTest`, `DefaultParallelSttServiceTimeoutTest`
  - `ReconcilerStrategiesTest`, `DualEngineOrchestratorReconciledTest`
  - `WhisperJsonParserTest`, `WhisperProcessManagerJsonTest`, `WhisperSttEngineJsonModeTest`
  - Fallback: `StrategyChainTypingService*`, `ClipboardTypingAdapterTest`

## Coding Standards
- Java 21, Spring Boot 3.5.x
- Clean Code principles enforced via Checkstyle (build fails on warnings)
- Constructor injection (no field injection)
- Public API Javadoc (interfaces and widely used components)
- Privacy: never log full transcripts at INFO; use `LogSanitizer.truncate()` for previews at DEBUG

## Build & Run
```bash
./gradlew clean build
./gradlew bootRun
```
- Dev metrics at `/actuator/metrics`; production profile restricts Actuator to health/info

## Contribution Flow
1. Create a small, independently testable task (feature or doc).
2. Keep changes minimal and hermetic; add unit tests.
3. Run `./gradlew check` locally (Checkstyle + tests).
4. Update docs as needed (README + relevant guide).
5. Submit PR with succinct description and references to plan tasks.

## Troubleshooting for Developers
- JNI/Whisper issues: run `./setup-models.sh` and `./build-whisper.sh`; ensure macOS quarantine removed (`xattr -dr com.apple.quarantine tools/whisper.cpp/main`).
- Hotkeys on macOS: verify Accessibility permission; avoid OS-reserved combos.
- Audio capture: confirm microphone permission; device selection via `audio.capture.device-name` when needed.

## Future Work (Phase 6)
- Security hardening (Actuator auth, TLS, OWASP scans)
- Dependency locking, Dependabot/Renovate
- Streaming dictation (beyond whole-buffer MVP)
