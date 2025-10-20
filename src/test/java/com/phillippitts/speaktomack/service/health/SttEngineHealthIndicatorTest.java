package com.phillippitts.speaktomack.service.health;

import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SttEngineHealthIndicatorTest {

    @Test
    void shouldReportUpWhenBothEnginesHealthyAndEnabled() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(true);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(true);
        when(vosk.isHealthy()).thenReturn(true);
        when(whisper.isHealthy()).thenReturn(true);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "Both engines operational");
        assertThat(health.getDetails()).containsEntry("vosk", "ready");
        assertThat(health.getDetails()).containsEntry("whisper", "ready");
    }

    @Test
    void shouldReportDegradedWhenOnlyVoskReady() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(true);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(false);
        when(vosk.isHealthy()).thenReturn(true);
        when(whisper.isHealthy()).thenReturn(false);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("status", "Partial engine availability");
        assertThat(health.getDetails()).containsEntry("vosk", "ready");
        assertThat(health.getDetails()).containsEntry("whisper", "disabled");
    }

    @Test
    void shouldReportDegradedWhenOnlyWhisperReady() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(false);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(true);
        when(vosk.isHealthy()).thenReturn(false);
        when(whisper.isHealthy()).thenReturn(true);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("status", "Partial engine availability");
        assertThat(health.getDetails()).containsEntry("vosk", "disabled");
        assertThat(health.getDetails()).containsEntry("whisper", "ready");
    }

    @Test
    void shouldReportDegradedWhenVoskEnabledButUnhealthyAndWhisperReady() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(true);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(true);
        when(vosk.isHealthy()).thenReturn(false);
        when(whisper.isHealthy()).thenReturn(true);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("status", "Partial engine availability");
        assertThat(health.getDetails()).containsEntry("vosk", "unhealthy");
        assertThat(health.getDetails()).containsEntry("whisper", "ready");
    }

    @Test
    void shouldReportDownWhenBothEnginesDisabled() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(false);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(false);
        when(vosk.isHealthy()).thenReturn(true);
        when(whisper.isHealthy()).thenReturn(true);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "No engines available");
        assertThat(health.getDetails()).containsEntry("vosk", "disabled");
        assertThat(health.getDetails()).containsEntry("whisper", "disabled");
    }

    @Test
    void shouldReportDownWhenBothEnginesUnhealthy() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(true);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(true);
        when(vosk.isHealthy()).thenReturn(false);
        when(whisper.isHealthy()).thenReturn(false);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "No engines available");
        assertThat(health.getDetails()).containsEntry("vosk", "unhealthy");
        assertThat(health.getDetails()).containsEntry("whisper", "unhealthy");
    }

    @Test
    void shouldReportDownWhenBothEnginesEnabledButUnhealthy() {
        SttEngine vosk = mock(SttEngine.class);
        SttEngine whisper = mock(SttEngine.class);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);

        when(watchdog.isEngineEnabled("vosk")).thenReturn(true);
        when(watchdog.isEngineEnabled("whisper")).thenReturn(true);
        when(vosk.isHealthy()).thenReturn(false);
        when(whisper.isHealthy()).thenReturn(false);

        SttEngineHealthIndicator indicator = new SttEngineHealthIndicator(vosk, whisper, watchdog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "No engines available");
        assertThat(health.getDetails()).containsEntry("vosk", "unhealthy");
        assertThat(health.getDetails()).containsEntry("whisper", "unhealthy");
    }
}
