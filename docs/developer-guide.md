# Developer Guide

This guide helps contributors understand the architecture, patterns, testing strategy, and contribution flow for speakToMack.

## Architecture Overview
- 2‑tier layering: Presentation → Service (no reverse deps; Data layer planned for Phase 6)
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

## Event Threading & Responsiveness

The application uses Spring's event-driven architecture to keep the UI responsive during long-running operations.

**Key Pattern: Async Event Handling**
- Hotkey events are handled synchronously for immediate capture start/stop
- Transcription processing is offloaded to background threads using `@Async` and a configured executor
- This prevents blocking the hotkey listener thread during STT engine calls (which can take seconds)

**Implementation:**
```java
@Component
public class DualEngineOrchestrator {

    @EventListener
    public void onHotkeyPressed(HotkeyPressedEvent event) {
        // Synchronous: immediate response to start audio capture
        captureService.startSession();
    }

    @EventListener
    @Async("sttExecutor")  // Offload to background thread pool
    public void onHotkeyReleased(HotkeyReleasedEvent event) {
        // Asynchronous: transcription can take 1-5 seconds
        // Runs on sttExecutor thread pool, not hotkey listener thread
        byte[] audio = captureService.stopAndRead();
        TranscriptionResult result = sttEngine.transcribe(audio);
        eventPublisher.publishEvent(new TranscriptionCompletedEvent(result));
    }
}
```

**Thread Pool Configuration:**
- `sttExecutor` thread pool is configured in `AppConfig`
- Pool size matches STT engine concurrency limits to prevent resource exhaustion
- Callers should never block event listener threads with long-running STT operations

## Paragraph Break Semantics

The application automatically inserts paragraph breaks (newlines) within transcriptions when silence gaps exceed the configured threshold.

**Architecture:**
- Pause detection happens **within each STT engine** by analyzing the audio/transcription data
- **Vosk:** Uses Voice Activity Detection (VAD) with RMS amplitude analysis on PCM audio to detect silence periods
- **Whisper:** Uses segment timestamps from JSON output to identify silence gaps between spoken segments
- Both engines insert newlines (`"\n"`) directly into the transcription text when silence exceeds the threshold
- Configuration: `stt.orchestration.silence-gap-ms` (default: 1000ms / 1 second)

**Implementation Details:**
- **VoskSttEngine:** `AudioSilenceDetector` analyzes PCM amplitude; when consecutive silence frames exceed threshold, a newline is prepended to subsequent text
- **WhisperSttEngine:** `WhisperJsonParser.extractTextWithPauseDetection()` calculates time gaps between segment timestamps; newlines inserted at pause boundaries
- Whisper JSON mode must be enabled (`stt.whisper.output=json`) for pause detection to work

**Consumer Expectations:**
- Downstream consumers (typing adapters, UI displays) receive `TranscriptionResult` with **embedded newlines** when pauses are detected
- Consumers must handle newlines gracefully (e.g., `RobotTypingAdapter` types newlines as keystrokes, creating paragraph breaks)
- No post-processing or stripping of newlines should occur - they are intentional formatting from the STT engines

**Configuration:**
```properties
# Silence gap threshold for automatic paragraph breaks (milliseconds)
# Vosk: Uses Voice Activity Detection (VAD) to detect silence in PCM audio
# Whisper: Uses segment timestamps from JSON output (requires stt.whisper.output=json)
# Set to 0 to disable. Default: 1000 (1 second)
stt.orchestration.silence-gap-ms=1000
```

**Example Flow:**
1. User dictates "Hello world" → pauses 1.5 seconds → "New paragraph"
2. Vosk/Whisper detects the 1.5-second silence gap (exceeds 1000ms threshold)
3. Engine returns transcription text: `"Hello world\nNew paragraph"` (embedded newline)
4. `TranscriptionResult` published with text containing the newline
5. Typing adapter outputs the text with newline, creating a paragraph break in the document

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

### Dependencies
The project uses the following key dependencies:

**Logging:**
- Log4j2 (`spring-boot-starter-log4j2`) replaces the default Logback
- LMAX Disruptor (`com.lmax:disruptor:3.4.4`) for async logging performance
- Configuration excludes `spring-boot-starter-logging` in favor of Log4j2

**STT Engines:**
- Vosk (`com.alphacephei:vosk:0.3.38`) - Offline speech-to-text engine
- JNA (`net.java.dev.jna:jna:5.13.0`) - Required by Vosk for native library access
- Note: Vosk 0.3.45 has native library issues on macOS; 0.3.38 is stable

**Code Quality:**
- Checkstyle plugin (`checkstyle`) enforces coding standards
- Configuration: `config/checkstyle/checkstyle.xml`
- Build fails on any violations (`maxWarnings = 0`)
- Checkstyle version: 10.12.0

**Other Dependencies:**
- JNativeHook (`com.github.kwhat:jnativehook:2.2.2`) - Global hotkey support
- org.json (`org.json:json:20231013`) - JSON parsing for Vosk responses
- Awaitility (`org.awaitility:awaitility:4.2.0`) - Async testing support

### Build Commands
```bash
./gradlew clean build           # Full build with tests and Checkstyle
./gradlew check                 # Run tests + Checkstyle + integration tests
./gradlew test                  # Unit tests only (excludes integration/real-binary tests)
./gradlew integrationTest       # Integration tests only
./gradlew voskIntegrationTest   # Vosk model integration tests
./gradlew realBinaryTest        # Tests requiring real binaries/hardware
./gradlew bootRun               # Run the application
```

### Running the Application
```bash
./gradlew bootRun
```
- Dev metrics at `/actuator/metrics`; production profile restricts Actuator to health/info
- Application runs with `-Djava.awt.headless=false` for Robot/Clipboard API support

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

## Roadmap / Future Work

The following features are planned but not yet implemented:

**Phase 6 - Production Hardening:**
- **Database persistence:** PostgreSQL integration for transcription history (currently no database)
- **Security hardening:** Actuator authentication, TLS, OWASP scans, PII redaction
- **GDPR compliance:** Data retention policies, automated deletion, backup/restore procedures
- **Modulith architecture:** Refactor to Spring Modulith for better bounded contexts
- **Advanced monitoring:** Distributed tracing with OpenTelemetry/Jaeger
- **Resilience:** Circuit breakers with Resilience4j
- **Dependency management:** Dependency locking, Dependabot/Renovate
- **Streaming dictation:** Real-time transcription beyond whole-buffer MVP

See `docs/IMPLEMENTATION_PLAN.md` Phase 6 for detailed task breakdown.
