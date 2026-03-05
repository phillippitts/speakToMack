package com.phillippitts.speaktomack.service.tray;

import com.phillippitts.speaktomack.service.orchestration.ApplicationState;
import com.phillippitts.speaktomack.service.orchestration.RecordingService;
import com.phillippitts.speaktomack.service.orchestration.event.ApplicationStateChangedEvent;
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
        assertThat(bi.getWidth()).isEqualTo(16);
        assertThat(bi.getHeight()).isEqualTo(16);
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
