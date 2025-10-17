package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.phillippitts.speaktomack.service.stt.whisper.WhisperTestDoubles.ProcessBehavior;
import static com.phillippitts.speaktomack.service.stt.whisper.WhisperTestDoubles.StubProcessFactory;
import static com.phillippitts.speaktomack.service.stt.whisper.WhisperTestDoubles.TestProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperSttEngineTest {

    @Test
    void successReturnsTranscription() throws Exception {
        // Stub process that exits immediately with known stdout
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("hello world", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm1s = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm1s);
        assertThat(r.text()).isEqualTo("hello world");
        assertThat(r.engineName()).isEqualTo("whisper");
        assertThat(r.confidence()).isEqualTo(1.0);
        engine.close();
    }

    @Test
    void nonZeroExitRethrowsTranscriptionException() throws Exception {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "error", 1, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("engine=whisper");
        engine.close();
    }

    @Test
    void timeoutRethrowsTranscriptionException() throws Exception {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "", 0, -1)) // never finishes
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 1, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Timeout");
        engine.close();
    }

    @Test
    void inputValidationShouldRejectNullOrEmpty() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        assertThatThrownBy(() -> engine.transcribe(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.transcribe(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBeIdempotentForMultipleInitializations() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("test", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);

        // Initialize multiple times - should be idempotent
        engine.initialize();
        engine.initialize();
        engine.initialize();

        // Should still work normally
        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);
        assertThat(r.text()).isEqualTo("test");
        assertThat(engine.isHealthy()).isTrue();
        engine.close();
    }

    @Test
    void shouldThrowWhenTranscribingAfterClose() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("test", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        // Close the engine
        engine.close();
        assertThat(engine.isHealthy()).isFalse();

        // Transcribe after close should fail
        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void shouldHandleEmptyStdoutGracefully() {
        // Simulate whisper returning empty output (e.g., silence or no speech detected)
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);

        // Should return empty text, not fail
        assertThat(r.text()).isEmpty();
        assertThat(r.confidence()).isEqualTo(1.0);
        assertThat(r.engineName()).isEqualTo("whisper");
        engine.close();
    }

    @Test
    void shouldNotLogFullTranscriptionTextForPrivacy() {
        // Arrange: Set up log capture
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Logger logger = ctx.getLogger(WhisperSttEngine.class.getName());
        InMemoryAppender appender = new InMemoryAppender("privacy-test");
        appender.start();
        logger.addAppender(appender);

        try {
            WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                    new TestProcess(new ProcessBehavior("sensitive secret password data", "", 0, 0))
            ));
            WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
            WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
            engine.initialize();

            // Act: Transcribe with sensitive content
            byte[] pcm = new byte[32_000];
            TranscriptionResult r = engine.transcribe(pcm);
            assertThat(r.text()).isEqualTo("sensitive secret password data");

            // Assert: Logs should NOT contain the full text
            List<LogEvent> events = appender.getEvents();
            for (LogEvent event : events) {
                String message = event.getMessage().getFormattedMessage();
                // Should log character count
                if (message.contains("transcribed")) {
                    assertThat(message).contains("chars=");
                    assertThat(message).contains("30"); // length of the text
                }
                // Must NOT log sensitive words
                assertThat(message).doesNotContain("sensitive");
                assertThat(message).doesNotContain("secret");
                assertThat(message).doesNotContain("password");
            }

            engine.close();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    /**
     * Simple in-memory Log4j2 appender that captures LogEvents for privacy assertions.
     */
    private static class InMemoryAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        protected InMemoryAppender(String name) {
            super(name, new AbstractFilter() {}, PatternLayout.createDefaultLayout(), true, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }
    }
}
