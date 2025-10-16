#!/usr/bin/env bash
set -euo pipefail

# Build script for whisper.cpp binary used by speakToMack
# Assumes models were already downloaded by ./setup-models.sh and checksums recorded.
# - Verifies Whisper model exists in ./models
# - Clones ggerganov/whisper.cpp
# - Builds the 'main' executable via make
# - Clears macOS quarantine and sets exec perms
# - Prints the binary path for application.properties (stt.whisper.binary-path)
#
# Configurable via environment variables:
#   INSTALL_DIR=./tools                 # Where to place the cloned repo and built binary
#   REPO_URL=https://github.com/ggerganov/whisper.cpp.git
#   REPO_DIR=$INSTALL_DIR/whisper.cpp
#   GIT_REF=main                        # Tag/branch/commit for reproducibility
#   MAKE_JOBS=<cores>                   # Parallel make jobs
#   WRITE_APP_PROPS=false               # If 'true', update src/main/resources/application.properties in-place
#   MODELS_DIR=./models                 # Where models are located (from setup-models.sh)
#   WHISPER_MODEL=<path>                # Override whisper model path (defaults to ${MODELS_DIR}/ggml-base.en.bin)
#
# Usage:
#   ./build-whisper.sh
#   INSTALL_DIR=$HOME/.cache/speaktomack GIT_REF=v1.6.2 ./build-whisper.sh
#   WRITE_APP_PROPS=true ./build-whisper.sh

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
info() { printf "   %s\n" "$1"; }
ok()   { printf "✅ %s\n" "$1"; }
warn() { printf "\033[33m⚠ %s\033[0m\n" "$1"; }
err()  { printf "\033[31m✗ %s\033[0m\n" "$1"; }

OS_NAME=$(uname -s || echo unknown)
OS_ARCH=$(uname -m || echo unknown)

REPO_ROOT=$(pwd)
MODELS_DIR="${MODELS_DIR:-"${REPO_ROOT}/models"}"
WHISPER_MODEL="${WHISPER_MODEL:-"${MODELS_DIR}/ggml-base.en.bin"}"
CHECKSUMS_FILE="${CHECKSUMS_FILE:-"${MODELS_DIR}/checksums.sha256"}"

INSTALL_DIR=${INSTALL_DIR:-"${REPO_ROOT}/tools"}
REPO_URL=${REPO_URL:-"https://github.com/ggerganov/whisper.cpp.git"}
REPO_DIR="${REPO_DIR:-"${INSTALL_DIR}/whisper.cpp"}"
GIT_REF=${GIT_REF:-"main"}

# Determine parallel jobs
if command -v nproc >/dev/null 2>&1; then
  MAKE_JOBS=${MAKE_JOBS:-"$(nproc)"}
elif [ "$OS_NAME" = "Darwin" ] && command -v sysctl >/dev/null 2>&1; then
  MAKE_JOBS=${MAKE_JOBS:-"$(sysctl -n hw.ncpu)"}
else
  MAKE_JOBS=${MAKE_JOBS:-"4"}
fi

bold "Building whisper.cpp (models already present)"
info "OS: ${OS_NAME}, Arch: ${OS_ARCH}"
info "Install dir: ${INSTALL_DIR}"
info "Repo dir: ${REPO_DIR}"
info "Git ref: ${GIT_REF}"
info "Parallel jobs: ${MAKE_JOBS}"

# 1) Verify models exist (since setup-models.sh already ran)
if [ ! -d "${MODELS_DIR}" ]; then
  err "Models directory not found at ${MODELS_DIR}. Run ./setup-models.sh first."; exit 1
fi
if [ ! -f "${WHISPER_MODEL}" ]; then
  err "Whisper model not found at ${WHISPER_MODEL}. Run ./setup-models.sh first."; exit 1
fi
ok "Found Whisper model: ${WHISPER_MODEL}"

if [ -f "${CHECKSUMS_FILE}" ]; then
  info "Checksums file present: ${CHECKSUMS_FILE}"
else
  warn "Checksums file not found at ${CHECKSUMS_FILE}. Continuing (models assumed already verified)."
fi

# 2) Prereq hints (non-fatal)
if ! command -v git >/dev/null 2>&1; then
  warn "git not found. Please install git and re-run."
fi
if ! command -v make >/dev/null 2>&1; then
  warn "make not found. Please install build tools and re-run."
fi
case "$OS_NAME" in
  Linux)
    info "Linux prerequisites (Debian/Ubuntu): sudo apt-get install -y git build-essential"
    ;;
  Darwin)
    info "macOS prerequisites: xcode-select --install (clang/make)"
    ;;
  *) ;;
esac

# 3) Clone/update whisper.cpp
mkdir -p "$INSTALL_DIR"
if [ -d "$REPO_DIR/.git" ]; then
  info "Repository exists; fetching latest..."
  git -C "$REPO_DIR" fetch --all --tags --prune
else
  info "Cloning whisper.cpp..."
  git clone "$REPO_URL" "$REPO_DIR"
fi

info "Checking out ${GIT_REF}"
( cd "$REPO_DIR" && git checkout -q "$GIT_REF" )

# 4) Build
bold "Running make"
( cd "$REPO_DIR" && make -j"$MAKE_JOBS" )

BINARY_PATH="${REPO_DIR}/main"

# 5) macOS quarantine and permissions
if [ "$OS_NAME" = "Darwin" ]; then
  if command -v xattr >/dev/null 2>&1; then
    info "Clearing macOS quarantine attribute (if present)"
    xattr -dr com.apple.quarantine "$BINARY_PATH" || true
  fi
fi
chmod +x "$BINARY_PATH" || true

# 6) Smoke test
if "$BINARY_PATH" -h >/dev/null 2>&1; then
  ok "whisper.cpp binary built: $BINARY_PATH"
else
  warn "Binary built but 'main -h' returned non-zero. You may still proceed, but verify locally."
fi

# 7) Optional: update application.properties
APP_PROPS="${REPO_ROOT}/src/main/resources/application.properties"
if [ "${WRITE_APP_PROPS:-false}" = "true" ]; then
  if [ -f "$APP_PROPS" ]; then
    info "Updating stt.whisper.binary-path in application.properties"
    # Replace existing line or append if missing
    if grep -q '^stt\.whisper\.binary-path=' "$APP_PROPS"; then
      # portable sed -i (macOS/BSD vs GNU)
      if sed --version >/dev/null 2>&1; then
        sed -i "s#^stt\.whisper\.binary-path=.*#stt.whisper.binary-path=${BINARY_PATH//#/\\#}#" "$APP_PROPS"
      else
        sed -i '' "s#^stt\.whisper\.binary-path=.*#stt.whisper.binary-path=${BINARY_PATH//#/\\#}#" "$APP_PROPS"
      fi
    else
      printf "\nstt.whisper.binary-path=%s\n" "$BINARY_PATH" >> "$APP_PROPS"
    fi
    ok "Updated: stt.whisper.binary-path=${BINARY_PATH}"
  else
    warn "application.properties not found at $APP_PROPS; skipping auto-update"
  fi
fi

cat <<EOF

Next steps:
1) Configure Spring Boot properties to use the built binary:

   stt.whisper.binary-path=${BINARY_PATH}
   stt.whisper.model-path=${WHISPER_MODEL}
   stt.whisper.timeout-seconds=10

2) If you encounter EACCES on macOS, ensure quarantine is cleared:
   xattr -dr com.apple.quarantine ${BINARY_PATH}

3) Verify quickly:
   ${BINARY_PATH} -h | head -n 1

4) Run the app (Task 2.1 will validate binary/model on startup once implemented):
   ./gradlew bootRun

EOF
