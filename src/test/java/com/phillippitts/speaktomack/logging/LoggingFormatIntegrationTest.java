package com.phillippitts.speaktomack.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test to validate structured log format (MDC) when invoking the /ping endpoint.
 *
 * Verifies that:
 * - The PingController logs an INFO message
 * - The Log4j2 MDC contains requestId and userId propagated by MdcFilter
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoggingFormatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private InMemoryAppender appender;
    private Logger logger;

    @BeforeEach
    void setUpAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        logger = ctx.getLogger("com.phillippitts.speaktomack.presentation.controller.PingController");
        appender = new InMemoryAppender("test-appender");
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownAppender() {
        if (logger != null && appender != null) {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldIncludeRequestIdAndUserIdInStructuredLogs() {
        // Arrange
        String requestId = "abc123";
        String userId = "demo";

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-ID", requestId);
        headers.add("X-User-ID", userId);

        // Act: Call /ping to trigger a log line from PingController
        String url = "http://localhost:" + port + "/ping";
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        // Assert HTTP ok
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        // Await until at least one matching log event is captured
        await().atMost(3, SECONDS).until(() ->
                appender.getEvents().stream().anyMatch(e ->
                        e.getMessage() != null && String.valueOf(e.getMessage().getFormattedMessage())
                                .contains("Ping received"))
        );

        // Find the event we logged from PingController
        LogEvent event = appender.getEvents().stream()
                .filter(e -> e.getMessage() != null && String.valueOf(e.getMessage().getFormattedMessage())
                        .contains("Ping received"))
                .findFirst()
                .orElseThrow();

        // Validate MDC values present in the event
        ReadOnlyStringMap contextData = event.getContextData();
        assertThat(contextData).isNotNull();
        assertEquals(requestId, contextData.getValue("requestId"));
        assertEquals(userId, contextData.getValue("userId"));

        // Sanity: logger name should be the PingController
        assertThat(event.getLoggerName()).isEqualTo("com.phillippitts.speaktomack.presentation.controller.PingController");
    }

    /**
     * Simple in-memory Log4j2 appender that captures LogEvents for assertions.
     */
    private static class InMemoryAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        protected InMemoryAppender(String name) {
            super(name, new AbstractFilter() {}, PatternLayout.createDefaultLayout(), true, null);
        }

        @Override
        public void append(LogEvent event) {
            // Use a mutable copy to avoid issues with reused events in async contexts
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }
    }
}
