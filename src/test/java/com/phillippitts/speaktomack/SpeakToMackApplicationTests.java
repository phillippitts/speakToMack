package com.phillippitts.speaktomack;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
    "stt.validation.enabled=false" // avoid requiring real models/binaries in tests
})
class SpeakToMackApplicationTests {

    @Test
    void contextLoads() {
    }

}
