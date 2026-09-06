package com.singlepoint.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.common.error.ApiError;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
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
    private final com.singlepoint.user.AppUserRepository users;
    private final com.singlepoint.notification.DeviceTokenRepository devices;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper,
                         com.singlepoint.user.AppUserRepository users,
                         com.singlepoint.notification.DeviceTokenRepository devices) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.users = users;
        this.devices = devices;
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

            // MVP-10 (B): a token minted with a device claim is bound to that device — a
            // copied/replayed token used without it (or with a different one) is rejected,
            // forcing a fresh OTP sign-in there. A token with no claim (legacy / a client that
            // has not adopted the header) is never gated, for backward compatibility.
            if (principal.getDeviceId() != null
                    && !principal.getDeviceId().equals(request.getHeader("X-Device-Id"))) {
                writeError(response, new AppException(ErrorCode.DEVICE_MISMATCH, null));
                return;
            }

            // MVP-13 (C3): the user signed this device out from another one.
            if (principal.getDeviceId() != null
                    && devices.existsByUserIdAndDeviceIdAndRevokedAtIsNotNull(
                            principal.getUserId(), principal.getDeviceId())) {
                writeError(response, new AppException(ErrorCode.DEVICE_MISMATCH, "This device was signed out"));
                return;
            }

            // MVP-13 (C2): a closed account's tokens are dead immediately, not at expiry.
            if (users.existsByIdAndDeletedAtIsNotNull(principal.getUserId())) {
                writeError(response, new AppException(ErrorCode.UNAUTHENTICATED, "This account has been closed"));
                return;
            }

            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(principal.getRole().authority())));
            SecurityContextHolder.getContext().setAuthentication(auth);

            if (principal.getRole() == Role.SUPER_ADMIN && principal.getTenantId() == null) {
                // Platform-wide: cross-tenant reads via the RLS wildcard.
                TenantContext.setWildcard();
                MDC.put("tenantId", "*");
            } else if (principal.getTenantId() != null) {
                // A Super Admin with a tenant claim is "acting as admin" — scope to that community.
                TenantContext.setTenant(principal.getTenantId());
                MDC.put("tenantId", principal.getTenantId().toString());
            }
            // MVP-8: the RLS null-tenant branch matches on this for community-less requests.
            UserContext.set(principal.getUserId());
            MDC.put("userId", principal.getUserId().toString());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            UserContext.clear();
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
