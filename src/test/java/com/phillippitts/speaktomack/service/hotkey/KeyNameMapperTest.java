package com.phillippitts.speaktomack.service.hotkey;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KeyNameMapperTest {

    @Test
    void normalizesAliases() {
        assertThat(KeyNameMapper.normalizeKey("Right Meta")).isEqualTo("RIGHT_META");
        assertThat(KeyNameMapper.normalizeKey("Command")).isEqualTo("META");
        assertThat(KeyNameMapper.normalizeModifier("cmd")).isEqualTo("META");
        assertThat(KeyNameMapper.normalizeModifier("Command")).isEqualTo("META");
    }

    @Test
    void validatesKeysAndModifiers() {
        assertThat(KeyNameMapper.isValidKey("F13")).isTrue();
        assertThat(KeyNameMapper.isValidKey("ENTER")).isTrue();
        assertThat(KeyNameMapper.isValidKey("foo")).isFalse();
        assertThat(KeyNameMapper.isValidModifier("SHIFT")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("LEFT_META")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("WEIRD")).isFalse();
    }

    @Test
    void matchesReservedCombos() {
        Set<String> mods = Set.of("META", "SHIFT");
        assertThat(KeyNameMapper.matchesReserved(mods, "D", "META+SHIFT+D")).isTrue();
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", "META+TAB")).isTrue();
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", "META+L")).isFalse();
    }
}
