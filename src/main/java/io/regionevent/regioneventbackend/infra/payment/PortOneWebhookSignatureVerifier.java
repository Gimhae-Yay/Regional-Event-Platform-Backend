package io.regionevent.regioneventbackend.infra.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;

@Component
@ConditionalOnProperty(name = "portone.fake.enabled", havingValue = "false", matchIfMissing = true)
public class PortOneWebhookSignatureVerifier {

    private final PortOneProperties properties;

    public PortOneWebhookSignatureVerifier(PortOneProperties properties) {
        this.properties = properties;
    }

    public void verify(String webhookId, String timestamp, String signature, String rawBody) {
        try {
            new WebhookVerifier(requireSecret()).verify(rawBody, webhookId, signature, timestamp);
        } catch (WebhookVerificationException | IllegalArgumentException exception) {
            throw new InvalidWebhookSignatureException();
        }
    }

    private String requireSecret() {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new InvalidWebhookSignatureException();
        }
        return secret;
    }

    public static class InvalidWebhookSignatureException extends RuntimeException {
    }
}
