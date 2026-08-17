package io.regionevent.regioneventbackend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PaymentDiscrepancyTest {

    @Test
    void resolveNoIssue_OPEN상태를문제없음종결상태로전이한다() {
        PaymentDiscrepancy discrepancy = openDiscrepancy();

        discrepancy.resolveNoIssue();

        assertThat(discrepancy.getStatus()).isEqualTo("RESOLVED_NO_ISSUE");
    }

    @Test
    void resolveNoIssue_OPEN이아닌상태는전이하지않는다() {
        PaymentDiscrepancy discrepancy = openDiscrepancy();
        discrepancy.resolveNoIssue();

        assertThatThrownBy(discrepancy::resolveNoIssue)
            .isInstanceOf(IllegalStateException.class);
        assertThat(discrepancy.getStatus()).isEqualTo("RESOLVED_NO_ISSUE");
    }

    private PaymentDiscrepancy openDiscrepancy() {
        return new PaymentDiscrepancy(
            mock(Payment.class),
            "AMOUNT_MISMATCH",
            "OPEN",
            Instant.parse("2026-08-12T00:00:00Z")
        );
    }
}
