package io.regionevent.regioneventbackend.support.jpa;

import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentLogService;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionService;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.content.service.DeleteContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.EndContentReservationsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.OriginalContentReviewTargetPolicy;
import io.regionevent.regioneventbackend.domain.content.service.OriginalContentReviewTargetService;
import io.regionevent.regioneventbackend.domain.content.service.PublicCatalogCacheInvalidator;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.SubmitContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.WithdrawContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectCleanupService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationIdentifierGenerator;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationPriceSnapshotService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;

@TestConfiguration
@Import({
    ApproveContentUseCase.class,
    ApproveContentSessionUseCase.class,
    DeleteContentUseCase.class,
    RejectContentUseCase.class,
    ApproveContentRevisionUseCase.class,
    RejectContentRevisionUseCase.class,
    WithdrawContentRevisionUseCase.class,
    CancelContentSessionUseCase.class,
    SubmitContentUseCase.class,
    EndContentReservationsUseCase.class,
    ContentService.class,
    ContentRevisionService.class,
    OriginalContentReviewTargetService.class,
    OriginalContentReviewTargetPolicy.class,
    ContentSessionService.class,
    ContentLogService.class,
    ImageObjectService.class,
    CapacityHoldService.class,
    ReservationService.class,
    RegionAdminAuthorizationService.class,
    OperatorAuthorizationService.class,
    RecordAuditEventUseCase.class,
    RecordFailedAuditEventUseCase.class,
    AuditEventService.class,
    AuditEventActorLinkService.class
})
public class ContentAtomicityJpaTestConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReservationIdentifierGenerator reservationIdentifierGenerator() {
        return new ReservationIdentifierGenerator();
    }

    @Bean
    QrTokenService qrTokenService() {
        return mock(QrTokenService.class);
    }

    @Bean
    PublicCatalogCacheInvalidator publicCatalogCacheInvalidator() {
        return mock(PublicCatalogCacheInvalidator.class);
    }

    @Bean
    ImageObjectCleanupService imageObjectCleanupService() {
        return mock(ImageObjectCleanupService.class);
    }

    @Bean
    CreateRefundUseCase createRefundUseCase() {
        return mock(CreateRefundUseCase.class);
    }

    @Bean
    PaymentService paymentService() {
        return mock(PaymentService.class);
    }

    @Bean
    ReservationPriceSnapshotService reservationPriceSnapshotService() {
        return mock(ReservationPriceSnapshotService.class);
    }

    @Bean
    CouponService couponService() {
        return mock(CouponService.class);
    }

    @Bean
    CouponRedemptionService couponRedemptionService() {
        return mock(CouponRedemptionService.class);
    }

    @Bean
    CouponStatusHistoryService couponStatusHistoryService() {
        return mock(CouponStatusHistoryService.class);
    }

    @Bean
    RestoreCouponUseCase restoreCouponUseCase() {
        return mock(RestoreCouponUseCase.class);
    }

    @Bean
    ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase() {
        return mock(ExpirePendingPaymentForTerminatedHoldUseCase.class);
    }
}
