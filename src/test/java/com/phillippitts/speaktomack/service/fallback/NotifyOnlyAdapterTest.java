package com.phillippitts.speaktomack.service.fallback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyOnlyAdapterTest {

    @Test
    void canTypeAlwaysReturnsTrue() {
        NotifyOnlyAdapter adapter = new NotifyOnlyAdapter();
        assertThat(adapter.canType()).isTrue();
    }

    @Test
    void typeAlwaysSucceeds() {
        NotifyOnlyAdapter adapter = new NotifyOnlyAdapter();
        assertThat(adapter.type("hello world")).isTrue();
        assertThat(adapter.type("")).isTrue();
        assertThat(adapter.type(null)).isTrue();
    }

    @Test
    void typeHandlesLongText() {
        NotifyOnlyAdapter adapter = new NotifyOnlyAdapter();
        String longText = "a".repeat(10000);
        assertThat(adapter.type(longText)).isTrue();
    }

    @Test
    void typeHandlesNewlines() {
        NotifyOnlyAdapter adapter = new NotifyOnlyAdapter();
        assertThat(adapter.type("line1\nline2\r\nline3")).isTrue();
    }

    @Test
    void returnsCorrectName() {
        NotifyOnlyAdapter adapter = new NotifyOnlyAdapter();
        assertThat(adapter.name()).isEqualTo("notify");
    }
}
