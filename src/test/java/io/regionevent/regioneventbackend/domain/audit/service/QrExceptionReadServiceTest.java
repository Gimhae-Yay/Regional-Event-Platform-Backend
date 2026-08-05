package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    void findAll_cursor_is_after_now_then_rejects_with_INVALID_INPUT() {
        Instant futureCursorOccurredAt = NOW.plusNanos(1);

        assertThatThrownBy(() -> qrExceptionReadService.findAll(REGION_ID, futureCursorOccurredAt, 10L, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void findAll_cursor_boundary_does_not_exist_then_rejects_with_INVALID_INPUT() {
        when(auditEventRepository.existsQrExceptionCursorBoundary(
            eq(REGION_ID),
            eq(NOW),
            eq(10L),
            any(Instant.class),
            eq(NOW),
            any(String.class),
            any(String.class),
            any(String.class)
        )).thenReturn(false);

        assertThatThrownBy(() -> qrExceptionReadService.findAll(REGION_ID, NOW, 10L, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verify(auditEventRepository).existsQrExceptionCursorBoundary(
            eq(REGION_ID),
            eq(NOW),
            eq(10L),
            any(Instant.class),
            eq(NOW),
            eq("QR_CHECK_IN_"),
            eq("QR_VERIFICATION_FAILED"),
            eq("MANUAL_CHECK_IN_")
        );
        verifyNoMoreInteractions(auditEventRepository);
    }

    @Test
    void findAll_cursor_boundary_exists_then_queries_next_page() {
        Instant cursorOccurredAt = NOW.minusSeconds(1);
        Long cursorAuditEventId = 10L;
        when(auditEventRepository.existsQrExceptionCursorBoundary(
            eq(REGION_ID),
            eq(cursorOccurredAt),
            eq(cursorAuditEventId),
            any(Instant.class),
            eq(NOW),
            any(String.class),
            any(String.class),
            any(String.class)
        )).thenReturn(true);
        when(auditEventRepository.findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            eq(cursorOccurredAt),
            eq(cursorAuditEventId),
            any(String.class),
            any(String.class),
            any(String.class),
            any(Pageable.class)
        )).thenReturn(List.of());

        QrExceptionReadService.QrExceptionPage page = qrExceptionReadService.findAll(
            REGION_ID,
            cursorOccurredAt,
            cursorAuditEventId,
            20
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        verify(auditEventRepository).existsQrExceptionCursorBoundary(
            eq(REGION_ID),
            eq(cursorOccurredAt),
            eq(cursorAuditEventId),
            any(Instant.class),
            eq(NOW),
            eq("QR_CHECK_IN_"),
            eq("QR_VERIFICATION_FAILED"),
            eq("MANUAL_CHECK_IN_")
        );
        verify(auditEventRepository).findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            eq(cursorOccurredAt),
            eq(cursorAuditEventId),
            eq("QR_CHECK_IN_"),
            eq("QR_VERIFICATION_FAILED"),
            eq("MANUAL_CHECK_IN_"),
            any(Pageable.class)
        );
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
    void findAll_maps_qr_exception_items_and_detects_next_page() {
        when(auditEventRepository.findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            isNull(),
            isNull(),
            any(String.class),
            any(String.class),
            any(String.class),
            any(Pageable.class)
        )).thenReturn(List.of(
            unresolvedProjection(30L, "QR_CHECK_IN_SIGNATURE_INVALID", AuditEventResult.FAILURE),
            reservationProjection(20L),
            visitProjection(10L)
        ));

        QrExceptionReadService.QrExceptionPage page = qrExceptionReadService.findAll(REGION_ID, null, null, 2);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0))
            .satisfies(item -> {
                assertThat(item.exceptionId()).isEqualTo(30L);
                assertThat(item.exceptionType()).isEqualTo("QR_CHECK_IN_FAILURE");
                assertThat(item.result()).isEqualTo("FAILURE");
                assertThat(item.reasonCode()).isEqualTo("QR_CHECK_IN_SIGNATURE_INVALID");
                assertThat(item.reservationResolved()).isFalse();
                assertThat(item.reservationId()).isNull();
                assertThat(item.contentId()).isNull();
                assertThat(item.sessionId()).isNull();
            });
        assertThat(page.items().get(1))
            .satisfies(item -> {
                assertThat(item.exceptionId()).isEqualTo(20L);
                assertThat(item.exceptionType()).isEqualTo("RESERVATION_NUMBER_LOOKUP");
                assertThat(item.result()).isEqualTo("SUCCESS");
                assertThat(item.reservationResolved()).isTrue();
                assertThat(item.reservationId()).isEqualTo(100L);
                assertThat(item.contentId()).isEqualTo(300L);
                assertThat(item.sessionId()).isEqualTo(200L);
            });
    }

    @Test
    void findAll_maps_visit_exception_item() {
        when(auditEventRepository.findQrExceptionReadProjections(
            eq(REGION_ID),
            any(Instant.class),
            isNull(),
            isNull(),
            any(String.class),
            any(String.class),
            any(String.class),
            any(Pageable.class)
        )).thenReturn(List.of(visitProjection(10L)));

        QrExceptionReadService.QrExceptionPage page = qrExceptionReadService.findAll(REGION_ID, null, null, 20);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.exceptionId()).isEqualTo(10L);
                assertThat(item.exceptionType()).isEqualTo("MANUAL_CHECK_IN");
                assertThat(item.result()).isEqualTo("SUCCESS");
                assertThat(item.reservationResolved()).isTrue();
                assertThat(item.reservationId()).isEqualTo(400L);
                assertThat(item.contentId()).isEqualTo(600L);
                assertThat(item.sessionId()).isEqualTo(500L);
            });
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

    private QrExceptionReadProjection unresolvedProjection(
        Long auditEventId,
        String reasonCode,
        AuditEventResult result
    ) {
        return new QrExceptionReadProjection(
            auditEventId,
            NOW.minusSeconds(auditEventId),
            REGION_ID,
            AuditEventTargetType.RESERVATION,
            null,
            result,
            reasonCode,
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
        );
    }

    private QrExceptionReadProjection reservationProjection(Long auditEventId) {
        return new QrExceptionReadProjection(
            auditEventId,
            NOW.minusSeconds(auditEventId),
            REGION_ID,
            AuditEventTargetType.RESERVATION,
            100L,
            AuditEventResult.SUCCESS,
            "QR_VERIFICATION_FAILED",
            100L,
            REGION_ID,
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
        );
    }

    private QrExceptionReadProjection visitProjection(Long auditEventId) {
        return new QrExceptionReadProjection(
            auditEventId,
            NOW.minusSeconds(auditEventId),
            REGION_ID,
            AuditEventTargetType.VISIT,
            700L,
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS",
            null,
            null,
            null,
            null,
            null,
            null,
            700L,
            REGION_ID,
            400L,
            REGION_ID,
            500L,
            REGION_ID,
            600L,
            REGION_ID
        );
    }
}
