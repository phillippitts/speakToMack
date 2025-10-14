package com.phillippitts.speaktomack.config.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds request-scoped values to Log4j2's MDC (ThreadContext) for structured logging.
 *
 * <p>Values added:</p>
 * <ul>
 *   <li>requestId: from X-Request-ID header, or generated UUID</li>
 *   <li>userId: from X-User-ID header (if present)</li>
 *   <li>method: HTTP method</li>
 *   <li>uri: request URI</li>
 * </ul>
 *
 * <p>The context is always cleared after the request to avoid leakage across threads.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter implements Filter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String USER_ID_HEADER = "X-User-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest http) {
                String requestId = headerOrGenerate(http, REQUEST_ID_HEADER);
                ThreadContext.put("requestId", requestId);

                String userId = http.getHeader(USER_ID_HEADER);
                if (userId != null && !userId.isBlank()) {
                    ThreadContext.put("userId", userId);
                }

                ThreadContext.put("method", http.getMethod());
                ThreadContext.put("uri", http.getRequestURI());
            }
            chain.doFilter(request, response);
        } finally {
            ThreadContext.clearAll();
        }
    }

    private static String headerOrGenerate(HttpServletRequest req, String headerName) {
        String v = req.getHeader(headerName);
        return (v == null || v.isBlank()) ? UUID.randomUUID().toString() : v;
        
    }
}
