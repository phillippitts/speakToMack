package com.phillippitts.speaktomack.presentation.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight endpoint to generate a request that traverses the MDC filter
 * so we can visually verify structured Log4j 2 logs (requestId, userId, etc.).
 */
@RestController
class PingController {

    private static final Logger log = LogManager.getLogger(PingController.class);

    @GetMapping("/ping")
    ResponseEntity<Map<String, Object>> ping() {
        // Log an informational message; MDC values (requestId, userId) will be added by MdcFilter
        log.info("Ping received — structured logging verification");
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        ));
    }
}
