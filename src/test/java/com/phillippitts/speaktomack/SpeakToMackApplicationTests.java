package com.phillippitts.speaktomack;

import com.phillippitts.speaktomack.config.IntegrationTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@Import({TestcontainersConfiguration.class, IntegrationTestConfiguration.class})
@SpringBootTest(
    properties = {
        "stt.validation.enabled=false", // avoid requiring real models/binaries in tests
        "audio.capture.chunk-millis=40",
        "audio.capture.max-duration-ms=60000"
    }
)
class SpeakToMackApplicationTests {

    @Test
    void contextLoads() {
    }

}
