# Production Deployment Guide

This guide is for operators deploying speakToMack in production environments.

## Table of Contents
1. [Pre-Deployment Checklist](#pre-deployment-checklist)
2. [Environment Configuration](#environment-configuration)
3. [Deployment Options](#deployment-options)
4. [Monitoring & Health Checks](#monitoring--health-checks)
5. [Logging](#logging)
6. [Security Hardening](#security-hardening)
7. [Performance Tuning](#performance-tuning)
8. [Backup & Recovery](#backup--recovery)
9. [Troubleshooting](#troubleshooting)

---

## Pre-Deployment Checklist

Before deploying to production, verify:

- [ ] Java 21 JRE installed on target system
- [ ] Models downloaded and checksums verified (`./setup-models.sh`)
- [ ] Whisper.cpp binary built for target architecture (`./build-whisper.sh`)
- [ ] Integration tests passed (`./gradlew integrationTest`)
- [ ] Application properties configured with **absolute paths**
- [ ] Production profile enabled (`spring.profiles.active=production`)
- [ ] Log rotation configured
- [ ] Health check endpoint accessible (`/actuator/health`)
- [ ] Prometheus metrics endpoint exposed (`/actuator/prometheus`)
- [ ] Firewall rules configured (if remote monitoring needed)

---

## Environment Configuration

### Critical: Use Absolute Paths in Production

**Problem**: Default `application.properties` uses relative paths that **will not work** when running as a system service.

**Solution**: Create environment-specific configuration.

#### Option 1: Environment Variables (Recommended)

Set these environment variables before starting the application:

```bash
export VOSK_MODEL_PATH=/opt/speaktomack/models/vosk-model-small-en-us-0.15
export WHISPER_MODEL_PATH=/opt/speaktomack/models/ggml-base.en.bin
export WHISPER_BINARY_PATH=/opt/speaktomack/bin/whisper
export SPEAKTOMACK_LOG_PATH=/var/log/speaktomack
export SPRING_PROFILES_ACTIVE=production
```

Update `application.properties` to read from env vars:

```properties
stt.vosk.model-path=${VOSK_MODEL_PATH:models/vosk-model-small-en-us-0.15}
stt.whisper.model-path=${WHISPER_MODEL_PATH:models/ggml-base.en.bin}
stt.whisper.binary-path=${WHISPER_BINARY_PATH:tools/whisper.cpp/main}
logging.file.path=${SPEAKTOMACK_LOG_PATH:/var/log/speaktomack}
```

#### Option 2: External Properties File

Create `/etc/speaktomack/application-production.properties`:

```properties
# Model paths (absolute)
stt.vosk.model-path=/opt/speaktomack/models/vosk-model-small-en-us-0.15
stt.whisper.model-path=/opt/speaktomack/models/ggml-base.en.bin
stt.whisper.binary-path=/opt/speaktomack/bin/whisper

# Logging (production location)
logging.file.path=/var/log/speaktomack

# Server configuration
server.port=8080
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

# Actuator endpoints (expose prometheus for monitoring)
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=never

# Performance tuning
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
stt.parallel.timeout-ms=10000

# Watchdog configuration
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

Launch with:
```bash
java -jar speakToMack.jar \
  -Dspring.profiles.active=production \
  --spring.config.additional-location=/etc/speaktomack/application-production.properties
```

### Production Profile Settings

The `application-production.properties` (in `src/main/resources/`) **currently has a critical issue**:

**Problem**: Hides prometheus endpoint needed for monitoring:
```properties
# CURRENT (BROKEN for monitoring)
management.endpoints.web.exposure.include=health,info
```

**Fix Required**: Update before deploying:
```properties
# CORRECTED (exposes metrics for Prometheus scraping)
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=never
```

### Logging Configuration

**Critical Issue**: Default `log4j2-spring.xml` uses **DEBUG level** which will flood production logs.

**Fix Required**: Create `log4j2-production.xml` in `/etc/speaktomack/`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <Property name="APP_NAME">speakToMack</Property>
        <Property name="LOG_PATTERN">
            %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] [%X{requestId}] %-5level %logger{36} - %msg%n
        </Property>
    </Properties>

    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="${LOG_PATTERN}"/>
        </Console>

        <RollingFile name="RollingFile"
                     fileName="/var/log/speaktomack/speakToMack.log"
                     filePattern="/var/log/speaktomack/speakToMack-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="${LOG_PATTERN}"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                <SizeBasedTriggeringPolicy size="100 MB"/>
            </Policies>
            <DefaultRolloverStrategy max="30"/>
        </RollingFile>

        <Async name="AsyncFile" bufferSize="512">
            <AppenderRef ref="RollingFile"/>
        </Async>
    </Appenders>

    <Loggers>
        <!-- PRODUCTION: Use INFO level (not DEBUG) -->
        <Logger name="com.phillippitts.speaktomack" level="info" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="AsyncFile"/>
        </Logger>

        <Logger name="org.springframework" level="warn"/>
        <Logger name="org.springframework.web" level="warn"/>

        <Root level="info">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="AsyncFile"/>
        </Root>
    </Loggers>
</Configuration>
```

Launch with:
```bash
java -jar speakToMack.jar \
  -Dlogging.config=/etc/speaktomack/log4j2-production.xml \
  -Dspring.profiles.active=production
```

---

## Deployment Options

### Option 1: systemd Service (Linux)

**File**: `/etc/systemd/system/speaktomack.service`

See [deployment/systemd/speaktomack.service](deployment/systemd/speaktomack.service) for complete configuration.

Quick setup:
```bash
# Create deployment directory
sudo mkdir -p /opt/speaktomack/{models,bin,logs}

# Copy JAR
sudo cp build/libs/speakToMack-0.0.1-SNAPSHOT.jar /opt/speaktomack/speaktomack.jar

# Copy models
sudo cp -r models/* /opt/speaktomack/models/

# Copy Whisper binary
sudo cp tools/whisper.cpp/main /opt/speaktomack/bin/whisper
sudo chmod +x /opt/speaktomack/bin/whisper

# Create speaktomack user
sudo useradd -r -s /bin/false speaktomack

# Set permissions
sudo chown -R speaktomack:speaktomack /opt/speaktomack
sudo mkdir -p /var/log/speaktomack
sudo chown speaktomack:speaktomack /var/log/speaktomack

# Copy service file
sudo cp deployment/systemd/speaktomack.service /etc/systemd/system/

# Reload systemd
sudo systemctl daemon-reload

# Enable and start
sudo systemctl enable speaktomack
sudo systemctl start speaktomack

# Check status
sudo systemctl status speaktomack
```

**Service management**:
```bash
# Start
sudo systemctl start speaktomack

# Stop
sudo systemctl stop speaktomack

# Restart
sudo systemctl restart speaktomack

# View logs
sudo journalctl -u speaktomack -f

# View last 100 lines
sudo journalctl -u speaktomack -n 100
```

### Option 2: macOS LaunchDaemon (System-Wide)

**File**: `/Library/LaunchDaemons/com.phillippitts.speaktomack.plist`

See [deployment/macos/com.phillippitts.speaktomack.plist](deployment/macos/com.phillippitts.speaktomack.plist) for complete configuration.

Quick setup:
```bash
# Create deployment directory
sudo mkdir -p /opt/speaktomack/{models,bin,logs}

# Copy files (same as Linux)
sudo cp build/libs/speakToMack-0.0.1-SNAPSHOT.jar /opt/speaktomack/speaktomack.jar
sudo cp -r models/* /opt/speaktomack/models/
sudo cp tools/whisper.cpp/main /opt/speaktomack/bin/whisper
sudo chmod +x /opt/speaktomack/bin/whisper

# Clear quarantine
sudo xattr -dr com.apple.quarantine /opt/speaktomack/bin/whisper

# Copy LaunchDaemon
sudo cp deployment/macos/com.phillippitts.speaktomack.plist /Library/LaunchDaemons/

# Load service
sudo launchctl load /Library/LaunchDaemons/com.phillippitts.speaktomack.plist

# Check if running
ps aux | grep speaktomack
```

**Service management**:
```bash
# Start
sudo launchctl start com.phillippitts.speaktomack

# Stop
sudo launchctl stop com.phillippitts.speaktomack

# Unload (disable)
sudo launchctl unload /Library/LaunchDaemons/com.phillippitts.speaktomack.plist

# Reload (enable)
sudo launchctl load /Library/LaunchDaemons/com.phillippitts.speaktomack.plist

# View logs
tail -f /var/log/speaktomack/speakToMack.log
```

### Option 3: Docker Container (Future)

**Status**: Not yet implemented. Planned for Phase 6.

Challenges for desktop audio app in container:
- Accessing host microphone (requires `--device` mapping)
- Global hotkey detection (needs host key events)
- Typing to foreground app (needs host accessibility)

Recommendation: Use native systemd/LaunchDaemon for now.

---

## Monitoring & Health Checks

### Health Check Endpoint

**URL**: `http://localhost:8080/actuator/health`

**Expected response** (healthy):
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "models": {
      "status": "UP",
      "details": {
        "status": "All models and binaries accessible",
        "voskModel": "accessible at /opt/speaktomack/models/vosk-model-small-en-us-0.15",
        "whisperModel": "accessible at /opt/speaktomack/models/ggml-base.en.bin",
        "whisperBinary": "accessible and executable at /opt/speaktomack/bin/whisper"
      }
    },
    "sttEngines": {
      "status": "UP",
      "details": {
        "status": "All engines operational",
        "vosk": "UP (enabled)",
        "whisper": "UP (enabled)"
      }
    }
  }
}
```

**Degraded state** (one engine down):
```json
{
  "status": "DEGRADED",
  "components": {
    "sttEngines": {
      "status": "DEGRADED",
      "details": {
        "status": "Partial engine availability",
        "vosk": "UP (enabled)",
        "whisper": "DOWN (disabled by watchdog)"
      }
    }
  }
}
```

### Metrics Endpoint (Prometheus)

**URL**: `http://localhost:8080/actuator/prometheus`

**Key metrics to monitor**:

```
# Transcription latency (milliseconds)
speaktomack_transcription_latency_seconds{engine="vosk",quantile="0.95"}
speaktomack_transcription_latency_seconds{engine="whisper",quantile="0.95"}

# Success rate
speaktomack_transcription_success_total{engine="vosk"}
speaktomack_transcription_success_total{engine="whisper"}

# Failure rate
speaktomack_transcription_failure_total{engine="vosk",reason="timeout"}
speaktomack_transcription_failure_total{engine="whisper",reason="transcription_error"}

# Reconciliation
speaktomack_transcription_reconciliation_total{strategy="SIMPLE",selected="vosk"}

# System metrics
process_cpu_usage
jvm_memory_used_bytes{area="heap"}
```

### Prometheus Configuration Example

Add to `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'speaktomack'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### Alerting Rules (Example)

```yaml
groups:
  - name: speaktomack
    rules:
      # Alert if both engines down
      - alert: SpeakToMackDown
        expr: up{job="speaktomack"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "speakToMack is down"

      # Alert if failure rate > 10%
      - alert: HighTranscriptionFailureRate
        expr: |
          (
            rate(speaktomack_transcription_failure_total[5m])
            /
            rate(speaktomack_transcription_success_total[5m] + speaktomack_transcription_failure_total[5m])
          ) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transcription failure rate"

      # Alert if p95 latency > 3 seconds
      - alert: HighTranscriptionLatency
        expr: |
          speaktomack_transcription_latency_seconds{quantile="0.95"} > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transcription latency"

      # Alert if watchdog cooldown triggered
      - alert: EngineInCooldown
        expr: |
          increase(speaktomack_transcription_failure_total{reason="watchdog_cooldown"}[10m]) > 0
        labels:
          severity: warning
        annotations:
          summary: "STT engine in watchdog cooldown"
```

---

## Logging

### Log Locations

Default locations based on deployment method:

| Deployment Method | Log Location |
|-------------------|--------------|
| systemd | `/var/log/speaktomack/speakToMack.log` |
| macOS LaunchDaemon | `/var/log/speaktomack/speakToMack.log` |
| Docker | stdout (captured by Docker) |
| Manual | `./logs/speakToMack.log` (relative to working directory) |

### Log Rotation

**Built-in rotation** (Log4j2):
- Max size: 100 MB per file
- Retention: 30 days
- Compression: gzip

**System log rotation** (Linux):

Create `/etc/logrotate.d/speaktomack`:

```
/var/log/speaktomack/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 speaktomack speaktomack
    sharedscripts
    postrotate
        systemctl reload speaktomack > /dev/null 2>&1 || true
    endscript
}
```

### Log Levels by Component

Production logging (`INFO` level) includes:

| Component | Logged Events |
|-----------|---------------|
| Startup | Model validation, engine initialization, health check results |
| Transcription | Duration, character count, engine used (no actual text) |
| Watchdog | Engine restarts, cooldown triggers, recovery events |
| Errors | Exception stack traces, error context (engine, duration, exit codes) |
| Security | Permission denied events, accessibility issues |

**Privacy guarantee**: Full transcribed text is **never logged** at INFO level.

### Viewing Logs

```bash
# Tail live logs
sudo tail -f /var/log/speaktomack/speakToMack.log

# Search for errors
sudo grep ERROR /var/log/speaktomack/speakToMack.log

# Search for watchdog events
sudo grep "watchdog" /var/log/speaktomack/speakToMack.log

# Search for specific engine failures
sudo grep "engine=whisper" /var/log/speaktomack/speakToMack.log | grep FAIL

# View systemd journal (if using systemd)
sudo journalctl -u speaktomack -f --since "1 hour ago"
```

---

## Security Hardening

### Principle of Least Privilege

1. **Run as dedicated user** (not root):
```bash
# Linux
sudo useradd -r -s /bin/false speaktomack

# macOS (service runs as _speaktomack)
sudo dscl . -create /Users/_speaktomack
sudo dscl . -create /Users/_speaktomack UserShell /usr/bin/false
```

2. **Restrict file permissions**:
```bash
# Application files (read-only for service user)
sudo chown -R root:speaktomack /opt/speaktomack
sudo chmod -R 750 /opt/speaktomack

# JAR should not be writable
sudo chmod 640 /opt/speaktomack/speaktomack.jar

# Logs directory (writable by service)
sudo chown speaktomack:speaktomack /var/log/speaktomack
sudo chmod 750 /var/log/speaktomack
```

### Network Security

**Default configuration**: Binds to `localhost:8080` (not accessible from network).

**If remote monitoring needed**, use firewall rules:

```bash
# Linux (iptables) - Allow Prometheus server only
sudo iptables -A INPUT -p tcp -s 10.0.1.100 --dport 8080 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 8080 -j DROP

# macOS (pf) - Add to /etc/pf.conf
pass in proto tcp from 10.0.1.100 to any port 8080
block in proto tcp to any port 8080
```

**Recommendation**: Use TLS termination proxy (nginx, Caddy) if exposing externally.

### Dependency Scanning

```bash
# Check for CVEs in dependencies
./gradlew dependencyCheckAnalyze

# Update dependencies regularly
./gradlew dependencyUpdates
```

---

## Performance Tuning

### JVM Tuning

**Recommended JVM flags** for production:

```bash
java -jar speakToMack.jar \
  -Xms512m \                          # Initial heap
  -Xmx2g \                            # Max heap (adjust based on load)
  -XX:+UseG1GC \                      # G1 garbage collector (good for responsiveness)
  -XX:MaxGCPauseMillis=200 \          # Target GC pause time
  -XX:+HeapDumpOnOutOfMemoryError \   # Debug OOM errors
  -XX:HeapDumpPath=/var/log/speaktomack/heap-dump.hprof \
  -Dspring.profiles.active=production
```

### Concurrency Limits

Adjust based on CPU cores and expected load:

```properties
# Conservative (low-end machines, 2-4 cores)
stt.concurrency.vosk-max=2
stt.concurrency.whisper-max=1

# Balanced (4-8 cores)
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2

# Aggressive (8+ cores, high throughput)
stt.concurrency.vosk-max=8
stt.concurrency.whisper-max=4
```

### Disabling Reconciliation

For **maximum speed** (50% faster), use single engine:

```properties
stt.reconciliation.enabled=false
stt.orchestration.primary-engine=vosk    # Vosk is faster (~100ms vs 1-2s)
```

Trade-off: Lower accuracy (no Whisper cross-validation).

---

## Backup & Recovery

### What to Back Up

**Critical files**:
- JAR: `/opt/speaktomack/speaktomack.jar`
- Models: `/opt/speaktomack/models/*` (~200 MB)
- Configuration: `/etc/speaktomack/application-production.properties`
- Whisper binary: `/opt/speaktomack/bin/whisper`

**Not critical** (can be regenerated):
- Logs: `/var/log/speaktomack/`
- Build artifacts: `build/`

### Backup Script Example

```bash
#!/bin/bash
# /usr/local/bin/backup-speaktomack.sh

BACKUP_DIR=/backup/speaktomack/$(date +%Y%m%d)
mkdir -p $BACKUP_DIR

# Backup application
tar -czf $BACKUP_DIR/app.tar.gz /opt/speaktomack

# Backup config
cp /etc/speaktomack/application-production.properties $BACKUP_DIR/

# Keep last 7 days
find /backup/speaktomack -type d -mtime +7 -exec rm -rf {} +
```

### Recovery Procedure

```bash
# 1. Stop service
sudo systemctl stop speaktomack

# 2. Restore files
sudo tar -xzf /backup/speaktomack/20251020/app.tar.gz -C /
sudo cp /backup/speaktomack/20251020/application-production.properties /etc/speaktomack/

# 3. Verify permissions
sudo chown -R speaktomack:speaktomack /opt/speaktomack
sudo chmod +x /opt/speaktomack/bin/whisper

# 4. Start service
sudo systemctl start speaktomack

# 5. Verify health
curl http://localhost:8080/actuator/health
```

---

## Troubleshooting

### Service Won't Start

**Diagnostic steps**:

```bash
# Check systemd status
sudo systemctl status speaktomack

# View full logs
sudo journalctl -u speaktomack -n 100 --no-pager

# Common issues:
# 1. Models not found - check paths
ls -lh /opt/speaktomack/models/

# 2. Whisper binary not executable
ls -lh /opt/speaktomack/bin/whisper
sudo chmod +x /opt/speaktomack/bin/whisper

# 3. Permission denied
sudo chown -R speaktomack:speaktomack /opt/speaktomack
```

### High Failure Rate

```bash
# Check metrics
curl http://localhost:8080/actuator/prometheus | grep failure

# Check logs for patterns
sudo grep FAIL /var/log/speaktomack/speakToMack.log | tail -20

# Common causes:
# - Timeout too low: increase stt.parallel.timeout-ms
# - Concurrency limit hit: increase stt.concurrency.* limits
# - Watchdog cooldown: wait 10 minutes or restart service
```

### Memory Issues

```bash
# Check heap usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq

# Analyze heap dump (if OOM occurred)
jhat /var/log/speaktomack/heap-dump.hprof

# Fix: Increase heap
# Edit service file, add: -Xmx4g
```

### Metrics Not Appearing in Prometheus

**Problem**: Prometheus endpoint hidden by production profile.

**Fix**: Update `src/main/resources/application-production.properties`:

```properties
# OLD (BROKEN)
management.endpoints.web.exposure.include=health,info

# NEW (CORRECT)
management.endpoints.web.exposure.include=health,info,prometheus
```

Rebuild JAR and redeploy.

---

## Upgrading

### Safe Upgrade Procedure

```bash
# 1. Back up current version
sudo /usr/local/bin/backup-speaktomack.sh

# 2. Test new version in staging
# (deploy to test system first)

# 3. Stop production service
sudo systemctl stop speaktomack

# 4. Replace JAR
sudo cp speakToMack-NEW.jar /opt/speaktomack/speaktomack.jar

# 5. Update models if needed
sudo cp -r models/* /opt/speaktomack/models/

# 6. Update Whisper binary if needed
sudo cp tools/whisper.cpp/main /opt/speaktomack/bin/whisper
sudo chmod +x /opt/speaktomack/bin/whisper

# 7. Check configuration compatibility
# (review CHANGELOG for breaking changes)

# 8. Start service
sudo systemctl start speaktomack

# 9. Verify health
curl http://localhost:8080/actuator/health

# 10. Monitor for issues
sudo journalctl -u speaktomack -f
```

### Rollback Procedure

```bash
# 1. Stop service
sudo systemctl stop speaktomack

# 2. Restore from backup
sudo tar -xzf /backup/speaktomack/YYYYMMDD/app.tar.gz -C /

# 3. Start service
sudo systemctl start speaktomack

# 4. Verify
curl http://localhost:8080/actuator/health
```

---

## Production Readiness Checklist

Before going live:

**Configuration**:
- [ ] Absolute paths configured for models and binaries
- [ ] Production profile enabled
- [ ] Logging level set to INFO (not DEBUG)
- [ ] Prometheus endpoint exposed
- [ ] Appropriate concurrency limits set

**Security**:
- [ ] Service runs as dedicated user (not root)
- [ ] File permissions restricted
- [ ] Network access restricted (firewall rules)
- [ ] Dependencies scanned for CVEs

**Monitoring**:
- [ ] Health checks automated
- [ ] Metrics scraped by Prometheus
- [ ] Alerts configured (failure rate, latency, downtime)
- [ ] Logs forwarded to centralized system (optional)

**Reliability**:
- [ ] Service auto-starts on boot
- [ ] Graceful shutdown configured
- [ ] Watchdog enabled with reasonable limits
- [ ] Backup procedure tested
- [ ] Recovery procedure documented and tested

**Performance**:
- [ ] JVM heap size tuned
- [ ] Concurrency limits tested under load
- [ ] Log rotation configured
- [ ] Disk space monitored

---

## Getting Help

- **Operator Guide**: [docs/operator-guide.md](docs/operator-guide.md)
- **Runbooks**: [docs/runbooks/](docs/runbooks/)
- **Architecture**: [docs/diagrams/architecture-overview.md](docs/diagrams/architecture-overview.md)
- **Issue Tracker**: [GitHub Issues](https://github.com/your-org/speakToMack/issues)
