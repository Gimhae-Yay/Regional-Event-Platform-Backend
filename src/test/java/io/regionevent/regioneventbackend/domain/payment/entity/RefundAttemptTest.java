package io.regionevent.regioneventbackend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RefundAttemptTest {

    @Test
    void 대기_시도를_응답_완료로_전이한다() {
        RefundAttempt attempt = new RefundAttempt(
            mock(Refund.class),
            1,
            RefundAttemptInitiatorKind.PLATFORM_ADMIN,
            Instant.parse("2026-08-10T00:00:00Z")
        );

        attempt.respond("cancel-1", "SUCCEEDED", "result-hash");

        assertThat(attempt.getOutcomeKind()).isEqualTo(RefundAttemptOutcomeKind.RESPONDED);
        assertThat(attempt.getPortoneCancellationId()).isEqualTo("cancel-1");
    }

    @Test
    void 완료된_시도는_응답없음으로_다시_전이할_수_없다() {
        RefundAttempt attempt = new RefundAttempt(
            mock(Refund.class),
            1,
            RefundAttemptInitiatorKind.PLATFORM_ADMIN,
            Instant.parse("2026-08-10T00:00:00Z")
        );
        attempt.respond("cancel-1", "SUCCEEDED", "result-hash");

        assertThatThrownBy(() -> attempt.noResponse(RefundFailureReasonCode.NETWORK))
            .isInstanceOf(IllegalStateException.class);
    }
}
