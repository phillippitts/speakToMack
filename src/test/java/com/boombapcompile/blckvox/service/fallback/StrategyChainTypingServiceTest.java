package com.boombapcompile.blckvox.service.fallback;

import com.boombapcompile.blckvox.config.properties.TypingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyChainTypingServiceTest {

    @Test
    void fallsBackFromRobotToClipboard() {
        TypingProperties props = new TypingProperties(800, 30, 0, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default", 200);
        TypingAdapter failingRobot = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                return false;
            }
            @Override public String name() {
                return "robot";
            }
        };
        final boolean[] clipboardCalled = {false};
        TypingAdapter okClipboard = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                clipboardCalled[0] = true;
                return true;
            }
            @Override public String name() {
                return "clipboard";
            }
        };
        TypingAdapter notify = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                return true;
            }
            @Override public String name() {
                return "notify";
            }
        };
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(failingRobot, okClipboard, notify), props, e -> { });
        boolean ok = svc.paste("hello");
        assertThat(ok).isTrue();
        assertThat(clipboardCalled[0]).isTrue();
    }
}
