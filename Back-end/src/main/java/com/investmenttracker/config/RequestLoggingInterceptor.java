package com.investmenttracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs an INFO-level entry for every completed REST request containing the
 * HTTP method, request path, response status code, and latency in milliseconds.
 *
 * Requirements: 9.2
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long latencyMs = (startTime != null) ? System.currentTimeMillis() - startTime : -1;

        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();

        log.info("method={} path={} status={} latency={}ms", method, path, status, latencyMs);
    }
}
