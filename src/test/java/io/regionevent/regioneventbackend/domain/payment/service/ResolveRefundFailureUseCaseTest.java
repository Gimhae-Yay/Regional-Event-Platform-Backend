package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolveRefundFailureRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ResolveRefundFailureUseCaseTest {

    private static final Instant RESOLVED_AT = Instant.parse("2026-08-12T01:02:03Z");
    private static final UUID REQUEST_ID = UUID.fromString("06f74b1f-9d68-4219-bdcb-d3992ce93f4f");

    @Test
    void resolve_성공확정은불일치환불을종결하고환불감사를기록한다() {
        Fixture fixture = new Fixture();
        Refund refund = fixture.discrepantRefund();
        when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT, RefundStatus.SUCCEEDED);
        when(fixture.authorizationService.requireAuthorizedPlatformAdmin(1L)).thenReturn(fixture.assignment);
        when(fixture.refundService.findByRefundIdForUpdate(552L)).thenReturn(Optional.of(refund));

        ResolveRefundFailureResult result = fixture.useCase.resolve(
            1L,
            552L,
            new ResolveRefundFailureRequest("SUCCEEDED", "  PortOne 재조회 #5013  ", "  실제 성공 확인  "),
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new ResolveRefundFailureResult(552L, "SUCCEEDED", RESOLVED_AT));
        verify(refund).resolveAsSucceeded(RESOLVED_AT);
        verify(fixture.couponRedemptionService).findByReservationPriceSnapshotIdForUpdate(901L);
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(fixture.auditEventUseCase).record(auditCaptor.capture());
        AuditEventCommand audit = auditCaptor.getValue();
        assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
        assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.REFUND);
        assertThat(audit.targetId()).isEqualTo(552L);
        assertThat(audit.previousState()).isEqualTo("DISCREPANT");
        assertThat(audit.nextState()).isEqualTo("SUCCEEDED");
        assertThat(audit.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(audit.reasonCode()).isEqualTo("MANUAL_REFUND_SUCCEEDED");
        assertThat(audit.reason()).isEqualTo("실제 성공 확인");
        assertThat(audit.evidenceReference()).isEqualTo("PortOne 재조회 #5013");
        assertThat(audit.occurredAt()).isEqualTo(RESOLVED_AT);
    }

    @Test
    void resolve_실패확정은재시도가능상태로전이하고쿠폰을복구하지않는다() {
        Fixture fixture = new Fixture();
        Refund refund = fixture.discrepantRefund();
        when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT, RefundStatus.FAILED);
        when(fixture.authorizationService.requireAuthorizedPlatformAdmin(1L)).thenReturn(fixture.assignment);
        when(fixture.refundService.findByRefundIdForUpdate(552L)).thenReturn(Optional.of(refund));

        ResolveRefundFailureResult result = fixture.useCase.resolve(
            1L,
            552L,
            new ResolveRefundFailureRequest("FAILED", "증빙", "실제 미처리 확인"),
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new ResolveRefundFailureResult(552L, "FAILED", RESOLVED_AT));
        verify(refund).resolveAsFailed();
        verifyNoInteractions(
            fixture.couponService,
            fixture.couponRedemptionService,
            fixture.couponStatusHistoryService
        );
    }

    @Test
    void resolve_성공확정은일치하는사용쿠폰을같은요청식별자로복구하고감사한다() {
        Fixture fixture = new Fixture();
        Refund refund = fixture.discrepantRefund();
        Payment payment = refund.getPayment();
        io.regionevent.regioneventbackend.domain.reservation.entity.Reservation reservation = payment
            .getReservation();
        io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot snapshot = payment
            .getReservationPriceSnapshot();
        io.regionevent.regioneventbackend.domain.coupon.entity.Coupon coupon = snapshot.getCoupon();
        io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption redemption = mock(
            io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption.class
        );
        Instant restoredAt = Instant.parse("2026-08-12T01:02:04Z");
        when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT, RefundStatus.SUCCEEDED);
        when(fixture.authorizationService.requireAuthorizedPlatformAdmin(1L)).thenReturn(fixture.assignment);
        when(fixture.refundService.findByRefundIdForUpdate(552L)).thenReturn(Optional.of(refund));
        when(coupon.getCouponId()).thenReturn(701L);
        when(coupon.getStatus()).thenReturn(
            io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus.USED
        );
        when(fixture.couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(901L))
            .thenReturn(Optional.of(redemption));
        when(redemption.getStatus()).thenReturn(
            io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus.CONFIRMED
        );
        when(redemption.getCoupon()).thenReturn(coupon);
        when(redemption.getReservation()).thenReturn(reservation);
        when(redemption.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(fixture.couponService.findByCouponIdForUpdate(701L)).thenReturn(Optional.of(coupon));
        when(fixture.couponService.findCurrentDatabaseTime()).thenReturn(restoredAt);
        when(fixture.couponService.restoreUsedCoupon(coupon, restoredAt)).thenReturn(
            io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus.AVAILABLE
        );

        fixture.useCase.resolve(
            1L,
            552L,
            new ResolveRefundFailureRequest("SUCCEEDED", "증빙", "실제 성공 확인"),
            REQUEST_ID
        );

        verify(redemption).reverse(restoredAt);
        verify(fixture.couponService).restoreUsedCoupon(coupon, restoredAt);
        verify(fixture.couponStatusHistoryService).create(any());
        verify(fixture.auditEventUseCase, org.mockito.Mockito.times(2)).record(any());
    }

    @Test
    void resolve_종결상태환불은충돌로거부하고감사나쿠폰을변경하지않는다() {
        Fixture fixture = new Fixture();
        Refund refund = mock(Refund.class);
        when(refund.getStatus()).thenReturn(RefundStatus.SUCCEEDED);
        when(fixture.authorizationService.requireAuthorizedPlatformAdmin(1L)).thenReturn(fixture.assignment);
        when(fixture.refundService.findByRefundIdForUpdate(552L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> fixture.useCase.resolve(
            1L,
            552L,
            new ResolveRefundFailureRequest("FAILED", "증빙", "사유"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.REFUND_STATE_CONFLICT);

        verify(refund, never()).resolveAsSucceeded(RESOLVED_AT);
        verify(refund, never()).resolveAsFailed();
        verifyNoInteractions(
            fixture.couponService,
            fixture.couponRedemptionService,
            fixture.couponStatusHistoryService,
            fixture.auditEventUseCase
        );
    }

    @Test
    void resolve_허용하지않은확정상태와공백입력은조회전에거부한다() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.useCase.resolve(
            1L,
            552L,
            new ResolveRefundFailureRequest("DISCREPANT", " ", "사유"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(
            fixture.authorizationService,
            fixture.refundService,
            fixture.couponService,
            fixture.couponRedemptionService,
            fixture.couponStatusHistoryService,
            fixture.auditEventUseCase
        );
    }

    private static class Fixture {

        private final PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        private final RefundService refundService = mock(RefundService.class);
        private final CouponService couponService = mock(CouponService.class);
        private final CouponRedemptionService couponRedemptionService = mock(CouponRedemptionService.class);
        private final CouponStatusHistoryService couponStatusHistoryService = mock(CouponStatusHistoryService.class);
        private final RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        private final PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        private final ResolveRefundFailureUseCase useCase = new ResolveRefundFailureUseCase(
            authorizationService,
            refundService,
            couponService,
            couponRedemptionService,
            couponStatusHistoryService,
            auditEventUseCase,
            Clock.fixed(RESOLVED_AT, ZoneOffset.UTC)
        );

        private Refund discrepantRefund() {
            Refund refund = mock(Refund.class);
            Payment payment = mock(Payment.class);
            CapacityHold capacityHold = mock(CapacityHold.class);
            Region region = mock(Region.class);
            io.regionevent.regioneventbackend.domain.reservation.entity.Reservation reservation = mock(
                io.regionevent.regioneventbackend.domain.reservation.entity.Reservation.class
            );
            io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot snapshot = mock(
                io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot.class
            );
            io.regionevent.regioneventbackend.domain.content.entity.ContentSession contentSession = mock(
                io.regionevent.regioneventbackend.domain.content.entity.ContentSession.class
            );
            when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT);
            when(refund.getRefundId()).thenReturn(552L);
            when(refund.getPayment()).thenReturn(payment);
            when(payment.getCapacityHold()).thenReturn(capacityHold);
            when(capacityHold.getRegion()).thenReturn(region);
            when(region.getRegionId()).thenReturn(1L);
            when(payment.getReservation()).thenReturn(reservation);
            when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
            when(snapshot.getReservationPriceSnapshotId()).thenReturn(901L);
            when(snapshot.getCoupon()).thenReturn(mock(io.regionevent.regioneventbackend.domain.coupon.entity.Coupon.class));
            when(reservation.getStatus()).thenReturn(
                io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus.CANCELLED
            );
            when(reservation.getCancelledAt()).thenReturn(RESOLVED_AT.minusSeconds(60));
            when(reservation.getContentSession()).thenReturn(contentSession);
            when(contentSession.getStartsAt()).thenReturn(RESOLVED_AT.plusSeconds(60));
            when(couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(901L)).thenReturn(Optional.empty());
            AppUser appUser = mock(AppUser.class);
            when(assignment.getPlatformAdminAssignmentId()).thenReturn(101L);
            when(assignment.getAppUser()).thenReturn(appUser);
            when(assignment.isActive()).thenReturn(true);
            when(assignment.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
            when(appUser.getUserId()).thenReturn(1L);
            when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
            when(appUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
            return refund;
        }
    }
}
