package com.singlepoint.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/** Assigns a request id (MDC {@code requestId}) and writes one access-log line per request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcAccessLogFilter extends OncePerRequestFilter {

    private static final Logger access = LoggerFactory.getLogger("com.singlepoint.ACCESS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = System.currentTimeMillis() - start;
            access.info("{} {} -> {} ({} ms) ip={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), ms,
                    request.getRemoteAddr());
            MDC.remove("requestId");
        }
    }
}
