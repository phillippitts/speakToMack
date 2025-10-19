package com.phillippitts.speaktomack.config.orchestration;

import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the DualEngineOrchestrator explicitly to avoid bean ambiguity.
 */
@Configuration
public class OrchestrationConfig {

    @Bean
    public DualEngineOrchestrator dualEngineOrchestrator(AudioCaptureService captureService,
                                                         SttEngine voskSttEngine,
                                                         SttEngine whisperSttEngine,
                                                         SttEngineWatchdog watchdog,
                                                         OrchestrationProperties props,
                                                         ApplicationEventPublisher publisher) {
        return new DualEngineOrchestrator(captureService, voskSttEngine, whisperSttEngine, watchdog, props, publisher);
    }
}
