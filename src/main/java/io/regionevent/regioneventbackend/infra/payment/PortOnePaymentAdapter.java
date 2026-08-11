package io.regionevent.regioneventbackend.infra.payment;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;

import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;

@Component
public class PortOnePaymentAdapter implements PortOnePaymentGateway {

    private static final String API_BASE_URL = "https://api.portone.io";
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final String AUTHORIZATION_PREFIX = "PortOne ";

    private final PortOneProperties properties;
    private final HttpClient httpClient;

    public PortOnePaymentAdapter(PortOneProperties properties) {
        this(properties, HttpClient.newBuilder()
            .connectTimeout(RESPONSE_TIMEOUT)
            .build());
    }

    PortOnePaymentAdapter(
        PortOneProperties properties,
        HttpClient httpClient
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public PortOnePayment findByPaymentId(String paymentId) {
        byte[] responseBody = send(HttpRequest.newBuilder(paymentUri(paymentId))
            .header("Authorization", authorizationHeader())
            .GET()
            .timeout(RESPONSE_TIMEOUT)
            .build());
        JsonObject payment = readResponse(responseBody);
        return new PortOnePayment(
            requiredText(payment, "id"),
            requiredText(payment, "transactionId"),
            optionalText(payment, "storeId"),
            requiredLong(requiredObject(payment, "amount"), "total"),
            requiredText(payment, "currency"),
            requiredText(payment, "status"),
            hash(responseBody)
        );
    }

    @Override
    public PortOneCancellation cancelPayment(String paymentId, long amount, String reason) {
        byte[] requestBody = toCancelRequestBody(amount, reason);
        byte[] responseBody = send(HttpRequest.newBuilder(cancellationUri(paymentId))
            .header("Authorization", authorizationHeader())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .timeout(RESPONSE_TIMEOUT)
            .build());
        JsonObject cancellation = requiredObject(readResponse(responseBody), "cancellation");
        return new PortOneCancellation(
            requiredText(cancellation, "id"),
            requiredText(cancellation, "status"),
            hash(responseBody)
        );
    }

    private byte[] send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortOneLookupException(new IllegalStateException(
                    "PortOne returned HTTP " + response.statusCode()
                ));
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PortOneNoResponseException(RefundFailureReasonCode.UNKNOWN, exception);
        } catch (java.net.http.HttpTimeoutException | SocketTimeoutException exception) {
            throw new PortOneNoResponseException(RefundFailureReasonCode.TIMEOUT, exception);
        } catch (ConnectException exception) {
            throw new PortOneNoResponseException(RefundFailureReasonCode.CONNECTION, exception);
        } catch (IOException exception) {
            throw new PortOneNoResponseException(RefundFailureReasonCode.NETWORK, exception);
        }
    }

    private byte[] toCancelRequestBody(long amount, String reason) {
        String requestBody = "{\"amount\":" + amount + ",\"reason\":\"" + escapeJson(reason) + "\"}";
        return requestBody.getBytes(StandardCharsets.UTF_8);
    }

    private JsonObject readResponse(byte[] responseBody) {
        try {
            JsonElement element = Json.Default.parseToJsonElement(
                new String(responseBody, StandardCharsets.UTF_8)
            );
            if (element instanceof JsonObject object) {
                return object;
            }
            throw new IllegalStateException("PortOne response must be a JSON object");
        } catch (RuntimeException exception) {
            throw new PortOneLookupException(exception);
        }
    }

    private URI paymentUri(String paymentId) {
        return URI.create(API_BASE_URL + "/payments/" + paymentId);
    }

    private URI cancellationUri(String paymentId) {
        return URI.create(API_BASE_URL + "/payments/" + paymentId + "/cancel");
    }

    private String authorizationHeader() {
        return AUTHORIZATION_PREFIX + requireSecret(properties.getApiSecret());
    }

    private String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PORTONE_API_SECRET must be configured");
        }
        return secret;
    }

    private String requiredText(JsonObject node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null) {
            throw new PortOneLookupException(new IllegalStateException(
                "PortOne response does not contain " + fieldName
            ));
        }
        return value;
    }

    private String optionalText(JsonObject node, String fieldName) {
        JsonElement value = node.get(fieldName);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return null;
        }
        String content = primitive.getContent();
        return content.isBlank() ? null : content;
    }

    private long requiredLong(JsonObject node, String fieldName) {
        JsonElement value = node.get(fieldName);
        if (!(value instanceof JsonPrimitive primitive)) {
            throw new PortOneLookupException(new IllegalStateException(
                "PortOne response does not contain " + fieldName
            ));
        }
        try {
            return Long.parseLong(primitive.getContent());
        } catch (NumberFormatException exception) {
            throw new PortOneLookupException(exception);
        }
    }

    private JsonObject requiredObject(JsonObject node, String fieldName) {
        JsonElement value = node.get(fieldName);
        if (value instanceof JsonObject object) {
            return object;
        }
        throw new PortOneLookupException(new IllegalStateException(
            "PortOne response does not contain " + fieldName
        ));
    }

    private String escapeJson(String value) {
        if (value == null) {
            throw new PortOneLookupException(new IllegalArgumentException("PortOne cancel reason must not be null"));
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> appendEscapedCharacter(escaped, character);
            }
        }
        return escaped.toString();
    }

    private void appendEscapedCharacter(StringBuilder escaped, char character) {
        if (character < 0x20) {
            escaped.append(String.format("\\u%04x", (int) character));
            return;
        }
        escaped.append(character);
    }

    static String hash(byte[] responseBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(responseBody);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
