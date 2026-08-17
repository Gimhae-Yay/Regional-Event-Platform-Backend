package io.regionevent.regioneventbackend.infra.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;

class PortOneWebhookSignatureVerifierTest {

    private static final String WEBHOOK_ID = "msg_2KWPBgLlAfxdpx2AI54pPJ85f4W";
    private static final String WEBHOOK_SECRET = "webhook-test-secret";
    private static final String RAW_BODY = """
        {
          "type": "Transaction.Paid",
          "timestamp": "2026-08-06T02:31:05Z",
          "data": {
            "storeId": "store-1",
            "paymentId": "order-1",
            "transactionId": "transaction-1"
          }
        }
        """;

    @Test
    void verify_실제_SDK_서명과_원문_본문이_일치하면_검증한다() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = signature(WEBHOOK_ID, timestamp, RAW_BODY);

        assertThatCode(() -> verifier().verify(WEBHOOK_ID, timestamp, signature, RAW_BODY))
            .doesNotThrowAnyException();
    }

    @Test
    void verify_원문_본문이_위조되면_서명_검증에_실패한다() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = signature(WEBHOOK_ID, timestamp, RAW_BODY);

        assertThatThrownBy(() -> verifier().verify(
            WEBHOOK_ID,
            timestamp,
            signature,
            RAW_BODY.replace("order-1", "order-2")
        )).isInstanceOf(PortOneWebhookSignatureVerifier.InvalidWebhookSignatureException.class);
    }

    private PortOneWebhookSignatureVerifier verifier() {
        PortOneProperties properties = new PortOneProperties();
        properties.setWebhookSecret(Base64.getEncoder().encodeToString(
            WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8)
        ));
        return new PortOneWebhookSignatureVerifier(properties);
    }

    private String signature(String webhookId, String timestamp, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(
            (webhookId + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)
        );
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }
}
