package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionReadProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class QrExceptionReadServiceTest {

    private static final Long REGION_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
    private final QrExceptionReadService qrExceptionReadService = new QrExceptionReadService(
        auditEventRepository,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void findAll_cursor_is_before_retention_period_then_rejects_with_INVALID_INPUT() {
        Instant expiredCursorOccurredAt = NOW.minus(Duration.ofDays(90)).minusNanos(1);

        assertThatThrownBy(() -> qrExceptionReadService.findAll(REGION_ID, expiredCursorOccurredAt, 10L, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void findAll_passes_literal_reason_code_prefixes_to_repository() {
        when(auditEventRepository.findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            isNull(),
            isNull(),
            any(String.class),
            any(String.class),
            any(String.class),
            any(Pageable.class)
        )).thenReturn(List.of());

        qrExceptionReadService.findAll(REGION_ID, null, null, 20);

        verify(auditEventRepository).findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            isNull(),
            isNull(),
            eq("QR_CHECK_IN_"),
            eq("QR_VERIFICATION_FAILED"),
            eq("MANUAL_CHECK_IN_"),
            any(Pageable.class)
        );
    }

    @Test
    void findAll_visit_target_is_missing_then_rejects_with_INTERNAL_SERVER_ERROR() {
        when(auditEventRepository.findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            isNull(),
            isNull(),
            any(String.class),
            any(String.class),
            any(String.class),
            any(Pageable.class)
        )).thenReturn(List.of(new QrExceptionReadProjection(
            10L,
            NOW.minusSeconds(1),
            REGION_ID,
            AuditEventTargetType.VISIT,
            500L,
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )));

        assertThatThrownBy(() -> qrExceptionReadService.findAll(REGION_ID, null, null, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    @Test
    void findAll_예약_대상_지역_관계가_불일치하면_INTERNAL_SERVER_ERROR로_거부한다() {
        when(auditEventRepository.findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            isNull(),
            isNull(),
            any(String.class),
            any(String.class),
            any(String.class),
            any(Pageable.class)
        )).thenReturn(List.of(new QrExceptionReadProjection(
            10L,
            NOW.minusSeconds(1),
            REGION_ID,
            AuditEventTargetType.RESERVATION,
            100L,
            AuditEventResult.SUCCESS,
            "QR_VERIFICATION_FAILED",
            100L,
            2L,
            200L,
            REGION_ID,
            300L,
            REGION_ID,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )));

        assertThatThrownBy(() -> qrExceptionReadService.findAll(REGION_ID, null, null, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }
}
