package com.phillippitts.blckvox;

import com.phillippitts.blckvox.config.stt.VoskConfig;
import com.phillippitts.blckvox.config.stt.WhisperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        VoskConfig.class,
        WhisperConfig.class,
        com.phillippitts.blckvox.config.properties.SttConcurrencyProperties.class,
        com.phillippitts.blckvox.config.properties.SttWatchdogProperties.class,
        com.phillippitts.blckvox.config.properties.AudioCaptureProperties.class,
        com.phillippitts.blckvox.config.properties.AudioValidationProperties.class,
        com.phillippitts.blckvox.config.properties.HotkeyProperties.class,
        com.phillippitts.blckvox.config.properties.OrchestrationProperties.class,
        com.phillippitts.blckvox.config.properties.TypingProperties.class,
        com.phillippitts.blckvox.config.properties.ReconciliationProperties.class,
        com.phillippitts.blckvox.config.properties.TrayProperties.class,
        com.phillippitts.blckvox.config.properties.LiveCaptionProperties.class,
        com.phillippitts.blckvox.config.properties.ThreadPoolProperties.class
})
@EnableScheduling
public class BlckvoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlckvoxApplication.class, args);
    }

}
