# ADR-003: Manual Model Setup Script

## Status
Accepted (2025-01-14)

## Context
Vosk and Whisper require pre-trained model files (~200 MB total):
- Vosk: `vosk-model-small-en-us-0.15.zip` (~50 MB)
- Whisper: `ggml-base.en.bin` (~150 MB)

Models needed before first transcription. Must balance:
- Developer onboarding experience
- Repository clone speed
- CI/CD pipeline efficiency
- Offline development capability

## Decision
Provide **manual setup script** (`setup-models.sh`) with fail-fast validation.

**Approach:**
- Script downloads models from public URLs (alphacephei.com, huggingface.co)
- SHA256 checksum verification
- Spring validates model presence at startup
- Models git-ignored, not committed to repository

**Script:**
```bash
#!/bin/bash
set -e
MODELS_DIR="./models"
mkdir -p "$MODELS_DIR"

# Download Vosk (50 MB)
curl -L "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip" -o "$MODELS_DIR/vosk.zip"
# Verify checksum
shasum -a 256 -c <<< "expected_hash *$MODELS_DIR/vosk.zip"
unzip -q "$MODELS_DIR/vosk.zip" -d "$MODELS_DIR"

# Download Whisper (150 MB)
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin" -o "$MODELS_DIR/ggml-base.en.bin"
shasum -a 256 -c <<< "expected_hash *$MODELS_DIR/ggml-base.en.bin"
```

**Validation:**
```java
@PostConstruct
public void validateModels() {
    if (!Files.exists(Paths.get(voskModelPath))) {
        throw new IllegalStateException(
            "Vosk model not found. Run ./setup-models.sh"
        );
    }
}
```

## Consequences

### Positive
- ✅ **Fast clones**: Repository ~5 MB (vs 205 MB with models)
- ✅ **Clear errors**: Startup fails with actionable message
- ✅ **CI-friendly**: Cache `./models/` directory between runs
- ✅ **Offline capable**: Works after initial setup
- ✅ **No Git LFS costs**: Free for all developers

### Negative
- ❌ **Manual step**: Developers must run script before first use
- ❌ **Network dependency**: Initial setup requires internet
- ❌ **Download failures**: Firewalls/proxies may block CDNs

### Mitigation
- Clear README instructions with script command
- Checksum validation prevents corrupted downloads
- Fallback mirror URLs in script
- CI caches models (download once per pipeline)

## Alternatives Considered

### Git LFS (Large File Storage)
- **Rejected**: Large clones (205 MB), GitHub LFS costs ($5/50GB)
- **Advantage**: One-command setup (`git clone`)
- **Disadvantage**: Slow clones, paid service, no offline mode

### Auto-Download on First Run
- **Rejected**: Silent failures, unclear progress
- **Advantage**: No manual step
- **Disadvantage**: First transcription takes 3+ minutes, error-prone

### Bundled in JAR
- **Rejected**: 200 MB JAR file, slow builds
- **Advantage**: No external dependencies
- **Disadvantage**: Every rebuild requires model copy

## References
- Guidelines: Lines 196-274 (Model Setup Strategy)
- Implementation: Task 0.1 (Model Setup with Validation)
