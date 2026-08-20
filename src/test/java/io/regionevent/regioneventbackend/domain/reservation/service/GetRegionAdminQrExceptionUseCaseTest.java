package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionAuditProjection;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetRegionAdminQrExceptionUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 100L;
    private static final Long OTHER_REGION_ID = 200L;
    private static final Long EXCEPTION_ID = 900L;
    private static final Long RESERVATION_ID = 123L;
    private static final Long VISIT_ID = 456L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T01:02:00Z");

    @Test
    void get_예약_대상_QR_예외를_마스킹된_예약_상세로_반환한다() {
        TestContext context = new TestContext();
        context.stubAudit(audit(AuditEventTargetType.RESERVATION, RESERVATION_ID, "QR_VERIFICATION_FAILED"));

        QrExceptionDetailResult result = context.useCase.get(USER_ID, EXCEPTION_ID);

        assertThat(result.exceptionId()).isEqualTo(EXCEPTION_ID);
        assertThat(result.exceptionType()).isEqualTo(QrExceptionType.RESERVATION_NUMBER_LOOKUP);
        assertThat(result.reservationResolved()).isTrue();
        assertThat(result.reservation().reservation().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.reservation().memberLinked()).isTrue();
        assertThat(result.reservation().participant().name()).isEqualTo("김*수");
        assertThat(result.reservation().participant().phone()).isEqualTo("010-****-5678");
        assertThat(result.reservation().checkIn().checkedIn()).isFalse();
        verify(context.regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
    }

    @Test
    void get_VISIT_대상이면_방문에서_예약_ID를_확인해_예약을_반환한다() {
        TestContext context = new TestContext();
        context.stubAudit(audit(
            AuditEventTargetType.VISIT,
            VISIT_ID,
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS"
        ));
        when(context.visitService.findReservationIdByVisitId(VISIT_ID)).thenReturn(Optional.of(RESERVATION_ID));

        QrExceptionDetailResult result = context.useCase.get(USER_ID, EXCEPTION_ID);

        assertThat(result.exceptionType()).isEqualTo(QrExceptionType.MANUAL_CHECK_IN);
        assertThat(result.reservationResolved()).isTrue();
        verify(context.visitService).findReservationIdByVisitId(VISIT_ID);
    }

    @Test
    void get_targetId가_없으면_예약_미해결로_반환하고_예약을_추정하지_않는다() {
        TestContext context = new TestContext();
        context.stubAudit(audit(AuditEventTargetType.RESERVATION, null, "QR_CHECK_IN_SIGNATURE_INVALID"));

        QrExceptionDetailResult result = context.useCase.get(USER_ID, EXCEPTION_ID);

        assertThat(result.exceptionType()).isEqualTo(QrExceptionType.QR_CHECK_IN_FAILURE);
        assertThat(result.reservationResolved()).isFalse();
        assertThat(result.reservation()).isNull();
        verifyNoInteractions(context.reservationReadService, context.reservationParticipantMasker);
    }

    @Test
    void get_감사_이벤트_지역이_없으면_NOT_FOUND를_반환한다() {
        TestContext context = new TestContext();
        context.stubAudit(new QrExceptionAuditProjection(
            EXCEPTION_ID,
            null,
            AuditEventTargetType.RESERVATION,
            RESERVATION_ID,
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED",
            OCCURRED_AT
        ));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void get_담당_지역이_다르면_FORBIDDEN을_반환한다() {
        TestContext context = new TestContext();
        context.stubAudit(new QrExceptionAuditProjection(
            EXCEPTION_ID,
            OTHER_REGION_ID,
            AuditEventTargetType.RESERVATION,
            RESERVATION_ID,
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED",
            OCCURRED_AT
        ));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    @Test
    void get_예약과_감사_지역이_불일치하면_정합성_오류를_전파한다() {
        TestContext context = new TestContext();
        context.stubAudit(audit(AuditEventTargetType.RESERVATION, RESERVATION_ID, "QR_VERIFICATION_FAILED"));
        when(context.reservationReadService.findByReservationId(RESERVATION_ID))
            .thenReturn(readResult(OTHER_REGION_ID));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("qr exception reservation relation is inconsistent");
    }

    @Test
    void get_QR_check_in_failure_with_VISIT_target_propagates_contract_error() {
        TestContext context = new TestContext();
        context.stubAudit(audit(AuditEventTargetType.VISIT, VISIT_ID, "QR_CHECK_IN_SIGNATURE_INVALID"));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("qr exception audit target contract is inconsistent");

        verifyNoInteractions(context.visitService);
    }

    @Test
    void get_reservation_number_lookup_with_VISIT_target_propagates_contract_error() {
        TestContext context = new TestContext();
        context.stubAudit(audit(AuditEventTargetType.VISIT, VISIT_ID, "QR_VERIFICATION_FAILED"));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("qr exception audit target contract is inconsistent");

        verifyNoInteractions(context.visitService);
    }

    @Test
    void get_manual_check_in_success_with_RESERVATION_target_propagates_contract_error() {
        TestContext context = new TestContext();
        context.stubAudit(audit(
            AuditEventTargetType.RESERVATION,
            RESERVATION_ID,
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS"
        ));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("qr exception audit target contract is inconsistent");
    }

    @Test
    void get_manual_check_in_failure_with_VISIT_target_propagates_contract_error() {
        TestContext context = new TestContext();
        context.stubAudit(audit(AuditEventTargetType.VISIT, VISIT_ID, "MANUAL_CHECK_IN_QR_SCAN_FAILED"));

        assertThatThrownBy(() -> context.useCase.get(USER_ID, EXCEPTION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("qr exception audit target contract is inconsistent");

        verifyNoInteractions(context.visitService);
    }

    private static QrExceptionAuditProjection audit(
        AuditEventTargetType targetType,
        Long targetId,
        String reasonCode
    ) {
        return audit(targetType, targetId, AuditEventResult.FAILURE, reasonCode);
    }

    private static QrExceptionAuditProjection audit(
        AuditEventTargetType targetType,
        Long targetId,
        AuditEventResult result,
        String reasonCode
    ) {
        return new QrExceptionAuditProjection(
            EXCEPTION_ID,
            REGION_ID,
            targetType,
            targetId,
            result,
            reasonCode,
            OCCURRED_AT
        );
    }

    private static ReservationReadResult readResult(Long regionId) {
        ReservationReadSnapshot snapshot = new ReservationReadSnapshot(
            new ReservationReadSnapshot.ReservationInfo(
                RESERVATION_ID,
                "R20260801ABCDEFGHJKLM",
                ReservationStatus.CONFIRMED,
                Instant.parse("2026-08-01T00:30:00Z"),
                null,
                null,
                null,
                1,
                regionId
            ),
            new ReservationReadSnapshot.SessionInfo(
                456L,
                ContentSessionStatus.SCHEDULED,
                Instant.parse("2026-08-01T01:00:00Z"),
                Instant.parse("2026-08-01T03:00:00Z"),
                Instant.parse("2026-08-01T00:30:00Z"),
                Instant.parse("2026-08-01T01:30:00Z"),
                regionId
            ),
            new ReservationReadSnapshot.ContentInfo(77L, "김해 도자기 체험", "김해시", regionId),
            new ReservationReadSnapshot.ParticipantInfo(300L, "김민수", "01012345678")
        );
        return new ReservationReadResult(
            snapshot,
            new ReservationReadIntegrityValidator.CheckInInfo(null, false, null)
        );
    }

    private static class TestContext {

        private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        private final RegionAdminAuthorizationService regionAdminAuthorizationService = mock(
            RegionAdminAuthorizationService.class
        );
        private final ReservationReadService reservationReadService = mock(ReservationReadService.class);
        private final ReservationParticipantMasker reservationParticipantMasker = mock(
            ReservationParticipantMasker.class
        );
        private final VisitService visitService = mock(VisitService.class);
        private final GetRegionAdminQrExceptionUseCase useCase = new GetRegionAdminQrExceptionUseCase(
            auditEventRepository,
            regionAdminAuthorizationService,
            reservationReadService,
            reservationParticipantMasker,
            visitService
        );

        private TestContext() {
            when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
            when(reservationReadService.findByReservationId(RESERVATION_ID)).thenReturn(readResult(REGION_ID));
            when(reservationParticipantMasker.mask(readResult(REGION_ID).snapshot().participant()))
                .thenReturn(new ReservationParticipantMasker.MaskedParticipant("김*수", "010-****-5678"));
        }

        private void stubAudit(QrExceptionAuditProjection audit) {
            when(auditEventRepository.findQrExceptionAuditProjectionById(EXCEPTION_ID))
                .thenReturn(Optional.of(audit));
        }
    }
}
