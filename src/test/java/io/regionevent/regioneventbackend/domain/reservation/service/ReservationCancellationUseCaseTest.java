package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;

class ReservationCancellationUseCaseTest {

    @Test
    void 결제행없는0원예약_최초취소에서만검증된쿠폰사용이력을복구하고감사를기록한다() {
        Fixture fixture = fixture(ReservationStatus.CONFIRMED);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Coupon coupon = mock(Coupon.class);
        CouponRedemption redemption = mock(CouponRedemption.class);
        Instant restoredAt = Instant.parse("2026-08-11T00:00:00Z");
        when(fixture.priceSnapshotService.findByHoldIdForUpdate(3L)).thenReturn(Optional.of(snapshot));
        when(snapshot.getReservationPriceSnapshotId()).thenReturn(4L);
        when(snapshot.getFinalAmount()).thenReturn(0L);
        when(snapshot.getCoupon()).thenReturn(coupon);
        when(coupon.getCouponId()).thenReturn(5L);
        when(fixture.couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(4L))
            .thenReturn(Optional.of(redemption));
        when(fixture.couponService.findByCouponIdForUpdate(5L)).thenReturn(Optional.of(coupon));
        when(redemption.getStatus()).thenReturn(CouponRedemptionStatus.CONFIRMED);
        when(redemption.getReservation()).thenReturn(fixture.reservation);
        when(redemption.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(redemption.getCoupon()).thenReturn(coupon);
        when(coupon.getStatus()).thenReturn(CouponStatus.USED);
        when(fixture.couponService.findCurrentDatabaseTime()).thenReturn(restoredAt);
        when(fixture.couponService.restoreUsedCoupon(coupon, restoredAt)).thenReturn(CouponStatus.AVAILABLE);

        fixture.useCase.cancel(1L, 2L, UUID.randomUUID());

        verify(redemption).reverse(restoredAt);
        verify(fixture.couponService).restoreUsedCoupon(coupon, restoredAt);
        verify(fixture.couponStatusHistoryService).create(any());
        verify(fixture.recordAuditEventUseCase, org.mockito.Mockito.times(2)).record(any());
    }

    @Test
    void 결제행이있는비승인예약_쿠폰복구나환불을새로시작하지않는다() {
        Fixture fixture = fixture(ReservationStatus.CONFIRMED);
        Payment payment = mock(Payment.class);
        when(fixture.paymentService.findByReservationId(2L)).thenReturn(Optional.of(payment));
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        fixture.useCase.cancel(1L, 2L, UUID.randomUUID());

        verify(fixture.couponService, never()).restoreUsedCoupon(any(), any());
        verify(fixture.createRefundUseCase, never()).prepareForReservationCancellation(any(), any(), any());
    }

    @Test
    void 이미취소된예약_쿠폰복구와환불을재실행하지않는다() {
        Fixture fixture = fixture(ReservationStatus.CANCELLED);
        when(fixture.paymentService.findByReservationId(2L)).thenReturn(Optional.empty());

        fixture.useCase.cancel(1L, 2L, UUID.randomUUID());

        verify(fixture.couponService, never()).restoreUsedCoupon(any(), any());
        verify(fixture.createRefundUseCase, never()).prepareForReservationCancellation(any(), any(), any());
    }

    private Fixture fixture(ReservationStatus reservationStatus) {
        AppUserService appUserService = mock(AppUserService.class);
        UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
        ReservationService reservationService = mock(ReservationService.class);
        ContentSessionService contentSessionService = mock(ContentSessionService.class);
        PaymentService paymentService = mock(PaymentService.class);
        CreateRefundUseCase createRefundUseCase = mock(CreateRefundUseCase.class);
        ReservationPriceSnapshotService priceSnapshotService = mock(ReservationPriceSnapshotService.class);
        CouponService couponService = mock(CouponService.class);
        CouponRedemptionService couponRedemptionService = mock(CouponRedemptionService.class);
        CouponStatusHistoryService couponStatusHistoryService = mock(CouponStatusHistoryService.class);
        RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        AppUser user = mock(AppUser.class);
        UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);
        Reservation reservation = mock(Reservation.class);
        CapacityHold capacityHold = mock(CapacityHold.class);
        ContentSession contentSession = mock(ContentSession.class);
        Region region = mock(Region.class);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(user.getUserId()).thenReturn(1L);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(roleAssignment.getRoleAssignmentId()).thenReturn(1L);
        when(roleAssignment.getAppUser()).thenReturn(user);
        when(roleAssignment.getRole()).thenReturn(UserRole.VISITOR);
        when(appUserService.findActiveUserForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRoleAssignmentService.findActiveVisitor(1L)).thenReturn(roleAssignment);
        when(reservationService.findCancellationLockTarget(2L, user))
            .thenReturn(new ReservationService.ReservationCancellationLockTarget(6L));
        when(reservationService.findOwnedReservationForUpdate(2L, user)).thenReturn(reservation);
        when(reservationService.findOwnedReservation(2L, user)).thenReturn(reservation);
        when(reservation.getReservationId()).thenReturn(2L);
        when(reservation.getStatus()).thenReturn(reservationStatus);
        when(reservation.getCapacityHold()).thenReturn(capacityHold);
        when(capacityHold.getHoldId()).thenReturn(3L);
        when(capacityHold.getQuantity()).thenReturn(1);
        when(reservation.getContentSession()).thenReturn(contentSession);
        when(contentSession.getSessionId()).thenReturn(6L);
        when(reservation.getRegion()).thenReturn(region);
        when(reservation.getCancelledAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));
        when(reservationService.cancelIfCancellable(2L, 1L)).thenReturn(true);

        ReservationCancellationUseCase useCase = new ReservationCancellationUseCase(
            appUserService,
            userRoleAssignmentService,
            reservationService,
            contentSessionService,
            paymentService,
            createRefundUseCase,
            priceSnapshotService,
            couponService,
            couponRedemptionService,
            couponStatusHistoryService,
            recordAuditEventUseCase,
            mock(RecordFailedAuditEventUseCase.class),
            transactionManager
        );
        return new Fixture(
            useCase,
            reservation,
            paymentService,
            createRefundUseCase,
            priceSnapshotService,
            couponService,
            couponRedemptionService,
            couponStatusHistoryService,
            recordAuditEventUseCase
        );
    }

    private record Fixture(
        ReservationCancellationUseCase useCase,
        Reservation reservation,
        PaymentService paymentService,
        CreateRefundUseCase createRefundUseCase,
        ReservationPriceSnapshotService priceSnapshotService,
        CouponService couponService,
        CouponRedemptionService couponRedemptionService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
    }
}
