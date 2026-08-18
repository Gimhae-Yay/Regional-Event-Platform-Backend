package io.regionevent.regioneventbackend.infra.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneFakeProperties;

class FakePortOnePaymentAdapterTest {

    @Test
    void findByPaymentId_구성된결제값으로_결제완료결과를반환한다() {
        PortOneFakeProperties properties = properties();
        FakePortOnePaymentAdapter adapter = new FakePortOnePaymentAdapter(properties);

        PortOnePaymentGateway.PortOnePayment payment = adapter.findByPaymentId("order-100");

        assertThat(payment.paymentId()).isEqualTo("order-100");
        assertThat(payment.transactionId()).isEqualTo("fixture-transaction-order-100");
        assertThat(payment.storeId()).isEqualTo("fixture-store");
        assertThat(payment.amount()).isEqualTo(12_000L);
        assertThat(payment.currency()).isEqualTo("KRW");
        assertThat(payment.isPaid()).isTrue();
    }

    @Test
    void cancelPayment_요청값과무관하게_결정적성공결과를반환한다() {
        FakePortOnePaymentAdapter adapter = new FakePortOnePaymentAdapter(properties());

        PortOnePaymentGateway.PortOneCancellation cancellation = adapter.cancelPayment(
            "payment-100",
            12_000L,
            "MANUAL_REFUND"
        );

        assertThat(cancellation.cancellationId()).isEqualTo("fixture-cancellation-payment-100");
        assertThat(cancellation.isSucceeded()).isTrue();
    }

    private PortOneFakeProperties properties() {
        PortOneFakeProperties properties = new PortOneFakeProperties();
        properties.setPaymentAmount(12_000L);
        properties.setCurrency("KRW");
        properties.setStoreId("fixture-store");
        properties.setTransactionIdPrefix("fixture-transaction-");
        properties.setCancellationIdPrefix("fixture-cancellation-");
        return properties;
    }
}
