package com.phillippitts.blckvox.service.tray;

import com.phillippitts.blckvox.service.orchestration.ApplicationState;
import com.phillippitts.blckvox.service.orchestration.RecordingService;
import com.phillippitts.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SystemTrayManagerTest {

    @Test
    void createIconReturnsNonNullImage() {
        var img = SystemTrayManager.createIcon(Color.GREEN);
        assertThat(img).isNotNull();
        assertThat(img).isInstanceOf(BufferedImage.class);
        BufferedImage bi = (BufferedImage) img;
        assertThat(bi.getWidth()).isEqualTo(32);
        assertThat(bi.getHeight()).isEqualTo(32);
    }

    @Test
    void createIconDrawsCircleShape() {
        BufferedImage img = (BufferedImage) SystemTrayManager.createIcon(Color.RED);
        // Center pixel should be opaque (part of the filled circle)
        int centerArgb = img.getRGB(16, 16);
        assertThat((centerArgb >> 24) & 0xFF).as("center pixel alpha").isEqualTo(255);
        // Corner pixel (0,0) should be transparent (outside the circle)
        int cornerArgb = img.getRGB(0, 0);
        assertThat((cornerArgb >> 24) & 0xFF).as("corner pixel alpha").isEqualTo(0);
    }

    @Test
    void lifecycleTracksRunningState() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty());

        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void stateChangeEventDoesNotThrowWhenTrayNotInitialized() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty());

        // Should be a no-op, not throw
        mgr.onStateChanged(new ApplicationStateChangedEvent(
                ApplicationState.IDLE, ApplicationState.RECORDING, Instant.now()));
    }
}
