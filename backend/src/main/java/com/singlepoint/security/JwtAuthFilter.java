package com.singlepoint.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.common.error.ApiError;
import com.singlepoint.common.error.AppException;
import com.singlepoint.user.domain.Role;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** Validates the Bearer token, populates the SecurityContext and the tenant/MDC context. */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            AppPrincipal principal;
            try {
                principal = jwtService.parse(token);
            } catch (AppException ex) {
                writeError(response, ex);
                return;
            }

            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(principal.getRole().authority())));
            SecurityContextHolder.getContext().setAuthentication(auth);

            if (principal.getRole() == Role.SUPER_ADMIN) {
                TenantContext.setWildcard();
                MDC.put("tenantId", "*");
            } else if (principal.getTenantId() != null) {
                TenantContext.setTenant(principal.getTenantId());
                MDC.put("tenantId", principal.getTenantId().toString());
            }
            MDC.put("userId", principal.getUserId().toString());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }

    private void writeError(HttpServletResponse response, AppException ex) throws IOException {
        response.setStatus(ex.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(ex.code(), ex.getMessage(), MDC.get("requestId"), null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
