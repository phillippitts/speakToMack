package com.phillippitts.speaktomack.annotation;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks tests that require real device access or external binaries (e.g., Vosk models, Whisper binary).
 *
 * <p>These tests are excluded from CI by default. To run them locally:
 * <pre>
 * ./gradlew test -Dtest.includeRealBinary=true
 * </pre>
 *
 * <p>Common use cases:
 * <ul>
 *   <li>Tests that load Vosk models from disk (requiresVoskModel)</li>
 *   <li>Tests that invoke Whisper binary (requiresWhisperBinary)</li>
 *   <li>Tests that require microphone access (requiresMicrophone)</li>
 *   <li>Tests that require OS-specific features (requiresAccessibility)</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("real-binary")
public @interface RequiresRealBinary {
    /**
     * Human-readable description of what this test requires.
     * Examples: "Vosk model in models/vosk-model-small-en-us-0.15",
     *           "whisper.cpp binary in bin/whisper",
     *           "Microphone permission granted"
     */
    String value() default "";
}
