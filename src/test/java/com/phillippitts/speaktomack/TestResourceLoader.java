package com.phillippitts.speaktomack;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for loading test resources in integration tests.
 *
 * <p>This class provides helper methods to load audio files and other test resources
 * from the test classpath, reducing code duplication across test classes.
 *
 * <p>Example usage:
 * <pre>
 * byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");
 * </pre>
 */
public final class TestResourceLoader {

    private TestResourceLoader() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Loads a PCM audio file from the test resources.
     *
     * @param path the classpath-relative path to the resource (e.g., "/audio/silence-1s.pcm")
     * @return the file contents as a byte array
     * @throws IOException if the resource is not found or cannot be read
     */
    public static byte[] loadPcm(String path) throws IOException {
        try (InputStream in = TestResourceLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Test resource not found: " + path);
            }
            return in.readAllBytes();
        }
    }

    /**
     * Loads any test resource file from the test classpath.
     *
     * @param path the classpath-relative path to the resource
     * @return the file contents as a byte array
     * @throws IOException if the resource is not found or cannot be read
     */
    public static byte[] loadResource(String path) throws IOException {
        return loadPcm(path); // Same implementation for now
    }
}
