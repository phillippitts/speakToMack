package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.service.audio.capture.PcmChunkCapturedEvent;
import com.phillippitts.speaktomack.service.orchestration.ApplicationState;
import com.phillippitts.speaktomack.service.orchestration.event.ApplicationStateChangedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Feeds PCM chunks to a Vosk recognizer incrementally and publishes partial text events.
 *
 * <p>Manages a session-scoped recognizer: created when recording starts, destroyed when
 * recording stops. Uses its own Vosk model instance separate from {@link VoskSttEngine}.
 *
 * @since 1.3
 */
@Service
@ConditionalOnProperty(name = "live-caption.enabled", havingValue = "true")
public class VoskStreamingService {

    private static final Logger LOG = LogManager.getLogger(VoskStreamingService.class);

    private final VoskConfig config;
    private final ApplicationEventPublisher publisher;

    private final Object recognizerLock = new Object();
    private org.vosk.Model model;
    private org.vosk.Recognizer recognizer;

    public VoskStreamingService(VoskConfig config, ApplicationEventPublisher publisher) {
        this.config = config;
        this.publisher = publisher;
    }

    @EventListener
    public void onStateChanged(ApplicationStateChangedEvent event) {
        if (event.current() == ApplicationState.RECORDING) {
            openRecognizer();
        } else {
            closeRecognizer();
        }
    }

    @EventListener
    public void onPcmChunk(PcmChunkCapturedEvent event) {
        synchronized (recognizerLock) {
            if (recognizer == null) {
                return;
            }
            boolean accepted = recognizer.acceptWaveForm(event.pcmData(), event.length());
            if (accepted) {
                String text = parseTextField(recognizer.getResult(), "text");
                if (!text.isEmpty()) {
                    publisher.publishEvent(new VoskPartialResultEvent(text, true));
                }
            } else {
                String partial = parseTextField(recognizer.getPartialResult(), "partial");
                if (!partial.isEmpty()) {
                    publisher.publishEvent(new VoskPartialResultEvent(partial, false));
                }
            }
        }
    }

    private void openRecognizer() {
        synchronized (recognizerLock) {
            closeRecognizerLocked();
            try {
                if (model == null) {
                    model = new org.vosk.Model(config.modelPath());
                }
                recognizer = new org.vosk.Recognizer(model, config.sampleRate());
                LOG.info("Vosk streaming recognizer opened");
            } catch (Exception e) {
                LOG.error("Failed to open Vosk streaming recognizer", e);
            }
        }
    }

    private void closeRecognizer() {
        synchronized (recognizerLock) {
            closeRecognizerLocked();
        }
    }

    private void closeRecognizerLocked() {
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception e) {
                LOG.debug("Error closing streaming recognizer: {}", e.getMessage());
            }
            recognizer = null;
            LOG.debug("Vosk streaming recognizer closed");
        }
    }

    private static String parseTextField(String json, String field) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return new JSONObject(json).optString(field, "").trim();
        } catch (Exception e) {
            LOG.debug("Failed to parse Vosk streaming JSON: {}", e.getMessage());
            return "";
        }
    }
}
