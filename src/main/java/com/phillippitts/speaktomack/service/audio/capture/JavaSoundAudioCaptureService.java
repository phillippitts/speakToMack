package com.phillippitts.speaktomack.service.audio.capture;

import com.phillippitts.speaktomack.config.audio.AudioCaptureProperties;
import com.phillippitts.speaktomack.service.audio.AudioFormat;
import com.phillippitts.speaktomack.service.validation.AudioValidator;
import com.phillippitts.speaktomack.util.ProcessTimeouts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BYTE_RATE;

/**
 * Java Sound based microphone capture that produces raw PCM16LE mono @16kHz.
 * Thread-safe for single active session. Designed for push-to-talk UX.
 *
 * <p>This is the default implementation of {@link AudioCaptureService}.
 * Test configurations can provide alternative implementations by marking them as @Primary.
 */
@Service
public class JavaSoundAudioCaptureService implements AudioCaptureService {

    private static final Logger LOG = LogManager.getLogger(JavaSoundAudioCaptureService.class);

    /** Abstraction to open a TargetDataLine (for testing). */
    public interface DataLineProvider {
        TargetDataLine open(javax.sound.sampled.AudioFormat format, Optional<String> deviceName)
                throws LineUnavailableException;
    }

    private final AudioCaptureProperties props;
    private final AudioValidator validator;
    private final ApplicationEventPublisher publisher;
    private final DataLineProvider provider;

    private final Object lock = new Object();
    private Session current;

    @org.springframework.beans.factory.annotation.Autowired
    public JavaSoundAudioCaptureService(AudioCaptureProperties props,
                                        AudioValidator validator,
                                        ApplicationEventPublisher publisher) {
        this(props, validator, publisher, defaultProvider());
    }

    // Package-private for tests
    JavaSoundAudioCaptureService(AudioCaptureProperties props,
                                 AudioValidator validator,
                                 ApplicationEventPublisher publisher,
                                 DataLineProvider provider) {
        this.props = Objects.requireNonNull(props);
        this.validator = Objects.requireNonNull(validator);
        this.publisher = Objects.requireNonNull(publisher);
        this.provider = Objects.requireNonNull(provider);
    }

    @PostConstruct
    public void logSystemInfo() {
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        int mixerCount = AudioSystem.getMixerInfo().length;
        String device = props.getDeviceName() != null ? props.getDeviceName() : "default";

        LOG.info("Audio capture initialized: OS={}, arch={}, device='{}', available-mixers={}, "
                + "chunk={}ms, max-duration={}ms",
                os, arch, device, mixerCount, props.getChunkMillis(), props.getMaxDurationMs());
    }

    @PreDestroy
    public void shutdown() {
        Thread captureThread = null;
        synchronized (lock) {
            if (current != null && current.active.get()) {
                LOG.info("Shutting down with active session {}; forcing cleanup", current.id);
                current.active.set(false);
                captureThread = current.thread;
                cleanup();
            }
        }
        // Join thread outside lock to avoid deadlock
        if (captureThread != null) {
            joinThread(captureThread, ProcessTimeouts.CAPTURE_THREAD_SHUTDOWN_TIMEOUT.toMillis());
        }
    }

    private static DataLineProvider defaultProvider() {
        return (format, device) -> {
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            TargetDataLine line = null;
            if (device.isPresent()) {
                for (Mixer.Info info : mixers) {
                    if (info.getName().equalsIgnoreCase(device.get())) {
                        Mixer m = AudioSystem.getMixer(info);
                        line = (TargetDataLine) m.getLine(new DataLine.Info(TargetDataLine.class, format));
                        break;
                    }
                }
            }
            if (line == null) {
                line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
            }
            line.open(format);
            return line;
        };
    }

    @Override
    public UUID startSession() {
        synchronized (lock) {
            if (current != null && current.active.get()) {
                throw new IllegalStateException("Another capture session is already active");
            }
            UUID id = UUID.randomUUID();
            current = new Session(id);
            startCaptureThread(current);
            return id;
        }
    }

    @Override
    public void stopSession(UUID sessionId) {
        Thread captureThread;
        synchronized (lock) {
            ensureSession(sessionId);
            current.active.set(false);
            captureThread = current.thread;
        }
        // Join thread outside lock to ensure clean termination before readAll()
        if (captureThread != null) {
            joinThread(captureThread, ProcessTimeouts.CAPTURE_THREAD_STOP_TIMEOUT.toMillis());
        }
    }

    @Override
    public void cancelSession(UUID sessionId) {
        synchronized (lock) {
            ensureSession(sessionId);
            current.canceled = true;
            current.active.set(false);
            // Clear buffer immediately to discard captured data
            if (current.buffer != null) {
                current.buffer.clear();
            }
        }
    }

    @Override
    public byte[] readAll(UUID sessionId) {
        synchronized (lock) {
            ensureSession(sessionId);
            if (current.active.get()) {
                throw new IllegalStateException("Capture still active; stop before reading");
            }
            if (current.canceled) {
                cleanup();
                throw new IllegalStateException("Session was canceled; no data available");
            }
            try {
                byte[] data = current.buffer.toByteArray();
                validator.validate(data);
                return data;
            } finally {
                cleanup();
            }
        }
    }

    private void startCaptureThread(Session s) {
        final int bytesPerChunk = (props.getChunkMillis() * REQUIRED_BYTE_RATE) / 1000;
        final javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(
                AudioFormat.REQUIRED_SAMPLE_RATE,
                AudioFormat.REQUIRED_BITS_PER_SAMPLE,
                AudioFormat.REQUIRED_CHANNELS,
                AudioFormat.REQUIRED_SIGNED,
                AudioFormat.REQUIRED_BIG_ENDIAN
        );
        final int capacity = (props.getMaxDurationMs() * REQUIRED_BYTE_RATE) / 1000;
        s.buffer = new PcmRingBuffer(capacity);
        s.active.set(true);

        Thread t = new Thread(() -> doCapture(s, fmt, bytesPerChunk), "audio-capture");
        t.setDaemon(true);
        s.thread = t;
        t.start();
    }

    private void doCapture(Session s, javax.sound.sampled.AudioFormat fmt, int bytesPerChunk) {
        TargetDataLine line = null;
        try {
            line = provider.open(fmt, Optional.ofNullable(props.getDeviceName()));
            line.start();
            byte[] buf = new byte[bytesPerChunk];
            final long hardStopBytes = ((long) props.getMaxDurationMs() * REQUIRED_BYTE_RATE) / 1000L;
            long written = 0;
            while (s.active.get()) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) {
                    continue;
                }
                s.buffer.write(buf, 0, n);
                written += n;
                LOG.debug("Audio capture: read {} bytes, total written {} bytes", n, written);
                if (written >= hardStopBytes) {
                    LOG.info("Max capture duration reached ({} ms)", props.getMaxDurationMs());
                    s.active.set(false);
                    break;
                }
            }
            LOG.info("Audio capture completed: total {} bytes captured", written);
        } catch (LineUnavailableException e) {
            LOG.warn("Microphone unavailable: {}", e.getMessage());
            publisher.publishEvent(new CaptureErrorEvent("MIC_UNAVAILABLE", Instant.now()));
        } catch (SecurityException se) {
            LOG.warn("Microphone access denied: {}", se.getMessage());
            publisher.publishEvent(new CaptureErrorEvent("MIC_PERMISSION_DENIED", Instant.now()));
        } catch (Throwable t) {
            LOG.warn("Capture failed: {}", t.toString());
            publisher.publishEvent(new CaptureErrorEvent("CAPTURE_ERROR", Instant.now()));
        } finally {
            if (line != null) {
                try {
                    line.stop();
                } catch (Exception ignore) {
                    // Ignore
                }
                try {
                    line.close();
                } catch (Exception ignore) {
                    // Ignore
                }
            }
        }
    }

    private void ensureSession(UUID id) {
        if (current == null || !current.id.equals(id)) {
            throw new IllegalStateException("Session not found or not active");
        }
    }

    private void cleanup() {
        if (current != null) {
            current.active.set(false);
            current = null;
        }
    }

    private void joinThread(Thread thread, long timeoutMs) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(timeoutMs);
            if (thread.isAlive()) {
                LOG.warn("Capture thread did not terminate within {}ms", timeoutMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for capture thread to terminate");
        }
    }

    private static final class Session {
        final UUID id;
        final AtomicBoolean active = new AtomicBoolean(false);
        volatile boolean canceled = false;
        volatile Thread thread;
        volatile PcmRingBuffer buffer;

        Session(UUID id) {
            this.id = id;
        }
    }
}
