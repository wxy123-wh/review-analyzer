package com.wh.reputation.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = requestIdFromHeader(request);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(ATTRIBUTE, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ATTRIBUTE);
        }
    }

    private static String requestIdFromHeader(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String headerValue = request.getHeader(HEADER);
        if (headerValue == null) {
            return null;
        }
        String trimmed = headerValue.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}

