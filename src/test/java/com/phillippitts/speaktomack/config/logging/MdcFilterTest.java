package com.phillippitts.speaktomack.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MdcFilterTest {

    private MdcFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new MdcFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        // Clear MDC before each test
        ThreadContext.clearAll();
    }

    @AfterEach
    void tearDown() {
        // Ensure MDC is clean after each test
        ThreadContext.clearAll();
    }

    @Test
    void extractsRequestIdFromHeader() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("test-request-123");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilter(request, response, chain);

        // MDC should be cleared after filter
        assertThat(ThreadContext.get("requestId")).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void generatesUuidIfNoRequestIdHeader() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transcribe");

        // Capture MDC state during filter execution
        doAnswer(invocation -> {
            String requestId = ThreadContext.get("requestId");
            assertThat(requestId).isNotNull();
            // Should be a valid UUID format
            assertThat(requestId).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
            );
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void generatesUuidIfRequestIdHeaderIsBlank() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("   ");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/health");

        doAnswer(invocation -> {
            String requestId = ThreadContext.get("requestId");
            assertThat(requestId).isNotNull();
            assertThat(requestId).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
            );
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void extractsUserIdFromHeader() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getHeader("X-User-ID")).thenReturn("user-456");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transcribe");

        doAnswer(invocation -> {
            assertThat(ThreadContext.get("userId")).isEqualTo("user-456");
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void doesNotSetUserIdIfHeaderMissing() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getHeader("X-User-ID")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        doAnswer(invocation -> {
            assertThat(ThreadContext.get("userId")).isNull();
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void doesNotSetUserIdIfHeaderIsBlank() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getHeader("X-User-ID")).thenReturn("  ");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        doAnswer(invocation -> {
            assertThat(ThreadContext.get("userId")).isNull();
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void setsMethodAndUri() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transcribe");

        doAnswer(invocation -> {
            assertThat(ThreadContext.get("method")).isEqualTo("POST");
            assertThat(ThreadContext.get("uri")).isEqualTo("/api/transcribe");
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void clearsContextAfterRequest() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getHeader("X-User-ID")).thenReturn("user-456");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilter(request, response, chain);

        // All MDC values should be cleared
        assertThat(ThreadContext.get("requestId")).isNull();
        assertThat(ThreadContext.get("userId")).isNull();
        assertThat(ThreadContext.get("method")).isNull();
        assertThat(ThreadContext.get("uri")).isNull();
    }

    @Test
    void clearsContextEvenWhenChainThrows() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transcribe");

        doThrow(new ServletException("Test exception")).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("Test exception");

        // MDC should still be cleared despite exception
        assertThat(ThreadContext.get("requestId")).isNull();
        assertThat(ThreadContext.get("userId")).isNull();
        assertThat(ThreadContext.get("method")).isNull();
        assertThat(ThreadContext.get("uri")).isNull();
    }

    @Test
    void clearsContextWhenIoExceptionOccurs() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        doThrow(new IOException("IO error")).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IOException.class);

        // MDC should still be cleared
        assertThat(ThreadContext.get("requestId")).isNull();
    }

    @Test
    void handlesNonHttpServletRequest() throws ServletException, IOException {
        ServletRequest nonHttpRequest = mock(ServletRequest.class);

        filter.doFilter(nonHttpRequest, response, chain);

        verify(chain).doFilter(nonHttpRequest, response);
        // MDC should be empty (no HTTP-specific values set)
        assertThat(ThreadContext.isEmpty()).isTrue();
    }

    @Test
    void setsAllValuesInSingleRequest() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("req-xyz");
        when(request.getHeader("X-User-ID")).thenReturn("user-abc");
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/sessions/123");

        doAnswer(invocation -> {
            Map<String, String> contextMap = ThreadContext.getContext();
            assertThat(contextMap).containsEntry("requestId", "req-xyz");
            assertThat(contextMap).containsEntry("userId", "user-abc");
            assertThat(contextMap).containsEntry("method", "DELETE");
            assertThat(contextMap).containsEntry("uri", "/api/sessions/123");
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }

    @Test
    void preventsMdcLeakageBetweenRequests() throws ServletException, IOException {
        // First request
        when(request.getHeader("X-Request-ID")).thenReturn("req-1");
        when(request.getHeader("X-User-ID")).thenReturn("user-1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/first");

        filter.doFilter(request, response, chain);

        // Verify cleanup
        assertThat(ThreadContext.get("requestId")).isNull();

        // Second request with different values
        when(request.getHeader("X-Request-ID")).thenReturn("req-2");
        when(request.getHeader("X-User-ID")).thenReturn("user-2");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/second");

        doAnswer(invocation -> {
            // Should NOT see values from first request
            assertThat(ThreadContext.get("requestId")).isEqualTo("req-2");
            assertThat(ThreadContext.get("userId")).isEqualTo("user-2");
            assertThat(ThreadContext.get("method")).isEqualTo("POST");
            assertThat(ThreadContext.get("uri")).isEqualTo("/api/second");
            return null;
        }).when(chain).doFilter(any(), any());


        filter.doFilter(request, response, chain);
    }
}
