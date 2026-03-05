package com.phillippitts.speaktomack.presentation.controller;

import com.phillippitts.speaktomack.service.orchestration.RecordingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for programmatic control of audio capture and transcription.
 *
 * @since 1.2
 */
@RestController
@RequestMapping("/api/capture")
public class CaptureController {

    private final RecordingService recordingService;

    public CaptureController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> start() {
        if (recordingService.startRecording()) {
            return ResponseEntity.ok(Map.of("status", "recording"));
        }
        return ResponseEntity.status(409)
                .body(Map.of("error", "already recording or unable to start"));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stop() {
        if (recordingService.stopRecording()) {
            return ResponseEntity.ok(Map.of("status", "transcribing"));
        }
        return ResponseEntity.status(409)
                .body(Map.of("error", "not currently recording"));
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancel() {
        recordingService.cancelRecording();
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> status() {
        return ResponseEntity.ok(
                Map.of("state", recordingService.getState().name().toLowerCase()));
    }
}
