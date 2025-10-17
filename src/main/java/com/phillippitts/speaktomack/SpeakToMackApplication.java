package com.phillippitts.speaktomack;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        VoskConfig.class,
        WhisperConfig.class,
        com.phillippitts.speaktomack.config.stt.SttConcurrencyProperties.class
})
@EnableScheduling
public class SpeakToMackApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeakToMackApplication.class, args);
    }

}
