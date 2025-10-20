package com.phillippitts.speaktomack.config;

import com.phillippitts.speaktomack.config.audio.AudioCaptureProperties;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.audio.capture.JavaSoundAudioCaptureService;
import com.phillippitts.speaktomack.service.validation.AudioValidationProperties;
import com.phillippitts.speaktomack.testutil.FakeAudioCaptureService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing mocked/stubbed beans for integration tests.
 *
 * <p>This configuration avoids hardware dependencies (microphone, audio devices) by providing
 * test doubles for audio-related components. Tests that need real audio capture should not
 * import this configuration.
 *
 * <p><b>Usage:</b> Import this class in integration tests that don't require real audio hardware:
 * <pre>
 * {@literal @}SpringBootTest
 * {@literal @}Import(IntegrationTestConfiguration.class)
 * class MyIntegrationTest { ... }
 * </pre>
 *
 * <p><b>Implementation note:</b> This configuration provides {@code @Primary} beans that should
 * take precedence over production beans. The {@link AudioCaptureService} bean is conditionally
 * created only if no other AudioCaptureService bean exists (preventing conflicts with production
 * {@link JavaSoundAudioCaptureService}).
 */
@Configuration
public class IntegrationTestConfiguration {

    /**
     * Provides test AudioCaptureProperties with sensible defaults.
     *
     * <p>These properties avoid the need for application.properties configuration in tests.
     *
     * @return test audio capture properties
     */
    @Bean
    @Primary
    public AudioCaptureProperties testAudioCaptureProperties() {
        return new AudioCaptureProperties(
                40,      // chunkMillis
                60000,   // maxDurationMs (60 seconds)
                null     // deviceName (use default)
        );
    }

    /**
     * Provides test AudioValidationProperties with permissive defaults.
     *
     * <p>These properties allow test audio clips of varying lengths without validation errors.
     *
     * @return test audio validation properties
     */
    @Bean
    @Primary
    public AudioValidationProperties testAudioValidationProperties() {
        AudioValidationProperties props = new AudioValidationProperties();
        props.setMinDurationMs(100);      // Allow very short clips for testing
        props.setMaxDurationMs(300000);   // 5 minutes max
        return props;
    }

    /**
     * Provides fake AudioCaptureService for tests that don't need real microphone access.
     *
     * <p>This bean is marked {@code @Primary} to override the real {@code JavaSoundAudioCaptureService}
     * in integration test contexts, avoiding microphone permission and hardware availability issues.
     *
     * @return fake audio capture service
     */
    @Bean
    @Primary
    public AudioCaptureService testAudioCaptureService() {
        return new FakeAudioCaptureService();
    }
}
