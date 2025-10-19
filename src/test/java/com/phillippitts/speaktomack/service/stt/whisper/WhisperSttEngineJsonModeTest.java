package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.SttConcurrencyProperties;
import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperSttEngineJsonModeTest {

    @Test
    void parsesJsonWhenJsonModeEnabled() {
        // Use a real WhisperProcessManager with a fake ProcessFactory that emits JSON
        ProcessFactory factory = new ProcessFactory() {
            @Override
            public Process start(java.util.List<String> command, java.nio.file.Path workingDir) {
                String json = "{\"text\":\"json parsed text\"}" + "\n";
                return new Process() {
                    private final java.io.ByteArrayInputStream out = new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    private final java.io.ByteArrayInputStream err = new java.io.ByteArrayInputStream(new byte[0]);
                    private volatile boolean alive = true;
                    @Override public java.io.OutputStream getOutputStream() { return new java.io.ByteArrayOutputStream(); }
                    @Override public java.io.InputStream getInputStream() { return out; }
                    @Override public java.io.InputStream getErrorStream() { return err; }
                    @Override public int waitFor() { alive = false; return 0; }
                    @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { alive = false; return true; }
                    @Override public int exitValue() { return 0; }
                    @Override public void destroy() { alive = false; }
                    @Override public Process destroyForcibly() { alive = false; return this; }
                    @Override public boolean isAlive() { return alive; }
                };
            }
        };
        WhisperProcessManager fakeManager = new WhisperProcessManager(factory, "json");

        WhisperConfig cfg = new WhisperConfig();
        SttConcurrencyProperties conc = new SttConcurrencyProperties();
        ApplicationEventPublisher publisher = e -> {};

        WhisperSttEngine engine = new WhisperSttEngine(cfg, conc, fakeManager, publisher, "json");
        engine.initialize();
        TranscriptionResult r = engine.transcribe(new byte[3200]);
        assertThat(r.text()).isEqualTo("json parsed text");
        assertThat(r.engineName()).isEqualTo("whisper");
    }
}
