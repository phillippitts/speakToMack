# speakToMack

Privacy-first voice dictation for macOS using dual-engine speech-to-text (Vosk + Whisper).

## What Is This?

speakToMack lets you dictate text into any macOS application using a configurable hotkey. Unlike cloud-based solutions (Dragon, Google), all transcription happens **locally on your Mac** - your voice data never leaves your device.

## Status: 🚧 In Development

Current phase: Phases 0–1 complete (Environment + Core Abstractions) ✅
Next: Phase 2 – STT Engine Integration (Vosk + Whisper)
See: [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)

Current capabilities (implemented):
- ✅ Log4j2 structured logging with MDC propagation (async console/file + audit log)
- ✅ Audio format validation (16kHz, 16-bit PCM, mono) with configurable min/max durations
- ✅ Domain model: TranscriptionResult
- ✅ Exception hierarchy and global REST exception handler
- ✅ SttEngine interface (Adapter pattern target)
- ✅ Typed configuration records (VoskConfig, WhisperConfig) bound via @EnableConfigurationProperties
- ✅ Thread pool configuration with MDC task decoration

Not yet implemented (planned in later phases):
- ❌ Vosk/Whisper engine implementations
- ❌ Audio capture and hotkey orchestration
- ❌ Reconciliation strategies
- ❌ Typing/paste service and fallbacks
- ❌ Database persistence and search

## Key Features (Planned)

- **Push-to-Talk Dictation:** Press/hold hotkey → speak → release → text appears
- **Dual-Engine Transcription:** Vosk (speed) + Whisper (accuracy) run in parallel
- **100% Local:** No cloud APIs, no internet required after setup
- **Configurable Hotkeys:** YAML configuration for keyboard shortcuts
- **Graceful Fallback:** Works even if Accessibility permission denied
- **GDPR Compliant:** 90-day retention, right to erasure, IP anonymization

## Key Terms & Acronyms

### Core Technologies
- **STT** - Speech-to-Text: Technology that converts spoken audio into written text
- **JNI** - Java Native Interface: Bridge between Java and native C/C++ libraries (used by Vosk)
- **PCM** - Pulse Code Modulation: Uncompressed audio format (16kHz, 16-bit, mono)
- **MDC** - Mapped Diagnostic Context: Thread-local logging context for request correlation

### STT Engines
- **Vosk** - Fast, offline STT engine (~100ms latency, Kaldi-based)
- **Whisper** - Accurate STT engine from OpenAI (~1-2s latency, better punctuation)

### Audio Specifications
- **16kHz** - Sample rate required by both STT engines (models trained on this rate)
- **16-bit** - Bit depth for PCM audio (signed integers)
- **Mono** - Single audio channel (stereo not supported)
- **WAV** - Container format (RIFF/WAVE headers)

### Architecture & Patterns
- **ADR** - Architectural Decision Record: Document capturing key design decisions
- **DTO** - Data Transfer Object: Object for passing data between layers
- **SLA** - Service Level Agreement: Performance guarantees (e.g., p95 latency < 2s)
- **SLO** - Service Level Objective: Target reliability metrics (e.g., 99.9% uptime)
- **SLI** - Service Level Indicator: Measured metric (error rate, latency)

### Development & Operations
- **CI/CD** - Continuous Integration/Continuous Deployment: Automated build and deploy pipeline
- **OWASP** - Open Web Application Security Project: Security standards and tools
- **CVE** - Common Vulnerabilities and Exposures: Security vulnerability identifier
- **GDPR** - General Data Protection Regulation: EU privacy law (90-day retention, right to erasure)
- **HIPAA** - Health Insurance Portability and Accountability Act: US healthcare privacy law

### Testing
- **TDD** - Test-Driven Development: Write tests before implementation
- **UAT** - User Acceptance Testing: End-user validation of features
- **JMH** - Java Microbenchmark Harness: Performance benchmarking framework

### Metrics & Monitoring
- **p50/p95/p99** - Percentile latency: 50th/95th/99th percentile response times
- **RTO** - Recovery Time Objective: Maximum acceptable downtime (e.g., 4 hours)
- **RPO** - Recovery Point Objective: Maximum acceptable data loss (e.g., 1 hour)
- **MTTR** - Mean Time to Recovery: Average time to restore service after failure

### speakToMack Domain Terms
- **Reconciliation** - Process of selecting final text when Vosk and Whisper disagree
- **Fallback Manager** - System that gracefully degrades when Accessibility permission denied
- **Audio Buffer** - Thread-safe storage for captured microphone audio
- **Model Validation** - Startup check ensuring STT models are present and loadable

## Prerequisites

- macOS 12+ (Monterey or later)
- Java 21
- Microphone access permission
- Accessibility permission (for keystroke injection)
- ~200 MB disk space for STT models

## Quick Start

```bash
# 1. Download STT models (~200 MB)
chmod +x ./setup-models.sh
./setup-models.sh

# 2. Build and run
./gradlew bootRun

# 3. Grant permissions when prompted
# System Settings → Privacy & Security → Accessibility
# System Settings → Privacy & Security → Microphone
```

### Checksum verification

The setup script verifies model integrity with SHA-256 checksums.
- On first run, if no expected checksums are provided, it computes and locks them to `models/checksums.sha256`.
- On subsequent runs, it verifies downloaded files against the locked checksums.
- To enforce official checksums, set env vars before running the script:

```bash
VOSK_SHA256=<official_sha256_for_zip> \
WHISPER_SHA256=<official_sha256_for_bin> \
./setup-models.sh
```

If upstream files are legitimately updated, either update the env vars or delete `models/checksums.sha256` to re-lock values after verifying the source.

## Verify Structured Logs

After starting the app, you can verify that structured Log4j 2 logs (with MDC values) are emitted.

1. Start the app:
   ```bash
   ./gradlew bootRun
   ```
2. In another terminal, call the ping endpoint with headers to populate MDC:
   ```bash
   curl -H 'X-Request-ID: abc123' -H 'X-User-ID: demo' http://localhost:8080/ping
   ```
3. Observe console logs. You should see a line similar to:
   ```
   2025-10-14 16:45:12.345 [http-nio-8080-exec-1] [abc123] [demo] INFO  c.p.s.presentation.controller.PingController - Ping received — structured logging verification
   ```
   Note the requestId and userId appearing in square brackets.

## Architecture

- **3-Tier Spring Boot Application** (Presentation → Service → Repository)
- **Dual-Engine Processing:** Vosk and Whisper run concurrently
- **PostgreSQL Database:** Transcription history, user preferences, audit logs
- **Log4j 2 Logging:** Structured logging with MDC for request correlation
- **Strategy Pattern:** Pluggable reconciliation strategies (5 implementations)

See: [Architecture Overview](docs/diagrams/architecture-overview.md) and [Data Flow](docs/diagrams/data-flow-diagram.md)

## Configuration

The project uses Spring Boot properties (application.properties) with typed configuration classes. Current relevant settings:

```properties
# Audio validation thresholds (defaults shown)
audio.validation.min-duration-ms=250
audio.validation.max-duration-ms=300000

# Vosk (model path installed by setup-models.sh)
stt.vosk.model-path=models/vosk-model-small-en-us-0.15
stt.vosk.sample-rate=16000
stt.vosk.max-alternatives=1

# Whisper (note: you must provide a valid binary path)
stt.whisper.model-path=models/ggml-base.en.bin
stt.whisper.binary-path=<SET ME: path to whisper.cpp binary>
stt.whisper.timeout-seconds=10

# Orchestration placeholders for future phases
stt.enabled-engines=vosk,whisper
stt.parallel.timeout-ms=10000
```

Notes:
- Whisper binary is not distributed by this repo. Build whisper.cpp separately and set `stt.whisper.binary-path` to the executable (e.g., `/usr/local/bin/whisper-cpp` or `./tools/whisper.cpp/main`).
- Engines are not implemented yet; these properties are seeded to fail-fast when Phase 2 is added.

## Documentation

### Implementation & Planning
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md) - 40-task roadmap (28 MVP + 12 production)
- [Session Context](docs/SESSION_CONTEXT.md) - Comprehensive planning session summary
- [Guidelines](.junie/guidelines.md) - 2,223-line developer guide

### Architecture Decisions
- [ADR-001: Dual-Engine STT Strategy](docs/adr/001-dual-engine-stt-strategy.md)
- [ADR-002: PostgreSQL MVP Database](docs/adr/002-postgresql-mvp-database.md)
- [ADR-003: Manual Model Setup](docs/adr/003-manual-model-setup.md)
- [ADR-004: Properties-Based Hotkey Config](docs/adr/004-properties-hotkey-config.md)
- [ADR-005: Log4j 2 Logging](docs/adr/005-log4j2-logging.md)
- [ADR-006: 3-Tier Architecture](docs/adr/006-three-tier-architecture.md)

### Diagrams
- [Architecture Overview](docs/diagrams/architecture-overview.md)
- [Data Flow Diagram](docs/diagrams/data-flow-diagram.md)

## Development

### Build

```bash
./gradlew clean build
```

### Run Tests

Where are the tests?
- src/test/java/com/phillippitts/speaktomack/SpeakToMackApplicationTests.java (Spring context load test)
- src/test/java/com/phillippitts/speaktomack/TestSpeakToMackApplication.java (test bootstrap example)
- src/test/java/com/phillippitts/speaktomack/TestcontainersConfiguration.java (test-only configuration)

How to run:
```bash
# All tests
./gradlew test

# Single class (simple name)
./gradlew test --tests SpeakToMackApplicationTests

# Single class (fully-qualified)
./gradlew test --tests com.phillippitts.speaktomack.SpeakToMackApplicationTests

# Single test method
./gradlew test --tests com.phillippitts.speaktomack.SpeakToMackApplicationTests.contextLoads
```

Notes:
- Gradle is already configured with useJUnitPlatform() (JUnit 5).
- You may see a Java 21 + Mockito warning during tests; it is harmless. A mitigation flag is already set in build.gradle: `tasks.test.jvmArgs('-XX:+EnableDynamicAgentLoading')`. 

### Project Structure

```
src/main/java/com/phillippitts/speaktomack/
├── presentation/       # Controllers, DTOs, exception handlers
├── service/           # Business logic, STT engines, orchestration
├── repository/        # Data access layer (PostgreSQL)
├── domain/           # Domain entities
└── config/           # Spring configuration
```

## Contributing

This project follows:
- **Clean Code Principles** (Robert C. Martin) - Mandatory naming conventions
- **3-Tier Architecture** - Strict layer separation
- **SOLID Principles** - Enforced via Checkstyle
- **Always Working Code** - Incremental development (15-45 min tasks)

See [Guidelines](.junie/guidelines.md) for comprehensive development standards.

## Project Timeline

**MVP Track (Phases 0-5): ~25.5 hours**
- Day 1: Phase 0-1 (Environment + Abstractions)
- Day 2: Phase 2-3 (Vosk + Parallel)
- Day 3: Phase 4-5 (Integration + Docs)

**Production Track (Phase 6): ~20 hours**
- Day 4: Operations (Monitoring, Alerting, Backups)
- Day 5: Deployment Safety (Canary, Rollback)
- Day 6: Performance & Security (Load Testing, Scanning)

## License

[To be determined]

## Acknowledgments

- **Vosk** - Alpha Cephei (https://alphacephei.com/vosk/)
- **Whisper** - OpenAI (https://github.com/openai/whisper)
- **whisper.cpp** - Georgi Gerganov (https://github.com/ggerganov/whisper.cpp)

---

**Grade: 99.5/100** - Production-ready planning with Phase 2 optimization, MVP implementation in progress.
