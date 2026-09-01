package com.singlepoint.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.common.error.ApiError;
import com.singlepoint.common.error.ErrorCode;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final String[] PUBLIC = {
            "/api/v1/auth/**",
            // Attachments are addressed by an unguessable UUID key. MVP-1 local convenience so
            // <Image> tags render without an auth header; cloud uses presigned S3 URLs instead.
            "/api/v1/files/**",
            "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
            "/swagger-ui/**", "/swagger-ui.html",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/error"
    };

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final String allowedOrigins;

    public SecurityConfig(JwtService jwtService, ObjectMapper objectMapper,
                          @Value("${sp.cors.allowed-origins:*}") String allowedOrigins) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(corsSource()).and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
                .antMatchers(PUBLIC).permitAll()
                .antMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll()
            .and()
            .exceptionHandling()
                .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, ErrorCode.UNAUTHENTICATED))
                .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, ErrorCode.FORBIDDEN))
            .and()
            .addFilterBefore(new JwtAuthFilter(jwtService, objectMapper),
                             UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(javax.servlet.http.HttpServletResponse res, int status, ErrorCode code) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getWriter(),
                new ApiError(code.code(), code.defaultMessage(), MDC.get("requestId"), null));
    }

    @Bean
    CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        if ("*".equals(allowedOrigins.trim())) {
            cfg.addAllowedOriginPattern("*");
        } else {
            cfg.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
        }
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
