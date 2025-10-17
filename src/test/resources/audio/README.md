# Test Audio Resources

This directory contains PCM audio files used for integration testing of the STT engines.

## Audio Format Specification

All test audio files use the following format:
- **Format:** Raw PCM (no header)
- **Sample Rate:** 16,000 Hz (16 kHz)
- **Bit Depth:** 16-bit signed integer
- **Channels:** Mono (1 channel)
- **Byte Order:** Little-endian
- **Sample Size:** 2 bytes per sample

## File Inventory

### silence-1s.pcm
- **Duration:** 1 second
- **Size:** 32,000 bytes (16,000 samples × 2 bytes/sample)
- **Content:** Pure silence (all zeros)
- **Purpose:** Test engine handles silence without errors
- **Creation:** `dd if=/dev/zero bs=32000 count=1 of=silence-1s.pcm`

### silence-3s.pcm
- **Duration:** 3 seconds
- **Size:** 96,000 bytes (48,000 samples × 2 bytes/sample)
- **Content:** Pure silence (all zeros)
- **Purpose:** Test longer duration audio processing
- **Creation:** `dd if=/dev/zero bs=96000 count=1 of=silence-3s.pcm`

### tone-pattern.pcm
- **Duration:** ~0.4 seconds
- **Size:** ~13 KB
- **Content:** Synthesized tone pattern (440Hz + 880Hz sequence)
- **Purpose:** Test non-silence, non-speech audio for low-confidence validation
- **Creation:** Generated using ffmpeg lavfi sine filter
- **Notes:** Not actual speech; used to verify engine handles non-silence audio without crashing

## Creating New Test Files

### Generate Silence

```bash
# 1 second of silence
dd if=/dev/zero bs=32000 count=1 of=silence-1s.pcm

# 3 seconds of silence
dd if=/dev/zero bs=96000 count=1 of=silence-3s.pcm

# N seconds of silence (formula: 16000 Hz × 2 bytes × N seconds)
dd if=/dev/zero bs=$((16000 * 2 * N)) count=1 of=silence-Ns.pcm
```

### Record from Microphone (macOS)

```bash
# Record 3 seconds of speech at correct format
ffmpeg -f avfoundation -i ":0" \
  -ar 16000 \
  -ac 1 \
  -sample_fmt s16 \
  -t 3 \
  hello-world.pcm
```

### Record from Microphone (Linux)

```bash
# Record 3 seconds of speech at correct format
ffmpeg -f alsa -i default \
  -ar 16000 \
  -ac 1 \
  -sample_fmt s16 \
  -t 3 \
  hello-world.pcm
```

### Convert Existing WAV File

```bash
# Convert any WAV to test format
ffmpeg -i input.wav \
  -ar 16000 \
  -ac 1 \
  -sample_fmt s16 \
  -f s16le \
  output.pcm
```

### Generate White Noise

```bash
# 1 second of white noise (for low-confidence testing)
ffmpeg -f lavfi -i "anoisesrc=d=1:c=white:r=16000:a=0.5" \
  -ar 16000 \
  -ac 1 \
  -sample_fmt s16 \
  -f s16le \
  noise-1s.pcm
```

### Generate Tone Pattern

```bash
# Tone pattern (440Hz + 880Hz for 0.2s each)
ffmpeg -f lavfi -i "sine=frequency=440:duration=0.2" \
  -f lavfi -i "sine=frequency=880:duration=0.2" \
  -filter_complex "[0][1]concat=n=2:v=0:a=1" \
  -ar 16000 \
  -ac 1 \
  -sample_fmt s16 \
  -f s16le \
  tone-pattern.pcm
```

## Verification

### Check File Size

```bash
# Should output exact byte counts
ls -l *.pcm

# Calculate expected size: SampleRate × BytesPerSample × Duration
# Example: 16000 Hz × 2 bytes × 1 second = 32,000 bytes
```

### Verify Format with ffprobe

```bash
# Convert PCM to WAV temporarily for analysis
ffmpeg -f s16le -ar 16000 -ac 1 -i silence-1s.pcm temp.wav
ffprobe temp.wav
rm temp.wav
```

### Listen to File (macOS)

```bash
# Play PCM file (requires conversion to WAV)
ffplay -f s16le -ar 16000 -ac 1 silence-1s.pcm
```

## Usage in Tests

Test files are loaded using the `TestResourceLoader` utility:

```java
import com.phillippitts.speaktomack.TestResourceLoader;

byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");
```

## Future Test Files (Planned)

- `hello-world.pcm` - Clear speech for accuracy testing
- `noise-1s.pcm` - White noise for low-confidence testing
- `fast-speech.pcm` - Rapid speech for stress testing
- `accented-speech.pcm` - Non-US accent for diversity testing
- `music-1s.pcm` - Background music to test non-speech audio

## Notes

- Files are committed to Git due to small size (< 100KB)
- Larger test files (> 1MB) should use Git LFS
- Real speech files should not contain PII or sensitive information
- All files must be 16kHz mono PCM for compatibility
