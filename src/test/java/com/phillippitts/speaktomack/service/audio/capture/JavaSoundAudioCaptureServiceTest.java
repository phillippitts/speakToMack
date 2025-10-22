package com.phillippitts.speaktomack.service.audio.capture;

import com.phillippitts.speaktomack.config.properties.AudioCaptureProperties;
import com.phillippitts.speaktomack.config.properties.AudioValidationProperties;
import com.phillippitts.speaktomack.service.validation.AudioValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSoundAudioCaptureServiceTest {

    @Test
    void startStopReadAllProducesPcm() throws Exception {
        // Arrange: small properties for fast test
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(50); // Lower min to account for CI thread scheduling
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger events = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> events.incrementAndGet();

        // Fake provider that returns a repeating data line
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        Thread.sleep(250); // Generous sleep for CI (capture thread startup + data capture)
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        assertThat(pcm).isNotNull();
        assertThat(pcm.length).isGreaterThan(0);
        // PCM must be block aligned (2 bytes)
        assertThat(pcm.length % 2).isEqualTo(0);
        assertThat(events.get()).isEqualTo(0); // no errors
    }

    @Test
    void singleActiveSessionOnly() throws Exception {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(50); // Lower min to account for CI thread scheduling
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> { };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        Thread.sleep(150); // Give capture thread time to fully initialize (longer for CI)
        assertThatThrownBy(svc::startSession)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
        Thread.sleep(150); // Capture enough data to pass validation (generous for CI)
        svc.stopSession(id);
        svc.readAll(id);
    }

    @Test
    void invalidAudioRejectedByValidator() throws Exception {
        // Arrange: validator with strict min duration
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(10000); // 10 seconds minimum
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> { };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act: capture for only 50ms (way below 10s minimum)
        UUID id = svc.startSession();
        Thread.sleep(50);
        svc.stopSession(id);

        // Assert: validator.validate() throws InvalidAudioException
        assertThatThrownBy(() -> svc.readAll(id))
                .isInstanceOf(com.phillippitts.speaktomack.exception.InvalidAudioException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void permissionDeniedPublishesEvent() {
        // Arrange: provider that throws SecurityException
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(250);
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger eventCount = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof CaptureErrorEvent) {
                CaptureErrorEvent evt = (CaptureErrorEvent) e;
                if ("MIC_PERMISSION_DENIED".equals(evt.reason())) {
                    eventCount.incrementAndGet();
                }
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            throw new SecurityException("Microphone access denied");
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act: start session (capture thread will fail with SecurityException)
        UUID id = svc.startSession();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        svc.stopSession(id);

        // Assert: CaptureErrorEvent with MIC_PERMISSION_DENIED published
        assertThat(eventCount.get()).isGreaterThan(0);
    }

    @Test
    void deviceUnavailablePublishesEvent() {
        // Arrange: provider that throws LineUnavailableException
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(250);
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger eventCount = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof CaptureErrorEvent) {
                CaptureErrorEvent evt = (CaptureErrorEvent) e;
                if ("MIC_UNAVAILABLE".equals(evt.reason())) {
                    eventCount.incrementAndGet();
                }
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            throw new LineUnavailableException("No audio device available");
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act
        UUID id = svc.startSession();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        svc.stopSession(id);

        // Assert
        assertThat(eventCount.get()).isGreaterThan(0);
    }

    @Test
    void maxDurationEnforcesHardStop() throws Exception {
        // Arrange: very short max duration (600ms)
        AudioCaptureProperties props = new AudioCaptureProperties(20, 600, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(100); // Allow shorter durations for testing
        vprops.setMaxDurationMs(60000); // Validator max higher than capture max
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> { };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act: start session and wait longer than max duration
        UUID id = svc.startSession();
        Thread.sleep(1200); // Wait 1.2 seconds (longer than 600ms max)
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        // Assert: captured audio should be ~600ms worth of data (not 1200ms)
        // At 16kHz, 16-bit, mono: 32,000 bytes/sec = 19,200 bytes for 600ms
        int expectedMaxBytes = (600 * 32000) / 1000; // ~19,200 bytes
        assertThat(pcm.length).isLessThanOrEqualTo(expectedMaxBytes + 2000); // Allow small buffer
        assertThat(pcm.length).isGreaterThan(3000); // Should have captured something substantial
    }

    @Test
    void canceledSessionThrowsOnRead() throws Exception {
        // Arrange
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(250);
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> { };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act: start, cancel, attempt to read
        UUID id = svc.startSession();
        Thread.sleep(50);
        svc.cancelSession(id);

        // Assert: readAll() throws IllegalStateException
        assertThatThrownBy(() -> svc.readAll(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canceled");
    }

    @Test
    void readAllWhileActiveThrows() throws Exception {
        // Arrange
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(250);
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> { };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act: start session but don't stop
        UUID id = svc.startSession();
        Thread.sleep(100);

        // Assert: readAll() throws IllegalStateException while active
        assertThatThrownBy(() -> svc.readAll(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still active");

        // Cleanup: stop the session
        svc.stopSession(id);
    }

    @Test
    void shutdownTerminatesCaptureThread() throws Exception {
        // Arrange
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties();
        vprops.setMinDurationMs(250);
        vprops.setMaxDurationMs(60000);
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> { };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Act: start session and call shutdown (simulating @PreDestroy)
        UUID id = svc.startSession();
        Thread.sleep(100); // Let capture thread initialize
        svc.shutdown();

        // Assert: capture thread should terminate within timeout
        // Poll briefly to verify thread terminates
        boolean terminated = false;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            // Check if we can start a new session (indicating full cleanup)
            try {
                UUID newId = svc.startSession();
                svc.stopSession(newId);
                terminated = true;
                break;
            } catch (IllegalStateException e) {
                // Still cleaning up, retry
            }
        }
        assertThat(terminated).as("Capture thread should terminate after shutdown").isTrue();
    }

    // --- Test doubles ---
    static final class RepeatingTargetDataLine implements TargetDataLine {
        private final javax.sound.sampled.AudioFormat fmt;
        private boolean started;
        private boolean open;
        private final byte[] pattern;
        private int pos = 0;

        RepeatingTargetDataLine(javax.sound.sampled.AudioFormat fmt) {
            this.fmt = fmt;
            this.pattern = new byte[320]; // ~10ms at 16k mono 16-bit
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) (i & 0xFF);
            }
        }

        @Override public javax.sound.sampled.AudioFormat getFormat() {
            return fmt;
        }
        @Override public void open(javax.sound.sampled.AudioFormat format, int bufferSize) {
            open = true;
        }
        @Override public void open(javax.sound.sampled.AudioFormat format) {
            open = true;
        }
        @Override public int read(byte[] b, int off, int len) {
            if (!started || !open) {
                return 0;
            }
            // Throttle to simulate real-time audio capture (~10ms per read)
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignore) {
            }
            int n = Math.min(len, pattern.length);
            // copy from pattern cyclically
            if (pos + n <= pattern.length) {
                System.arraycopy(pattern, pos, b, off, n);
                pos = (pos + n) % pattern.length;
            } else {
                int first = pattern.length - pos;
                System.arraycopy(pattern, pos, b, off, first);
                System.arraycopy(pattern, 0, b, off + first, n - first);
                pos = (n - first);
            }
            return n;
        }
        @Override public void start() {
            started = true;
        }
        @Override public void stop() {
            started = false;
        }
        @Override public void close() {
            open = false;
        }
        @Override public boolean isOpen() {
            return open;
        }
        @Override public int available() {
            return 0;
        }
        @Override public void drain() {
        }
        @Override public void flush() {
        }
        @Override public int getBufferSize() {
            return 0;
        }
        @Override public int getFramePosition() {
            return 0;
        }
        @Override public float getLevel() {
            return 0;
        }
        @Override public long getLongFramePosition() {
            return 0;
        }
        @Override public Control getControl(Control.Type control) {
            throw new IllegalArgumentException();
        }
        @Override public Control[] getControls() {
            return new Control[0];
        }
        @Override public boolean isControlSupported(Control.Type control) {
            return false;
        }
        @Override public void addLineListener(LineListener listener) {
        }
        @Override public void removeLineListener(LineListener listener) {
        }
        @Override public javax.sound.sampled.Line.Info getLineInfo() {
            return new DataLine.Info(TargetDataLine.class, fmt);
        }
        @Override public void open() {
            open = true;
        }
        @Override public boolean isActive() {
            return started;
        }
        @Override public boolean isRunning() {
            return started;
        }
        @Override public long getMicrosecondPosition() {
            return 0L;
        }
    }
}
