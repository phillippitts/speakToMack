package com.phillippitts.speaktomack.service.health;

import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for STT engines (Vosk and Whisper).
 *
 * <p>Reports engine health status for monitoring and alerting:
 * <ul>
 *   <li>UP: Both engines healthy and enabled</li>
 *   <li>DEGRADED: At least one engine healthy</li>
 *   <li>DOWN: Both engines unhealthy or disabled</li>
 * </ul>
 *
 * <p>Exposed via /actuator/health endpoint.
 */
@Component
public class SttEngineHealthIndicator implements HealthIndicator {

    private static final String ENGINE_VOSK = "vosk";
    private static final String ENGINE_WHISPER = "whisper";

    private final SttEngine vosk;
    private final SttEngine whisper;
    private final SttEngineWatchdog watchdog;

    public SttEngineHealthIndicator(SttEngine voskSttEngine,
                                    SttEngine whisperSttEngine,
                                    SttEngineWatchdog watchdog) {
        this.vosk = voskSttEngine;
        this.whisper = whisperSttEngine;
        this.watchdog = watchdog;
    }

    @Override
    public Health health() {
        boolean voskEnabled = watchdog.isEngineEnabled(ENGINE_VOSK);
        boolean whisperEnabled = watchdog.isEngineEnabled(ENGINE_WHISPER);
        boolean voskHealthy = vosk.isHealthy();
        boolean whisperHealthy = whisper.isHealthy();

        boolean voskReady = voskEnabled && voskHealthy;
        boolean whisperReady = whisperEnabled && whisperHealthy;

        // Build health status with detailed information
        Health.Builder builder = new Health.Builder();

        if (voskReady && whisperReady) {
            builder.up()
                    .withDetail("status", "Both engines operational")
                    .withDetail("vosk", buildEngineStatus(voskEnabled, voskHealthy))
                    .withDetail("whisper", buildEngineStatus(whisperEnabled, whisperHealthy));
        } else if (voskReady || whisperReady) {
            builder.status("DEGRADED")
                    .withDetail("status", "Partial engine availability")
                    .withDetail("vosk", buildEngineStatus(voskEnabled, voskHealthy))
                    .withDetail("whisper", buildEngineStatus(whisperEnabled, whisperHealthy));
        } else {
            builder.down()
                    .withDetail("status", "No engines available")
                    .withDetail("vosk", buildEngineStatus(voskEnabled, voskHealthy))
                    .withDetail("whisper", buildEngineStatus(whisperEnabled, whisperHealthy));
        }

        return builder.build();
    }

    private String buildEngineStatus(boolean enabled, boolean healthy) {
        if (!enabled) {
            return "disabled";
        }
        return healthy ? "ready" : "unhealthy";
    }
}
