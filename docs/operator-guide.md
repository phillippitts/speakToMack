# Operator Guide

This guide documents configuration, metrics, logs, and production profile guidance for speakToMack.

## Configuration Overview
All configuration uses Spring Boot properties (application.properties). The most important groups:

### Audio Validation / Capture
```properties
# Validation thresholds (ms)
audio.validation.min-duration-ms=250
audio.validation.max-duration-ms=300000

# Capture (push-to-talk)
audio.capture.chunk-millis=40
audio.capture.max-duration-ms=60000
# audio.capture.device-name=
```

### STT Engines
```properties
# Vosk
stt.vosk.model-path=models/vosk-model-small-en-us-0.15
stt.vosk.sample-rate=16000
stt.vosk.max-alternatives=1

# Whisper
stt.whisper.binary-path=tools/whisper.cpp/main
stt.whisper.model-path=models/ggml-base.en.bin
stt.whisper.timeout-seconds=10
stt.whisper.language=en
stt.whisper.threads=4
stt.whisper.max-stdout-bytes=1048576
# Output mode: text | json  (default text)
stt.whisper.output=text
```

### Orchestration & Parallel
```properties
# Engines to consider (informational)
stt.enabled-engines=vosk,whisper

# Parallel run timeout (ms)
stt.parallel.timeout-ms=10000

# Primary engine for single-engine routing
stt.orchestration.primary-engine=vosk  # vosk | whisper
```

### Reconciliation (Phase 4)
```properties
# Enable reconciled path in orchestrator
stt.reconciliation.enabled=false
# Strategy: simple | confidence | overlap
stt.reconciliation.strategy=simple
# Jaccard overlap threshold for word-overlap strategy
stt.reconciliation.overlap-threshold=0.6
```

### Concurrency & Watchdog
```properties
# Lightweight bulkheads
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
stt.concurrency.acquire-timeout-ms=1000

# Event-driven watchdog
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

### Hotkeys
```properties
hotkey.type=single-key               # single-key | double-tap | modifier-combination
hotkey.key=RIGHT_META
# hotkey.modifiers=META,SHIFT
# hotkey.threshold-ms=300
# hotkey.reserved=META+TAB,META+L     # OS-reserved examples
```

### Typing / Fallback
```properties
typing.chunk-size=800
typing.inter-chunk-delay-ms=30
typing.focus-delay-ms=100
typing.restore-clipboard=true
typing.clipboard-only-fallback=false
typing.normalize-newlines=LF         # LF | CRLF | NONE
typing.trim-trailing-newline=true
typing.enable-robot=true
# typing.paste-shortcut=os-default    # os-default | META+V | CONTROL+V
```

## Metrics (Micrometer)
Metrics are PII-safe. Key meters:

- Engine metrics
  - `stt.engine.latency.ms{engine=vosk|whisper}` (timer)
  - `stt.engine.success_total{engine=}` (counter)
  - `stt.engine.failure_total{engine=,reason=timeout|error}` (counter)

- Reconciliation metrics (when enabled)
  - `stt.reconcile.strategy_total{strategy=simple|confidence|overlap}` (counter)
  - `stt.reconcile.selected_total{engine=vosk|whisper|unknown}` (counter)

Access in dev at `/actuator/metrics`. In production profile, only `health` and `info` are exposed.

## Logging
- INFO logs never include full transcripts; only durations and character counts.
- DEBUG logs may include truncated previews via LogSanitizer.
- Error events are centralized and throttled (Hotkey permission/ conflict; capture errors).

## Production profile
`src/main/resources/application-production.properties` reduces Actuator exposure:
```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```
Run with: `--spring.profiles.active=production`.

## Operations Quick Tips
- Reconciliation rollout
  - Start with `stt.reconciliation.enabled=true` + `strategy=simple`
  - Collect engine/reconcile metrics, then try `confidence`/`overlap`
  - Optional: enable `stt.whisper.output=json` for better overlap tokens
- High CPU / slow Whisper
  - Reduce `stt.whisper.threads`
  - Increase `stt.parallel.timeout-ms` if needed
- Frequent Whisper failures
  - Check `whisper.cpp` quarantine/permissions
  - Watchdog will auto-restart up to budget; see logs
- Typing issues
  - If Robot fails (Accessibility), clipboard tier still works
  - Consider `typing.clipboard-only-fallback=true`
