# Installation Guide

This guide helps end users install and run speakToMack without needing Java development tools.

## ⚠️ Important: Permission Requirements

**Before running the app for the first time, you MUST grant Accessibility permission in System Settings.**

Without Accessibility permission, the app will crash with a fatal JVM error. See Step 6 below for detailed instructions.

## System Requirements

- **Operating System**: macOS 12+ (Monterey or later)
- **Java Runtime**: Java 21 (JRE is sufficient, JDK not required)
- **Disk Space**: ~300 MB (~200 MB for models + ~100 MB for application)
- **Permissions** (MUST be granted before first run):
  - **Accessibility** (REQUIRED - for global hotkey capture and text typing)
  - **Microphone** (REQUIRED - for voice capture)

---

## Option 1: Pre-Built JAR (Recommended for Most Users)

### Step 1: Install Java 21

Check if you have Java 21 installed:
```bash
java -version
```

If you see `java version "21"` or higher, skip to Step 2.

#### Installing Java 21 on macOS

**Using Homebrew (recommended):**
```bash
# Install Homebrew if you don't have it
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java 21
brew install openjdk@21

# Link it so macOS can find it
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Verify installation
java -version
```

**Manual download:**
1. Download from [Adoptium](https://adoptium.net/temurin/releases/?version=21)
2. Choose macOS (x64 or ARM64 depending on your Mac)
3. Download the `.pkg` installer
4. Run the installer

### Step 2: Download speakToMack

**Option A: Download from GitHub Releases (when available)**
1. Go to [Releases](https://github.com/your-org/speakToMack/releases)
2. Download `speakToMack-X.X.X.jar`
3. Save to a permanent location (e.g., `~/Applications/speakToMack/`)

**Option B: Build from source**
```bash
# Clone repository
git clone https://github.com/your-org/speakToMack.git
cd speakToMack

# Download models
chmod +x ./setup-models.sh
./setup-models.sh

# Build Whisper binary
chmod +x ./build-whisper.sh
WRITE_APP_PROPS=true ./build-whisper.sh

# Build JAR
./gradlew clean build

# The JAR will be at: build/libs/speakToMack-0.0.1-SNAPSHOT.jar
# Copy to permanent location:
mkdir -p ~/Applications/speakToMack
cp build/libs/speakToMack-0.0.1-SNAPSHOT.jar ~/Applications/speakToMack/speakToMack.jar
cp -r models ~/Applications/speakToMack/
cp -r tools ~/Applications/speakToMack/
```

### Step 3: Download Models

If you downloaded a pre-built JAR, you need to download the STT models separately:

```bash
cd ~/Applications/speakToMack

# Download setup script
curl -O https://raw.githubusercontent.com/your-org/speakToMack/main/setup-models.sh
chmod +x setup-models.sh

# Run it
./setup-models.sh
```

This downloads:
- Vosk model (~40 MB)
- Whisper model (~147 MB)

### Step 4: Download Whisper Binary

```bash
cd ~/Applications/speakToMack

# Download build script
curl -O https://raw.githubusercontent.com/your-org/speakToMack/main/build-whisper.sh
chmod +x build-whisper.sh

# Build whisper.cpp
./build-whisper.sh
```

### Step 5: Configure Paths

Create `application-local.properties` in the same directory as the JAR:

```bash
cd ~/Applications/speakToMack
cat > application-local.properties << 'EOF'
# Model paths (absolute paths for production)
stt.vosk.model-path=/Users/YOUR_USERNAME/Applications/speakToMack/models/vosk-model-small-en-us-0.15
stt.whisper.model-path=/Users/YOUR_USERNAME/Applications/speakToMack/models/ggml-base.en.bin
stt.whisper.binary-path=/Users/YOUR_USERNAME/Applications/speakToMack/tools/whisper.cpp/main

# Log location
logging.file.path=/Users/YOUR_USERNAME/Applications/speakToMack/logs
EOF

# Replace YOUR_USERNAME with your actual username
sed -i '' "s/YOUR_USERNAME/$(whoami)/g" application-local.properties
```

### Step 6: Grant Permissions (REQUIRED - Do This First!)

**⚠️ CRITICAL**: You MUST grant Accessibility permission BEFORE starting the app for the first time. Without this permission, the app will crash with a JVM segmentation fault.

1. **Accessibility Permission** (for hotkey and typing):
   - Open: **System Settings → Privacy & Security → Accessibility**
   - Click the lock icon to make changes (enter your password)
   - Click **"+"** button
   - Navigate to and select: `/Applications/Utilities/Terminal.app`
   - Enable the checkbox next to Terminal
   - **Verify**: The checkbox should be checked with Terminal in the list

2. **Microphone Permission** (will prompt on first audio capture):
   - The app will prompt automatically when you first use dictation
   - Or manually grant: **System Settings → Privacy & Security → Microphone**
   - Enable for "java"

**Why this order matters**:
- JNativeHook (the hotkey library) crashes the JVM if Accessibility permission is denied
- You cannot grant permission after the crash - you must grant it BEFORE first run
- If you forget and the app crashes, just grant the permission and restart

### Step 7: Run the Application

```bash
cd ~/Applications/speakToMack
java -jar speakToMack.jar --spring.config.additional-location=./application-local.properties
```

You should see log output indicating the app started successfully:
```
INFO  c.p.s.SpeakToMackApplication - Started SpeakToMackApplication in 3.3 seconds
INFO  c.p.s.h.HotkeyManager - HotkeyManager started with trigger=...
```

### Step 8: Test Dictation

1. Open any text editor (TextEdit, Notes, etc.)
2. Click in the text field
3. **Press and hold** the Right Command key (⌘)
4. **Speak** your text
5. **Release** the Right Command key
6. Your text should appear!

---

## Option 2: Run as Background Service (Auto-Start on Login)

For permanent installation that starts automatically when you log in.

### macOS LaunchAgent Installation

1. Complete Option 1 (Steps 1-5) to set up the JAR and models

2. Create a LaunchAgent plist:
```bash
mkdir -p ~/Library/LaunchAgents
cat > ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.phillippitts.speaktomack</string>

    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/java</string>
        <string>-Dspring.profiles.active=production</string>
        <string>-jar</string>
        <string>/Users/$(whoami)/Applications/speakToMack/speakToMack.jar</string>
        <string>--spring.config.additional-location=/Users/$(whoami)/Applications/speakToMack/application-local.properties</string>
    </array>

    <key>WorkingDirectory</key>
    <string>/Users/$(whoami)/Applications/speakToMack</string>

    <key>RunAtLoad</key>
    <true/>

    <key>KeepAlive</key>
    <true/>

    <key>StandardOutPath</key>
    <string>/Users/$(whoami)/Applications/speakToMack/logs/stdout.log</string>

    <key>StandardErrorPath</key>
    <string>/Users/$(whoami)/Applications/speakToMack/logs/stderr.log</string>
</dict>
</plist>
EOF
```

3. Load the service:
```bash
launchctl load ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist
```

4. Verify it's running:
```bash
# Check process is running
ps aux | grep speakToMack

# Check health endpoint
curl http://localhost:8080/actuator/health
```

5. Service management:
```bash
# Stop service
launchctl unload ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist

# Restart service
launchctl unload ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist
launchctl load ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist

# View logs
tail -f ~/Applications/speakToMack/logs/speakToMack.log
```

---

## Option 3: Native macOS App Bundle (Future)

**Status**: Not yet implemented

Planned features:
- Double-click .app to launch
- No Terminal required
- Bundled JRE (no Java installation needed)
- Automatic updates

See [Issue #XX] for progress.

---

## Customizing the Hotkey

The default hotkey is **Right Command** (⌘). To change it:

1. Stop the application if it's running

2. Edit `application-local.properties`:
```properties
# Single key mode
hotkey.type=single-key
hotkey.key=F13                    # Options: F13, F14, RIGHT_META, etc.

# Double-tap mode (tap a key twice quickly)
hotkey.type=double-tap
hotkey.key=RIGHT_META
hotkey.threshold-ms=300           # Max time between taps

# Modifier combination mode (e.g., Cmd+Shift+D)
hotkey.type=modifier-combination
hotkey.key=D
hotkey.modifiers=META,SHIFT
```

3. Restart the application

**Available key names**:
- Function keys: `F13`, `F14`, `F15`, `F16`, `F17`, `F18`, `F19`, `F20`
- Modifiers: `RIGHT_META` (Right Cmd), `LEFT_META`, `RIGHT_CONTROL`, `RIGHT_SHIFT`
- Letters: `A`, `B`, `C`, etc.
- See full list: [Hotkey Configuration Reference](docs/configuration-reference.md)

**Avoiding conflicts**:
- Avoid system shortcuts (Cmd+Tab, Cmd+Space, etc.)
- The app will warn you if you choose a reserved shortcut
- Recommendation: Use function keys (F13-F20) - they're rarely used by macOS

---

## Troubleshooting

### JVM Crash: SIGSEGV / Fatal Error on Startup

**Symptoms**:
```
ERROR com.github.kwhat.jnativehook - Failed to create event port!
# A fatal error has been detected by the Java Runtime Environment:
# SIGSEGV (0xb) at pc=0x...
# Problematic frame: C [CoreFoundation+0x...]
```

**Cause**: Accessibility permission was not granted before first run. JNativeHook (the global hotkey library) crashes the JVM when it cannot create an event port due to missing permissions.

**Solution**:
1. Grant Accessibility permission:
   - System Settings → Privacy & Security → Accessibility
   - Click lock icon (enter password)
   - Click "+" button
   - Add `/Applications/Utilities/Terminal.app`
   - Enable checkbox
2. Restart the application
3. The app should start without crashing

**Prevention**: Always grant Accessibility permission BEFORE running the app for the first time (see Step 6).

### "java: command not found"

**Problem**: Java is not installed or not in PATH.

**Solution**:
```bash
# Check if Java is installed
/usr/libexec/java_home -V

# If installed but not in PATH, add to ~/.zshrc:
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Hotkey Not Working

**Problem**: Accessibility permission not granted or app didn't start hotkey manager.

**Solution**:
1. Check logs for permission errors:
   ```bash
   tail -50 ~/Applications/speakToMack/logs/speakToMack.log | grep -i "hotkey\|permission"
   ```
2. If you see "Global key hook permission denied":
   - System Settings → Privacy & Security → Accessibility
   - Verify Terminal is in the list and enabled
   - Restart the application
3. If no errors, verify hotkey configuration in `application-local.properties`

### No Text Appears When Speaking

**Problem**: Multiple possible causes.

**Diagnostic steps**:
```bash
# 1. Check if app is running
curl http://localhost:8080/actuator/health

# 2. Check microphone permission
# System Settings → Privacy & Security → Microphone
# Verify "java" is enabled

# 3. Check logs for errors
tail -50 ~/Applications/speakToMack/logs/speakToMack.log

# 4. Test with verbose logging
java -jar speakToMack.jar --logging.level.com.phillippitts.speaktomack=DEBUG
```

**Common causes**:
- Microphone permission denied → Grant permission
- Models not found → Check paths in `application-local.properties`
- Whisper binary not executable → Run `chmod +x tools/whisper.cpp/main`

### Models Not Found Error

**Problem**: Application can't find Vosk or Whisper models.

**Solution**:
```bash
# Verify models exist
ls -lh ~/Applications/speakToMack/models/

# Expected output:
# drwxr-xr-x vosk-model-small-en-us-0.15/
# -rw-r--r-- ggml-base.en.bin (~147 MB)

# If missing, download again:
cd ~/Applications/speakToMack
./setup-models.sh

# Check paths in application-local.properties match actual locations
cat application-local.properties
```

### Whisper Binary Not Found

**Problem**: whisper.cpp binary not built or not executable.

**Solution**:
```bash
# Check if binary exists
ls -lh ~/Applications/speakToMack/tools/whisper.cpp/main

# If missing, rebuild:
cd ~/Applications/speakToMack
./build-whisper.sh

# Make sure it's executable
chmod +x tools/whisper.cpp/main

# On macOS, clear quarantine
xattr -dr com.apple.quarantine tools/whisper.cpp/main
```

### Application Won't Start After macOS Update

**Problem**: macOS security settings cleared permissions.

**Solution**:
1. Re-grant Microphone permission (System Settings → Privacy & Security → Microphone)
2. Re-grant Accessibility permission (System Settings → Privacy & Security → Accessibility)
3. Clear quarantine on Whisper binary: `xattr -dr com.apple.quarantine tools/whisper.cpp/main`
4. Restart application

### High CPU Usage

**Problem**: Transcription engines consuming resources.

**Solution**:
```bash
# Check metrics
curl http://localhost:8080/actuator/metrics/process.cpu.usage

# Reduce concurrency in application-local.properties:
echo "stt.concurrency.vosk-max=2" >> application-local.properties
echo "stt.concurrency.whisper-max=1" >> application-local.properties

# Disable reconciliation (use single engine):
echo "stt.reconciliation.enabled=false" >> application-local.properties
echo "stt.orchestration.primary-engine=vosk" >> application-local.properties
```

---

## Uninstalling

### Remove Application
```bash
# Stop service if running
launchctl unload ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist

# Remove files
rm -rf ~/Applications/speakToMack
rm ~/Library/LaunchAgents/com.phillippitts.speaktomack.plist
```

### Revoke Permissions
1. System Settings → Privacy & Security → Microphone
   - Find and remove "java"
2. System Settings → Privacy & Security → Accessibility
   - Find and remove "java" or "Terminal"

---

## Getting Help

- **User Guide**: [docs/user-guide.md](docs/user-guide.md)
- **FAQ**: [docs/FAQ.md](docs/FAQ.md)
- **Runbooks**: [docs/runbooks/](docs/runbooks/)
- **Issues**: Report bugs at [GitHub Issues](https://github.com/your-org/speakToMack/issues)

---

## Next Steps

Once installed and running:
- Read the [User Guide](docs/user-guide.md) for advanced features
- Explore [Configuration Reference](docs/configuration-reference.md) for customization
- Learn about [Reconciliation Strategies](docs/reconciliation.md) for better accuracy
