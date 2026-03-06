package com.phillippitts.speaktomack.presentation.controller;

import com.phillippitts.speaktomack.presentation.dto.CaptureResponse;
import com.phillippitts.speaktomack.service.orchestration.RecordingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<CaptureResponse> start() {
        if (recordingService.startRecording()) {
            return ResponseEntity.ok(CaptureResponse.ok("recording"));
        }
        return ResponseEntity.status(409)
                .body(CaptureResponse.error("idle", "already recording or unable to start"));
    }

    @PostMapping("/stop")
    public ResponseEntity<CaptureResponse> stop() {
        if (recordingService.stopRecording()) {
            return ResponseEntity.ok(CaptureResponse.ok("transcribing"));
        }
        return ResponseEntity.status(409)
                .body(CaptureResponse.error("idle", "not currently recording"));
    }

    @PostMapping("/cancel")
    public ResponseEntity<CaptureResponse> cancel() {
        recordingService.cancelRecording();
        return ResponseEntity.ok(CaptureResponse.ok("cancelled"));
    }

    @GetMapping("/status")
    public ResponseEntity<CaptureResponse> status() {
        String state = recordingService.getState().name().toLowerCase();
        return ResponseEntity.ok(CaptureResponse.ok(state));
    }
}
