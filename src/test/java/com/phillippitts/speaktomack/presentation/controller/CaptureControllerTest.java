package com.phillippitts.speaktomack.presentation.controller;

import com.phillippitts.speaktomack.service.orchestration.ApplicationState;
import com.phillippitts.speaktomack.service.orchestration.RecordingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptureController.class)
class CaptureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordingService recordingService;

    @Test
    void startReturns200WhenSuccessful() throws Exception {
        when(recordingService.startRecording()).thenReturn(true);

        mockMvc.perform(post("/api/capture/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recording"));
    }

    @Test
    void startReturns409WhenAlreadyRecording() throws Exception {
        when(recordingService.startRecording()).thenReturn(false);

        mockMvc.perform(post("/api/capture/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void stopReturns200WhenSuccessful() throws Exception {
        when(recordingService.stopRecording()).thenReturn(true);

        mockMvc.perform(post("/api/capture/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("transcribing"));
    }

    @Test
    void stopReturns409WhenNotRecording() throws Exception {
        when(recordingService.stopRecording()).thenReturn(false);

        mockMvc.perform(post("/api/capture/stop"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void statusReturnsCurrentState() throws Exception {
        when(recordingService.getState()).thenReturn(ApplicationState.IDLE);

        mockMvc.perform(get("/api/capture/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("idle"));
    }

    @Test
    void cancelReturns200() throws Exception {
        mockMvc.perform(post("/api/capture/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void cancelCallsCancelRecording() throws Exception {
        mockMvc.perform(post("/api/capture/cancel"))
                .andExpect(status().isOk());

        verify(recordingService).cancelRecording();
    }

    @Test
    void statusReturnsRecordingState() throws Exception {
        when(recordingService.getState()).thenReturn(ApplicationState.RECORDING);

        mockMvc.perform(get("/api/capture/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("recording"));
    }
}
