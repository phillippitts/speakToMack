package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.orchestration.OrchestrationConfig;
import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.service.audio.capture.CaptureErrorEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPressedEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyReleasedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.Objects;

/**
 * Thin hotkey-event adapter that delegates to {@link RecordingService}.
 *
 * <p>Supports push-to-talk and toggle modes. All recording logic is encapsulated
 * in the {@link RecordingService}; this class simply maps hotkey events to
 * start/stop/cancel calls.
 *
 * <p><b>Configuration:</b> Not annotated as {@code @Component} to avoid ambiguity; see
 * {@link OrchestrationConfig} for bean wiring.
 *
 * @since 1.0
 */
public class HotkeyRecordingAdapter {

    private static final Logger LOG = LogManager.getLogger(HotkeyRecordingAdapter.class);

    private final RecordingService recordingService;
    private final HotkeyProperties hotkeyProps;

    /**
     * Constructs a HotkeyRecordingAdapter.
     *
     * @param recordingService service for start/stop/cancel recording
     * @param hotkeyProps hotkey configuration (toggle mode, etc.)
     */
    public HotkeyRecordingAdapter(RecordingService recordingService,
                                   HotkeyProperties hotkeyProps) {
        this.recordingService = Objects.requireNonNull(recordingService, "recordingService");
        this.hotkeyProps = Objects.requireNonNull(hotkeyProps, "hotkeyProps");
    }

    @EventListener
    @Async("eventExecutor")
    public void onHotkeyPressed(HotkeyPressedEvent evt) {
        if (hotkeyProps.isToggleMode()) {
            // Atomic toggle: starts if idle, stops if recording — no TOCTOU gap
            if (recordingService.toggleRecording()) {
                LOG.info("Toggle mode: toggled capture at {}", evt.at());
            } else {
                LOG.warn("Toggle recording failed");
            }
            return;
        }

        // Push-to-talk: press starts
        if (recordingService.startRecording()) {
            LOG.info("Capture session started at {}", evt.at());
        } else {
            LOG.debug("Capture already active, ignoring press");
        }
    }

    @EventListener
    @Async("eventExecutor")
    public void onHotkeyReleased(HotkeyReleasedEvent evt) {
        if (hotkeyProps.isToggleMode()) {
            LOG.debug("Toggle mode: ignoring release event");
            return;
        }

        recordingService.stopRecording();
    }

    @EventListener
    @Async("eventExecutor")
    public void onCaptureError(CaptureErrorEvent event) {
        LOG.warn("Capture error: {}", event.reason());
        recordingService.cancelRecording();
    }

}
