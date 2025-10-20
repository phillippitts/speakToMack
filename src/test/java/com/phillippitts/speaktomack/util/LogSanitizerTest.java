package com.phillippitts.speaktomack.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void shouldReturnEmptyStringForNull() {
        assertThat(LogSanitizer.truncate(null, 10)).isEmpty();
        assertThat(LogSanitizer.truncate(null, 100)).isEmpty();
        assertThat(LogSanitizer.truncate(null, 0)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForZeroMax() {
        assertThat(LogSanitizer.truncate("hello world", 0)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForNegativeMax() {
        assertThat(LogSanitizer.truncate("hello world", -1)).isEmpty();
        assertThat(LogSanitizer.truncate("hello world", -100)).isEmpty();
    }

    @Test
    void shouldReturnFullStringWhenShorterThanMax() {
        assertThat(LogSanitizer.truncate("hello", 10)).isEqualTo("hello");
        assertThat(LogSanitizer.truncate("test", 100)).isEqualTo("test");
    }

    @Test
    void shouldReturnFullStringWhenEqualToMax() {
        assertThat(LogSanitizer.truncate("12345", 5)).isEqualTo("12345");
        assertThat(LogSanitizer.truncate("hello", 5)).isEqualTo("hello");
    }

    @Test
    void shouldTruncateWhenLongerThanMax() {
        assertThat(LogSanitizer.truncate("hello world", 5)).isEqualTo("hello");
        assertThat(LogSanitizer.truncate("This is a long string", 10)).isEqualTo("This is a ");
    }

    @Test
    void shouldTruncateToOneCharacter() {
        assertThat(LogSanitizer.truncate("hello", 1)).isEqualTo("h");
        assertThat(LogSanitizer.truncate("world", 1)).isEqualTo("w");
    }

    @Test
    void shouldHandleEmptyString() {
        assertThat(LogSanitizer.truncate("", 10)).isEmpty();
        assertThat(LogSanitizer.truncate("", 0)).isEmpty();
    }

    @Test
    void shouldTruncateVeryLongStrings() {
        String longString = "a".repeat(10000);
        assertThat(LogSanitizer.truncate(longString, 100)).hasSize(100);
        assertThat(LogSanitizer.truncate(longString, 100)).isEqualTo("a".repeat(100));
    }

    @Test
    void shouldPreserveUnicodeCharacters() {
        String unicode = "Hello 世界 🌍";
        assertThat(LogSanitizer.truncate(unicode, 5)).isEqualTo("Hello");
        assertThat(LogSanitizer.truncate(unicode, 8)).isEqualTo("Hello 世界");
    }

    @Test
    void shouldHandleWhitespaceOnlyStrings() {
        assertThat(LogSanitizer.truncate("     ", 3)).isEqualTo("   ");
        assertThat(LogSanitizer.truncate("\t\n\r", 2)).isEqualTo("\t\n");
    }
}
