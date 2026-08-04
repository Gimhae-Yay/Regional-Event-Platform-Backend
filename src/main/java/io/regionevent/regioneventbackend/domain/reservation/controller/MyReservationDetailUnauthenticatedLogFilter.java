package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class MyReservationDetailUnauthenticatedLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MyReservationDetailUnauthenticatedLogFilter.class);
    private static final Pattern TARGET_URI_PATTERN = Pattern.compile("^/api/v1/me/reservations/([^/]+)$");
    private static final String TARGET_URI_TEMPLATE = "/api/v1/me/reservations/{reservationId}";

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
        if (response.getStatus() == ErrorCode.UNAUTHENTICATED.httpStatus().value()) {
            logUnauthenticatedResult(request);
        }
    }

    private void logUnauthenticatedResult(HttpServletRequest request) {
        Matcher matcher = TARGET_URI_PATTERN.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            return;
        }
        log.info(
            "Reservation detail read. requestId={}, reservationId={}, sessionId={}, visitId={}, resultCode={}",
            request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE),
            MyReservationDetailRequestIdParser.parseOrNull(matcher.group(1)),
            null,
            null,
            ErrorCode.UNAUTHENTICATED.code()
        );
    }
}
