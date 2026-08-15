package io.regionevent.regioneventbackend.domain.mission.controller;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.regionevent.regioneventbackend.global.config.RequestIdFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PublicMissionDetailLogUriFilter extends OncePerRequestFilter {

    private static final Pattern TARGET_URI_PATTERN = Pattern.compile("^/api/v1/missions/[^/]+$");
    private static final String TARGET_URI_TEMPLATE = "/api/v1/missions/{missionId}";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.name().equals(request.getMethod())
            || !TARGET_URI_PATTERN.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        request.setAttribute(RequestIdFilter.REQUEST_LOG_URI_ATTRIBUTE, TARGET_URI_TEMPLATE);
        filterChain.doFilter(request, response);
    }
}
