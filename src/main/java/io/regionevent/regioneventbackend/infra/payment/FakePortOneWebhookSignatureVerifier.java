package io.regionevent.regioneventbackend.infra.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;

@Component
@ConditionalOnProperty(name = "portone.fake.enabled", havingValue = "true")
public class FakePortOneWebhookSignatureVerifier extends PortOneWebhookSignatureVerifier {

    public FakePortOneWebhookSignatureVerifier(PortOneProperties properties) {
        super(properties);
    }

    @Override
    public void verify(String webhookId, String timestamp, String signature, String rawBody) {
    }
}
