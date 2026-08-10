package io.regionevent.regioneventbackend.domain.region.controller;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PlatformAdminRegionListUnauthenticatedLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminRegionListUnauthenticatedLogFilter.class);
    private static final String TARGET_URI = "/api/v1/platform-admin/regions";
    private static final int FAILURE_RESULT_COUNT = 0;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.name().equals(request.getMethod())
            || !TARGET_URI.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        request.setAttribute(RequestIdFilter.REQUEST_LOG_URI_ATTRIBUTE, TARGET_URI);
        filterChain.doFilter(request, response);
        if (response.getStatus() == ErrorCode.UNAUTHENTICATED.httpStatus().value()) {
            log.info(
                "Platform admin region list queried. requestId={}, resultCount={}, resultCode={}",
                request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE),
                FAILURE_RESULT_COUNT,
                ErrorCode.UNAUTHENTICATED.code()
            );
        }
    }
}
