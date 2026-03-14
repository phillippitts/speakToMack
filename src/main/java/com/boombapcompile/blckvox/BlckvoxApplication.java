package com.boombapcompile.blckvox;

import com.boombapcompile.blckvox.config.properties.*;
import com.boombapcompile.blckvox.config.stt.VoskConfig;
import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        VoskConfig.class,
        WhisperConfig.class,
        SttConcurrencyProperties.class,
        SttWatchdogProperties.class,
        AudioCaptureProperties.class,
        AudioValidationProperties.class,
        HotkeyProperties.class,
        OrchestrationProperties.class,
        TypingProperties.class,
        ReconciliationProperties.class,
        TrayProperties.class,
        LiveCaptionProperties.class,
        ThreadPoolProperties.class
})
@EnableScheduling
public class BlckvoxApplication {

    public static void main(String[] args) {
        System.setProperty("apple.awt.UIElement", "true");
        SpringApplication.run(BlckvoxApplication.class, args);
    }

}
