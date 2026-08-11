package io.regionevent.regioneventbackend.infra.payment;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.Payment.Recognized;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;

@Component
public class PortOnePaymentAdapter implements PortOnePaymentGateway {

    private static final String API_BASE_URL = "https://api.portone.io";
    private static final String API_VERSION = "v2";
    private static final long LOOKUP_TIMEOUT_SECONDS = 30;

    private final PortOneProperties properties;

    public PortOnePaymentAdapter(PortOneProperties properties) {
        this.properties = properties;
    }

    @Override
    public PortOnePayment findByPaymentId(String paymentId) {
        try (PortOneClient client = new PortOneClient(
            requireSecret(properties.getApiSecret()),
            API_BASE_URL,
            API_VERSION
        )) {
            Payment payment = client.getPayment()
                .getPayment(paymentId)
                .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!(payment instanceof Recognized recognized)) {
                return new PortOnePayment(paymentId, "UNKNOWN", 0, "UNK", "PENDING");
            }
            String status = payment instanceof PaidPayment ? "PAID" : toStatus(payment);
            return new PortOnePayment(
                recognized.getId(),
                recognized.getTransactionId(),
                recognized.getAmount().getTotal(),
                recognized.getCurrency().getValue(),
                status
            );
        } catch (Exception exception) {
            throw new PortOneLookupException(exception);
        }
    }

    private String toStatus(Payment payment) {
        String typeName = payment.getClass().getSimpleName();
        return typeName.contains("Failed") || typeName.contains("Cancelled")
            ? "DECLINED"
            : "PENDING";
    }

    private String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PORTONE_API_SECRET must be configured");
        }
        return secret;
    }
}
