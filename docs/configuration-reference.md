# Configuration Reference

Complete reference for all configuration properties in speakToMack.

> **Location:** All properties are configured in `src/main/resources/application.properties`

## Table of Contents

- [Audio Configuration](#audio-configuration)
  - [Audio Validation](#audio-validation)
  - [Audio Capture](#audio-capture)
- [STT Engine Configuration](#stt-engine-configuration)
  - [Vosk Configuration](#vosk-configuration)
  - [Whisper Configuration](#whisper-configuration)
  - [Engine Orchestration](#engine-orchestration)
  - [Reconciliation (Phase 4)](#reconciliation-phase-4)
  - [Concurrency Limits](#concurrency-limits)
  - [Engine Watchdog](#engine-watchdog)
- [Hotkey Configuration](#hotkey-configuration)
- [Typing Configuration](#typing-configuration)
- [Spring Boot Actuator](#spring-boot-actuator)

---

## Audio Configuration

### Audio Validation

Controls audio validation rules for minimum and maximum recording duration.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `audio.validation.min-duration-ms` | long | `250` | Minimum audio duration in milliseconds. Clips shorter than this are rejected to avoid accidental hotkey taps. |
| `audio.validation.max-duration-ms` | long | `300000` | Maximum audio duration in milliseconds (5 minutes). Prevents unbounded memory usage and processing time. |

**Example:**
```properties
audio.validation.min-duration-ms=250
audio.validation.max-duration-ms=300000
```

---

### Audio Capture

Controls the audio capture service behavior.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `audio.capture.chunk-millis` | int | `40` | Audio buffer chunk size in milliseconds. Smaller values = lower latency but higher CPU usage. |
| `audio.capture.max-duration-ms` | long | `60000` | Maximum recording duration (60 seconds). Hard limit to prevent unbounded capture sessions. |
| `audio.capture.device-name` | String | (system default) | Optional: Specific audio input device name. If not set, uses system default microphone. |

**Example:**
```properties
audio.capture.chunk-millis=40
audio.capture.max-duration-ms=60000
# audio.capture.device-name=Built-in Microphone
```

---

## STT Engine Configuration

### Vosk Configuration

Configures the Vosk STT engine (fast, JNI-based, offline).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.vosk.model-path` | String | `models/vosk-model-en-us-0.22` | Path to Vosk model directory. Must contain `am/`, `graph/`, `rescore/` subdirectories. |
| `stt.vosk.sample-rate` | int | `16000` | Audio sample rate in Hz. Must match model requirements (typically 16000 or 8000). |
| `stt.vosk.max-alternatives` | int | `1` | Maximum number of recognition alternatives to generate. Higher values increase processing time. |

**Example:**
```properties
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.vosk.sample-rate=16000
stt.vosk.max-alternatives=1
```

**Supported Models:**
- `vosk-model-en-us-0.22` (1.8GB, high accuracy) **← Current default**
- See [Vosk Models](https://alphacephei.com/vosk/models) for more options

---

### Whisper Configuration

Configures the Whisper STT engine (accurate, process-based, offline).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.whisper.binary-path` | String | `tools/whisper.cpp/main` | Path to whisper.cpp binary executable. |
| `stt.whisper.model-path` | String | `models/ggml-base.en.bin` | Path to Whisper model file (GGML format). |
| `stt.whisper.timeout-seconds` | int | `10` | Maximum transcription time in seconds. Prevents hanging on long/corrupted audio. |
| `stt.whisper.language` | String | `en` | Language code (ISO 639-1). Use `en` for English, `es` for Spanish, etc. |
| `stt.whisper.threads` | int | `4` | Number of CPU threads for Whisper processing. Higher = faster but more CPU usage. |
| `stt.whisper.max-stdout-bytes` | long | `1048576` | Maximum stdout buffer size (1MB). Protects against malicious model output. |
| `stt.whisper.output` | String | `text` | Output format: `text` (plain text) or `json` (structured with tokens). JSON mode enables advanced reconciliation. |

**Example:**
```properties
stt.whisper.binary-path=tools/whisper.cpp/main
stt.whisper.model-path=models/ggml-base.en.bin
stt.whisper.timeout-seconds=10
stt.whisper.language=en
stt.whisper.threads=4
stt.whisper.max-stdout-bytes=1048576
stt.whisper.output=text
```

**Supported Models:**
- `ggml-tiny.en.bin` (75MB, fastest, lowest accuracy)
- `ggml-base.en.bin` (142MB, balanced, good accuracy) ⭐ Recommended
- `ggml-small.en.bin` (466MB, slower, better accuracy)
- See [Whisper Models](https://github.com/ggerganov/whisper.cpp#models) for more options

**JSON Mode:**
```properties
stt.whisper.output=json
```
Enables word-level tokens for `WordOverlapReconciler`. Slightly slower but more accurate reconciliation.

---

### Engine Orchestration

Controls which engines are enabled and which engine is used as primary.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.enabled-engines` | String | `vosk,whisper` | Comma-separated list of enabled engines. Options: `vosk`, `whisper`. At least one must be enabled. |
| `stt.orchestration.primary-engine` | String | `vosk` | Primary engine preference. Options: `vosk` (fast, lower accuracy) or `whisper` (slower, higher accuracy). Falls back to secondary if primary is unhealthy. |
| `stt.parallel.timeout-ms` | long | `10000` | Timeout in milliseconds for parallel dual-engine transcription (reconciliation mode only). |

**Example:**
```properties
stt.enabled-engines=vosk,whisper
stt.orchestration.primary-engine=vosk
stt.parallel.timeout-ms=10000
```

**Single-Engine Mode:**
```properties
stt.enabled-engines=whisper
stt.orchestration.primary-engine=whisper
stt.reconciliation.enabled=false
```
Disables Vosk entirely, uses only Whisper.

---

### Reconciliation (Phase 4)

Controls dual-engine reconciliation strategies.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.reconciliation.enabled` | boolean | `false` | Enable dual-engine reconciliation. When `true`, runs both engines in parallel and reconciles results. |
| `stt.reconciliation.strategy` | String | `simple` | Reconciliation strategy. Options: `simple` (prefer primary), `confidence` (select by confidence score), `overlap` (Jaccard word overlap). |
| `stt.reconciliation.overlap-threshold` | double | `0.6` | Minimum Jaccard similarity threshold for `overlap` strategy (0.0 to 1.0). Lower = more aggressive fallback to longer text. |

**Example - Simple Strategy:**
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=simple
```
Always prefers primary engine result unless empty.

**Example - Confidence Strategy:**
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
```
Selects result with higher confidence score. Optimal when confidence scores are reliable.

**Example - Overlap Strategy:**
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=overlap
stt.reconciliation.overlap-threshold=0.6
stt.whisper.output=json
```
Uses Jaccard word overlap similarity. Requires `stt.whisper.output=json` for best results.

**Performance Note:** Reconciliation doubles CPU usage (runs both engines) but improves accuracy by 10-25% in testing.

---

### Concurrency Limits

Lightweight bulkheads to prevent resource exhaustion from concurrent transcription requests.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.concurrency.vosk-max` | int | `4` | Maximum concurrent Vosk transcriptions. Vosk is fast, so higher concurrency is safe. |
| `stt.concurrency.whisper-max` | int | `2` | Maximum concurrent Whisper transcriptions. Whisper is CPU-intensive, keep this low. |
| `stt.concurrency.acquire-timeout-ms` | int | `1000` | Maximum wait time (ms) to acquire concurrency permit. Prevents indefinite blocking. |

**Example:**
```properties
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
stt.concurrency.acquire-timeout-ms=1000
```

**Tuning Guide:**
- **Low-end CPU (2-4 cores):** `vosk-max=2`, `whisper-max=1`
- **Mid-range CPU (4-8 cores):** `vosk-max=4`, `whisper-max=2` (default)
- **High-end CPU (8+ cores):** `vosk-max=8`, `whisper-max=4`

---

### Engine Watchdog

Auto-restart engines on repeated failures with sliding window rate limiting.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.watchdog.enabled` | boolean | `true` | Enable automatic engine restart on failures. Recommended for production. |
| `stt.watchdog.window-minutes` | int | `60` | Sliding window duration for restart budget (minutes). |
| `stt.watchdog.max-restarts-per-window` | int | `3` | Maximum restarts allowed within the sliding window. Prevents restart loops. |
| `stt.watchdog.cooldown-minutes` | int | `10` | Cooldown period after max restarts exhausted before allowing new restarts. |

**Example:**
```properties
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

**How It Works:**
1. Engine fails 3 times within 60 minutes → watchdog disables it
2. Waits 10 minutes (cooldown)
3. Re-enables engine after cooldown
4. Sliding window resets if no failures occur for 60 minutes

**Disable for Testing:**
```properties
stt.watchdog.enabled=false
```
Useful when debugging engine crashes.

---

### Model Validation

Fail-fast validation of STT models at startup.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.validation.enabled` | boolean | `true` | Validate model files exist at startup. Recommended to catch setup errors early. |

**Example:**
```properties
stt.validation.enabled=true
```

**Disable Only For:**
- Test profiles where models are intentionally missing
- CI environments using mock engines

---

## Hotkey Configuration

Configures the global hotkey for push-to-talk.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `hotkey.type` | String | `single-key` | Hotkey type. Options: `single-key`, `double-tap`, or `modifier-combo`. |
| `hotkey.key` | String | `RIGHT_META` | Primary key for hotkey. Examples: `RIGHT_META` (⌘ on macOS), `M`, `SPACE`, `F13`. |
| `hotkey.modifiers` | String | (empty) | Comma-separated modifiers. Required for `modifier-combo`, optional for `single-key` and `double-tap`. Options: `SHIFT`, `CTRL`, `ALT`, `META`, `LEFT_META`, `RIGHT_META`. |
| `hotkey.threshold-ms` | int | (no threshold) | Optional: For `double-tap`, this is the maximum time between taps (100-1000ms recommended). For other types, it's the minimum hold duration. |
| `hotkey.toggle-mode` | boolean | `false` | Toggle mode: `true` = click once to start recording, click again to stop and transcribe. `false` = push-to-talk (press to start, release to stop). |
| `hotkey.reserved` | String | (empty) | Comma-separated list of OS shortcuts to warn about. Platform-aware validation. |

**Example - Single Key (Right Command on macOS):**
```properties
hotkey.type=single-key
hotkey.key=RIGHT_META
```

**Example - Single Key with Optional Modifier:**
```properties
hotkey.type=single-key
hotkey.key=F13
hotkey.modifiers=SHIFT
```
**Note:** For `single-key` type, modifiers are optional and allowed but may be ignored by the trigger implementation. Only `modifier-combo` requires and enforces modifier usage.

**Example - Modifier Combination (Cmd+Shift+M):**
```properties
hotkey.type=modifier-combo
hotkey.key=M
hotkey.modifiers=META,SHIFT
```
Requires at least one modifier in `hotkey.modifiers`.

**Example - Double Tap (Double-tap D within 300ms):**
```properties
hotkey.type=double-tap
hotkey.key=D
hotkey.threshold-ms=300
```
**Note:** `threshold-ms` for `double-tap` should be between 100-1000 milliseconds for best results.

**Example - Hold Threshold (Hold 300ms to activate):**
```properties
hotkey.type=single-key
hotkey.key=RIGHT_META
hotkey.threshold-ms=300
```
Prevents accidental triggers from quick taps.

**Reserved Shortcuts Warning:**
```properties
hotkey.reserved=META+TAB,META+L
```
Application warns if configured hotkey conflicts with OS shortcuts.

**Valid Key Names:**
- Letters: `A` - `Z`
- Numbers: `0` - `9`
- Special: `SPACE`, `ENTER`, `TAB`, `ESCAPE`
- Modifiers: `SHIFT`, `CTRL`, `ALT`, `META`, `LEFT_META`, `RIGHT_META`
- See [JNativeHook KeyEvent](https://github.com/kwhat/jnativehook/wiki/Key-Event-Codes) for complete list

---

## Typing Configuration

Controls text delivery to active application via clipboard/robot.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `typing.chunk-size` | int | `800` | Maximum characters per paste chunk. Large texts are split to avoid clipboard limits. |
| `typing.inter-chunk-delay-ms` | int | `30` | Delay between paste chunks (ms). Gives apps time to process before next chunk. |
| `typing.focus-delay-ms` | int | `100` | Delay after window focus before pasting (ms). Ensures app is ready to receive input. |
| `typing.restore-clipboard` | boolean | `true` | Restore original clipboard contents after pasting. Prevents losing user's clipboard. |
| `typing.clipboard-only-fallback` | boolean | `false` | Use clipboard-only mode (no Robot keystroke simulation). Useful when Accessibility permission unavailable. |
| `typing.normalize-newlines` | String | `LF` | Newline normalization. Options: `LF` (\n), `CRLF` (\r\n), `CR` (\r), `NONE` (no normalization). |
| `typing.trim-trailing-newline` | boolean | `true` | Remove trailing newline from transcription. Prevents extra blank line after paste. |
| `typing.enable-robot` | boolean | `true` | Enable Java Robot API for keystroke simulation. Requires macOS Accessibility permission. |
| `typing.paste-shortcut` | String | `os-default` | Paste keyboard shortcut. Options: `os-default` (⌘+V on macOS, Ctrl+V on Windows), or custom like `META+V`. |

**Example - Default Configuration:**
```properties
typing.chunk-size=800
typing.inter-chunk-delay-ms=30
typing.focus-delay-ms=100
typing.restore-clipboard=true
typing.clipboard-only-fallback=false
typing.normalize-newlines=LF
typing.trim-trailing-newline=true
typing.enable-robot=true
typing.paste-shortcut=os-default
```

**Example - Clipboard-Only Mode (No Accessibility Permission):**
```properties
typing.enable-robot=false
typing.clipboard-only-fallback=true
```
Falls back to clipboard-only when Robot API unavailable. User must manually paste (⌘+V).

**Example - Windows Line Endings:**
```properties
typing.normalize-newlines=CRLF
```
Useful for pasting into Windows-specific apps.

---

## Spring Boot Actuator

Production monitoring and health check endpoints.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `management.endpoints.web.exposure.include` | String | `health,info,metrics,prometheus` | Comma-separated list of exposed actuator endpoints. |
| `management.endpoints.web.base-path` | String | `/actuator` | Base path for actuator endpoints. |
| `management.endpoint.health.show-details` | String | `when-authorized` | Health endpoint detail level. Options: `always`, `when-authorized`, `never`. |
| `management.health.defaults.enabled` | boolean | `true` | Enable default health indicators (disk space, etc.). Note: This application does not use a database; database health indicators are not applicable. |
| `server.shutdown` | String | `graceful` | Shutdown mode. `graceful` waits for requests to complete before shutdown. |
| `spring.lifecycle.timeout-per-shutdown-phase` | String | `30s` | Maximum time to wait per shutdown phase (e.g., "30s", "1m"). |

**Example:**
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoints.web.base-path=/actuator
management.endpoint.health.show-details=when-authorized
management.health.defaults.enabled=true
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

**Actuator Endpoints:**
- `GET /actuator/health` - Application health status
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus-formatted metrics

**Disable for Development:**
```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always
```

---

## Production-Ready Configuration

Recommended settings for production deployment:

```properties
# Enable all engines with reconciliation for best accuracy
stt.enabled-engines=vosk,whisper
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
stt.whisper.output=json

# Enable watchdog for auto-recovery
stt.watchdog.enabled=true

# Conservative concurrency limits
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2

# Enable actuator for monitoring
management.endpoints.web.exposure.include=health,metrics,prometheus

# Graceful shutdown
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

---

## Testing Configuration

Recommended settings for local development and testing:

```properties
# Single engine for faster startup
stt.enabled-engines=vosk
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false

# Disable watchdog for easier debugging
stt.watchdog.enabled=false

# Lower concurrency for resource-constrained dev machines
stt.concurrency.vosk-max=2
stt.concurrency.whisper-max=1

# Detailed health info
management.endpoint.health.show-details=always
```

---

## Environment-Specific Configuration

Use Spring profiles to override properties per environment:

**application-dev.properties:**
```properties
stt.watchdog.enabled=false
management.endpoint.health.show-details=always
```

**application-prod.properties:**
```properties
stt.watchdog.enabled=true
stt.reconciliation.enabled=true
management.endpoint.health.show-details=when-authorized
```

**Run with profile:**
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## Validation Rules

Configuration validation occurs at startup. Common errors:

| Error | Cause | Fix |
|-------|-------|-----|
| `ModelNotFoundException` | Invalid `stt.vosk.model-path` or `stt.whisper.model-path` | Run `./setup-models.sh` or manually download models |
| `IllegalArgumentException: threshold in [0,1]` | Invalid `stt.reconciliation.overlap-threshold` | Set value between 0.0 and 1.0 |
| `Engine concurrency limit reached` | Too many concurrent requests for `stt.concurrency.*-max` | Increase limits or reduce load |
| `Both engines unavailable` | All enabled engines failed to initialize | Check model paths and watchdog settings |

---

## Performance Tuning

### Optimizing for Speed (Low Latency)

```properties
# Use only Vosk (fastest engine)
stt.enabled-engines=vosk
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false

# Small model for speed
stt.vosk.model-path=models/vosk-model-en-us-0.22

# Higher concurrency
stt.concurrency.vosk-max=8
```

### Optimizing for Accuracy

```properties
# Use reconciliation with confidence strategy
stt.enabled-engines=vosk,whisper
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
stt.whisper.output=json

# Larger models
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.whisper.model-path=models/ggml-small.en.bin

# More Whisper threads
stt.whisper.threads=8
```

### Balancing Speed and Accuracy

```properties
# Primary Vosk with Whisper fallback (no reconciliation)
stt.enabled-engines=vosk,whisper
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false

# Balanced models
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.whisper.model-path=models/ggml-base.en.bin
```

---

## See Also

- [Setup Guide](../README.md#setup-guide) - Initial setup and model installation
- [Developer Guide](developer-guide.md) - Architecture and development workflow
- [Troubleshooting](../README.md#troubleshooting) - Common issues and solutions
- [ADRs](adr/) - Architectural decision records
