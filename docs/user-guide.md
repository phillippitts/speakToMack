# User Guide

This guide helps you install, run, and use speakToMack for local voice dictation on macOS.

## Prerequisites
- macOS 12+ (Monterey or later)
- Java 21
- Disk space: ~3 GB for STT models and tools
- Permissions: Microphone (capture) and Accessibility (typing via Robot)

## Quick Start
1) Download models (Vosk + Whisper):
```bash
chmod +x ./setup-models.sh
./setup-models.sh
```
2) Build Whisper binary:
```bash
chmod +x ./build-whisper.sh
WRITE_APP_PROPS=true ./build-whisper.sh
```
3) Build & run:
```bash
./gradlew clean build
./gradlew bootRun
```
4) Grant permissions when macOS prompts you:
- System Settings → Privacy & Security → Microphone
- System Settings → Privacy & Security → Accessibility (for paste/typing)

## Using the App
- Default hotkey: Right Command (RIGHT_META)
- Press and hold the hotkey, speak, release; the text is pasted into the active application.

## Changing the Hotkey
Edit `src/main/resources/application.properties`:
```properties
hotkey.type=single-key            # single-key | double-tap | modifier-combination
hotkey.key=RIGHT_META             # e.g., RIGHT_META, F13, D
# hotkey.modifiers=META,SHIFT     # for modifier-combination
# hotkey.threshold-ms=300         # for double-tap
# hotkey.reserved=META+TAB,META+L # flag conflicts (platform-aware)
```
Notes:
- The app detects common OS-reserved shortcuts (e.g., Cmd+Tab) and logs a warning.
- If Accessibility permission is denied, a HotkeyPermissionDeniedEvent is published and logged.

## Dictation Flow
- Push-to-talk, whole-buffer model (no streaming):
  - Hotkey Press → Start capture
  - Hotkey Release → Stop capture, transcribe, paste
- Audio capture returns validated PCM16LE mono @ 16kHz.

## Enabling Reconciliation (optional)
Run both engines in parallel and reconcile the results:
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=simple    # simple | confidence | overlap
stt.reconciliation.overlap-threshold=0.6
```
- The orchestrator publishes a reconciled `TranscriptionCompletedEvent`.
- INFO logs show strategy, duration, and character count (no full text).

## Enabling Whisper JSON Mode (optional)
JSON mode provides richer tokens for better overlap reconciliation:
```properties
stt.whisper.output=json   # default is text
```
- The manager adds `-oj` to the CLI and caps stdout to prevent memory spikes.
- JSON is parsed safely; malformed JSON falls back gracefully.

## Troubleshooting
- Hotkey not detected:
  - Check for OS-reserved conflict in logs; adjust `hotkey.*` properties.
  - Ensure Accessibility permission is granted.
- Typing doesn’t work:
  - Robot tier requires Accessibility; clipboard fallback works without it.
  - Use `typing.clipboard-only-fallback=true` to skip issuing paste shortcut.
- Microphone capture errors:
  - Check Microphone permission and device availability.
  - See logs for `CaptureErrorEvent` with reason.

## Support
- Metrics available at `/actuator/metrics` (dev). In production profile, only `health` and `info` are exposed.
- Logs are privacy-safe at INFO level (no full transcript).
