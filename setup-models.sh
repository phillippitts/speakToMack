#!/usr/bin/env bash
set -euo pipefail

# speakToMack model setup script
# - Downloads Vosk small English model (~50 MB)
# - Downloads Whisper ggml base.en model (~150 MB)
#
# Models are saved to ./models (git-ignored)
# Re-run anytime to refresh models.

MODELS_DIR="./models"
VOSK_ZIP="$MODELS_DIR/vosk-model-small-en-us-0.15.zip"
VOSK_DIR="$MODELS_DIR/vosk-model-small-en-us-0.15"
WHISPER_BIN="$MODELS_DIR/ggml-base.en.bin"
CHECKSUMS_FILE="$MODELS_DIR/checksums.sha256"

# Optional: set official checksums here or via environment variables to enforce strict verification.
# If not provided, the script will compute and lock them on first successful download (and verify on subsequent runs).
VOSK_SHA256_DEFAULT="${VOSK_SHA256:-}"
WHISPER_SHA256_DEFAULT="${WHISPER_SHA256:-}"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
step() { printf "\n🔹 %s\n" "$1"; }
info() { printf "   %s\n" "$1"; }
ok()   { printf "✅ %s\n" "$1"; }
err()  { printf "\033[31m✗ %s\033[0m\n" "$1"; }

hash_tool() {
  if command -v shasum >/dev/null 2>&1; then
    echo "shasum -a 256"
  elif command -v sha256sum >/dev/null 2>&1; then
    echo "sha256sum"
  else
    err "No SHA-256 tool found (shasum or sha256sum). Install one and re-run."; exit 1
  fi
}

compute_sha256() {
  local file="$1"
  local tool
  tool=$(hash_tool)
  if [[ "$tool" == "shasum -a 256" ]]; then
    $tool "$file" | awk '{print $1}'
  else
    $tool "$file" | awk '{print $1}'
  fi
}

get_locked_checksum() {
  local filename="$1"
  if [ -f "$CHECKSUMS_FILE" ]; then
    awk -v f="$filename" '$2==f {print $1}' "$CHECKSUMS_FILE" | head -n1
  fi
}

lock_checksum() {
  local file="$1"; local sha="$2"
  # Store as: <sha256>  <relative-path>
  # Replace existing entry if present
  if [ -f "$CHECKSUMS_FILE" ]; then
    grep -v "  $file$" "$CHECKSUMS_FILE" > "$CHECKSUMS_FILE.tmp" || true
    mv "$CHECKSUMS_FILE.tmp" "$CHECKSUMS_FILE"
  fi
  echo "$sha  $file" >> "$CHECKSUMS_FILE"
}

verify_checksum() {
  local file="$1"; local expected="$2"
  local actual
  actual=$(compute_sha256 "$file")
  if [ "$actual" != "$expected" ]; then
    err "Checksum mismatch for $file";
    info "Expected: $expected"
    info "Actual:   $actual"
    info "The file will be removed. Please re-run the script. If upstream files changed legitimately, update expected checksums."
    rm -f "$file"
    exit 1
  fi
}

step "Preparing models directory: $MODELS_DIR"
mkdir -p "$MODELS_DIR"

# --- Download Vosk model ---
if [ -d "$VOSK_DIR" ]; then
  ok "Vosk model already present: $VOSK_DIR"
else
  step "Downloading Vosk model (~50 MB)"
  curl -L "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip" -o "$VOSK_ZIP.part"
  mv "$VOSK_ZIP.part" "$VOSK_ZIP"

  # Resolve expected checksum (env var > locked file > none)
  expected_vosk="$VOSK_SHA256_DEFAULT"
  if [ -z "$expected_vosk" ]; then
    expected_vosk="$(get_locked_checksum "$VOSK_ZIP")"
  fi
  if [ -n "$expected_vosk" ]; then
    step "Verifying Vosk checksum"
    verify_checksum "$VOSK_ZIP" "$expected_vosk"
  else
    # First-time lock-in: compute and store
    sha_vosk="$(compute_sha256 "$VOSK_ZIP")"
    info "Computed Vosk checksum: $sha_vosk"
    info "Locking checksum to $CHECKSUMS_FILE for future verification"
    lock_checksum "$VOSK_ZIP" "$sha_vosk"
  fi

  step "Extracting Vosk model"
  unzip -q -o "$VOSK_ZIP" -d "$MODELS_DIR"
  rm -f "$VOSK_ZIP"
  ok "Vosk model ready: $VOSK_DIR"
fi

# --- Download Whisper ggml base.en model ---
if [ -f "$WHISPER_BIN" ]; then
  ok "Whisper model already present: $WHISPER_BIN"
else
  step "Downloading Whisper ggml base.en model (~150 MB)"
  curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin" -o "$WHISPER_BIN.part"
  mv "$WHISPER_BIN.part" "$WHISPER_BIN"

  # Resolve expected checksum (env var > locked file > none)
  expected_whisper="$WHISPER_SHA256_DEFAULT"
  if [ -z "$expected_whisper" ]; then
    expected_whisper="$(get_locked_checksum "$WHISPER_BIN")"
  fi
  if [ -n "$expected_whisper" ]; then
    step "Verifying Whisper checksum"
    verify_checksum "$WHISPER_BIN" "$expected_whisper"
  else
    sha_whisper="$(compute_sha256 "$WHISPER_BIN")"
    info "Computed Whisper checksum: $sha_whisper"
    info "Locking checksum to $CHECKSUMS_FILE for future verification"
    lock_checksum "$WHISPER_BIN" "$sha_whisper"
  fi

  ok "Whisper model ready: $WHISPER_BIN"
fi

step "Models directory contents:"
ls -lh "$MODELS_DIR" || true

ok "Models setup complete. You can now run: ./gradlew bootRun"