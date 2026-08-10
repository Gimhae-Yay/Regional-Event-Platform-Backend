package io.regionevent.regioneventbackend.global.security.access;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import io.regionevent.regioneventbackend.global.security.common.ApiResponseAuthenticationEntryPoint;

public class BearerAccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenService jwtAccessTokenService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public BearerAccessTokenAuthenticationFilter(
        JwtAccessTokenService jwtAccessTokenService,
        AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authorization.isBlank()) {
            if (requiresOptionalAuthenticationValidation(request)) {
                rejectAuthentication(request, response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            if (requiresOptionalAuthenticationValidation(request)) {
                rejectAuthentication(request, response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length());
        if (accessToken.isBlank()) {
            rejectAuthentication(request, response);
            return;
        }

        try {
            JwtAccessTokenService.AuthenticatedUser authenticatedUser = jwtAccessTokenService.authenticate(accessToken);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authenticatedUser.userId(),
                null,
                java.util.List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidAccessTokenException exception) {
            rejectAuthentication(request, response);
        }
    }

    private boolean requiresOptionalAuthenticationValidation(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod())
            && requestPath.matches("^/api/v1/regions/[^/]+/missions$");
    }

    private void rejectAuthentication(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        authenticationEntryPoint.commence(
            request,
            response,
            new InsufficientAuthenticationException("Invalid access token")
        );
    }
}
