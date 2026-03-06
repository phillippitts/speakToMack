package com.phillippitts.speaktomack.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified response for all capture endpoints.
 *
 * @param status the current capture status (e.g. "recording", "transcribing", "idle", "cancelled")
 * @param error  error message if the request could not be fulfilled, null on success
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaptureResponse(String status, String error) {

    public static CaptureResponse ok(String status) {
        return new CaptureResponse(status, null);
    }

    public static CaptureResponse error(String status, String error) {
        return new CaptureResponse(status, error);
    }
}
