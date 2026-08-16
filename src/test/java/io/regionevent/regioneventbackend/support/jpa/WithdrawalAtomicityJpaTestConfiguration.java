package io.regionevent.regioneventbackend.support.jpa;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.operator.service.OperatorApplicationService;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.payment.service.RefundService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.WithdrawUserUseCase;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@TestConfiguration
@Import({
    OperatorReservationAtomicityJpaTestConfiguration.class,
    WithdrawUserUseCase.class,
    AppUserService.class,
    UserRoleAssignmentService.class,
    ContentService.class,
    CouponService.class,
    CouponIssuanceService.class,
    CouponStatusHistoryService.class,
    CapacityHoldService.class,
    PaymentService.class,
    RefundService.class,
    ReservationService.class,
    OperatorApplicationService.class,
    VisitService.class,
    ReviewService.class,
    IdempotencyService.class,
    AuditEventActorLinkService.class
})
public class WithdrawalAtomicityJpaTestConfiguration {

    @Bean
    RefreshTokenService refreshTokenService() {
        return mock(RefreshTokenService.class);
    }
}
