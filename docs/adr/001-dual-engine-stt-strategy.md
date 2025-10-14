# ADR-001: Dual-Engine STT Strategy

## Status
Accepted (2025-01-14)

## Context
Voice dictation requires balancing speed (user experience) and accuracy (output quality). Single-engine approaches force compromise:
- Vosk: Fast (~100ms) but lower accuracy, limited punctuation
- Whisper: Accurate (~1-2s) with punctuation but slower

Users expect sub-3-second latency but also high-quality transcription for professional use.

## Decision
Run Vosk and Whisper **in parallel** on separate threads, reconcile results using configurable strategy (default: prefer Whisper).

**Architecture:**
- `ParallelSttService` uses `ExecutorService` with 2 threads
- Both engines process same audio buffer simultaneously
- `TranscriptReconciler` (Strategy pattern) selects final text
- 5 reconciliation strategies: Simple, Overlap, Diff, Confidence, Weighted

**Configuration:**
```yaml
stt:
  parallel:
    enabled: true
    timeout-ms: 5000
  reconciliation:
    strategy: overlap  # simple|overlap|diff|weighted|confidence
```

## Consequences

### Positive
- ✅ **Latency = max(vosk, whisper)** not sum (~1-2s vs 3s sequential)
- ✅ **Resilience**: If one engine fails, other provides fallback
- ✅ **Quality validation**: Agreement between engines indicates confidence
- ✅ **Flexibility**: Users choose speed vs accuracy via config

### Negative
- ❌ **2x CPU usage** during transcription (2-5 seconds per request)
- ❌ **2x memory** for loaded models (~200 MB total)
- ❌ **Complexity**: Reconciliation logic adds 400+ lines of code

### Mitigation
- Circuit breaker pattern to disable failing engine
- Model caching (SoftReference) allows GC under memory pressure
- Single-engine mode available via `stt.parallel.enabled=false`

## Alternatives Considered

### Sequential Processing (Vosk → Whisper)
- **Rejected**: Wall-clock time = sum (3s total)
- **Advantage**: Lower CPU usage
- **Disadvantage**: Poor user experience (3s wait)

### Single Engine (Whisper only)
- **Rejected**: No fallback if engine fails
- **Advantage**: Simpler code
- **Disadvantage**: Timeout = complete failure

### Cloud STT (Google/AWS)
- **Rejected**: Violates privacy requirement
- **Advantage**: No local model management
- **Disadvantage**: 60x cost, network dependency, data leaves device

## References
- Guidelines: Lines 122-193 (Design Patterns)
- Implementation: `ParallelSttService`, `TranscriptReconciler`
