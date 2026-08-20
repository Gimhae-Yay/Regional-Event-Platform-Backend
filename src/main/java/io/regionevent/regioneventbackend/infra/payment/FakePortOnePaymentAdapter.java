package io.regionevent.regioneventbackend.infra.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneFakeProperties;

@Component
@ConditionalOnProperty(name = "portone.fake.enabled", havingValue = "true")
public class FakePortOnePaymentAdapter implements PortOnePaymentGateway {

    private static final String PAID_STATUS = "PAID";
    private static final String SUCCEEDED_STATUS = "SUCCEEDED";

    private final PortOneFakeProperties properties;

    public FakePortOnePaymentAdapter(PortOneFakeProperties properties) {
        this.properties = properties;
    }

    @Override
    public PortOnePayment findByPaymentId(String paymentId) {
        return new PortOnePayment(
            paymentId,
            properties.getTransactionIdPrefix() + paymentId,
            properties.getStoreId(),
            properties.getPaymentAmount(),
            properties.getCurrency(),
            PAID_STATUS,
            "fake-payment-" + paymentId
        );
    }

    @Override
    public PortOneCancellation cancelPayment(String paymentId, long amount, String reason) {
        return new PortOneCancellation(
            properties.getCancellationIdPrefix() + paymentId,
            SUCCEEDED_STATUS,
            "fake-cancellation-" + paymentId
        );
    }
}
