package com.phillippitts.blckvox.service.livecaption;

import com.phillippitts.blckvox.config.properties.LiveCaptionProperties;
import com.phillippitts.blckvox.service.audio.capture.PcmChunkCapturedEvent;
import com.phillippitts.blckvox.service.orchestration.ApplicationState;
import com.phillippitts.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import com.phillippitts.blckvox.service.stt.vosk.VoskPartialResultEvent;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges Spring events to the JavaFX {@link LiveCaptionWindow}.
 *
 * <p>Shows/hides the overlay on recording state changes, updates the oscilloscope
 * waveform with PCM data, and displays streaming Vosk caption text.
 *
 * @since 1.3
 */
@Service
@ConditionalOnProperty(name = "live-caption.enabled", havingValue = "true")
public class LiveCaptionManager {

    private static final Logger LOG = LogManager.getLogger(LiveCaptionManager.class);

    private final LiveCaptionProperties props;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private volatile LiveCaptionWindow window;

    public LiveCaptionManager(LiveCaptionProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
        if (!value) {
            Platform.runLater(() -> {
                if (window != null) {
                    window.hide();
                }
            });
        }
    }

    @EventListener
    public void onStateChanged(ApplicationStateChangedEvent event) {
        if (!enabled.get()) {
            return;
        }
        if (event.current() == ApplicationState.RECORDING) {
            Platform.runLater(this::showWindow);
        } else if (event.current() == ApplicationState.IDLE) {
            Platform.runLater(() -> {
                if (window != null) {
                    window.hide();
                }
            });
        }
    }

    @EventListener
    public void onPcmChunk(PcmChunkCapturedEvent event) {
        if (!enabled.get() || window == null) {
            return;
        }
        short[] samples = PcmSampleConverter.toSamples(event.pcmData(), event.length());
        Platform.runLater(() -> {
            if (window != null) {
                window.updateWaveform(samples);
            }
        });
    }

    @EventListener
    public void onVoskPartialResult(VoskPartialResultEvent event) {
        if (!enabled.get() || window == null) {
            return;
        }
        Platform.runLater(() -> {
            if (window != null) {
                window.updateCaption(event.text(), event.isFinal());
            }
        });
    }

    private void showWindow() {
        if (window == null) {
            window = new LiveCaptionWindow(props);
            LOG.info("Live caption window created");
        }
        window.show();
    }
}
