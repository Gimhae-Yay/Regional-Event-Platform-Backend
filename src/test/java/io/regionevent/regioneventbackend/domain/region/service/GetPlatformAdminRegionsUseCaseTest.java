package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.regionevent.regioneventbackend.domain.region.repository.PlatformAdminRegionListProjection;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPlatformAdminRegionsUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;
    private static final String REQUEST_ID = "request-id";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService = mock(
        PlatformAdminAuthorizationService.class
    );
    private final RegionService regionService = mock(RegionService.class);
    private final GetPlatformAdminRegionsUseCase useCase = new GetPlatformAdminRegionsUseCase(
        platformAdminAuthorizationService,
        regionService
    );

    @Test
    void get_활성전체관리자_필터조건의지역목록을반환하고_성공로그를남긴다() {
        PlatformAdminRegionListProjection projection = new PlatformAdminRegionListProjection(
            11L,
            "GIMHAE",
            "김해시",
            false,
            2L,
            Instant.parse("2026-08-09T00:00:00Z"),
            Instant.parse("2026-08-09T01:00:00Z")
        );
        when(regionService.findPlatformAdminRegionList(false)).thenReturn(List.of(projection));
        Logger logger = (Logger) LoggerFactory.getLogger(GetPlatformAdminRegionsUseCase.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        try {
            MDC.put("requestId", REQUEST_ID);

            List<PlatformAdminRegionListInfo> result = useCase.get(ACTOR_USER_ID, false);

            assertThat(result).containsExactly(PlatformAdminRegionListInfo.from(projection));
            assertThat(logAppender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).contains(
                    "requestId=" + REQUEST_ID,
                    "resultCount=1",
                    "resultCode=SUCCESS"
                );
            });
        } finally {
            MDC.clear();
            logger.detachAppender(logAppender);
            logAppender.stop();
        }

        verify(platformAdminAuthorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(regionService).findPlatformAdminRegionList(false);
    }

    @Test
    void get_고권한인가에실패하면_지역을조회하지않고_실패로그를남긴다() {
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        Logger logger = (Logger) LoggerFactory.getLogger(GetPlatformAdminRegionsUseCase.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        try {
            MDC.put("requestId", REQUEST_ID);

            assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
            assertThat(logAppender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).contains(
                    "requestId=" + REQUEST_ID,
                    "resultCount=0",
                    "resultCode=FORBIDDEN"
                );
            });
        } finally {
            MDC.clear();
            logger.detachAppender(logAppender);
            logAppender.stop();
        }

        verify(regionService, never()).findPlatformAdminRegionList(null);
    }
}
