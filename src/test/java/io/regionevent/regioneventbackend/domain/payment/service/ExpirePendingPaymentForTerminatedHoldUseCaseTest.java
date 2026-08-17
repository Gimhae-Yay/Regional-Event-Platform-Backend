package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;

class ExpirePendingPaymentForTerminatedHoldUseCaseTest {

    @Test
    void 결제가_없는_종결_홀드도_성공_감사를_기록한다() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentIdempotencyService paymentIdempotencyService = mock(PaymentIdempotencyService.class);
        CouponService couponService = mock(CouponService.class);
        CouponStatusHistoryService couponStatusHistoryService = mock(CouponStatusHistoryService.class);
        RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        ExpirePendingPaymentForTerminatedHoldUseCase useCase =
            new ExpirePendingPaymentForTerminatedHoldUseCase(
                paymentService,
                paymentIdempotencyService,
                couponService,
                couponStatusHistoryService,
                recordAuditEventUseCase
            );
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(10L);
        Instant terminatedAt = Instant.parse("2026-08-16T06:00:00Z");
        CapacityHoldService.TerminatedCapacityHold capacityHold =
            new CapacityHoldService.TerminatedCapacityHold(
                500L,
                region,
                2,
                CapacityHoldStatus.INVALIDATED,
                "CONTENT_WITHDRAWN",
                terminatedAt
            );
        when(paymentService.expirePendingByHoldId(500L, terminatedAt)).thenReturn(Optional.empty());
        UUID requestId = UUID.randomUUID();

        assertThat(useCase.expire(capacityHold, requestId, null)).isFalse();

        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(AuditEventTargetType.CAPACITY_HOLD);
        assertThat(captor.getValue().previousState()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().nextState()).isEqualTo("INVALIDATED");
        assertThat(captor.getValue().reasonCode()).isEqualTo("CONTENT_WITHDRAWN");
        assertThat(captor.getValue().requestId()).isEqualTo(requestId);
    }
}
