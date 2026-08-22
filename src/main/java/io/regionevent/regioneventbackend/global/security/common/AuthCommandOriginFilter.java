package io.regionevent.regioneventbackend.global.security.common;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import io.regionevent.regioneventbackend.global.config.CorsProperties;

public class AuthCommandOriginFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_COMMAND_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout"
    );

    private final CorsProperties corsProperties;
    private final AccessDeniedHandler accessDeniedHandler;

    public AuthCommandOriginFilter(
        CorsProperties corsProperties,
        AccessDeniedHandler accessDeniedHandler
    ) {
        this.corsProperties = corsProperties;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !isAuthCommandPath(request);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!corsProperties.isAllowedOrigin(request.getHeader(HttpHeaders.ORIGIN))) {
            accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("Authentication command origin is not allowed")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthCommandPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return AUTH_COMMAND_PATHS.contains(requestPath);
    }
}
