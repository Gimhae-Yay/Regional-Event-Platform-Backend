package io.regionevent.regioneventbackend.infra.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway.PortOneCancellation;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway.PortOnePayment;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneResponseException;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;

class PortOnePaymentAdapterTest {

    @Test
    void findByPaymentId_취소된_결제_응답이면_DECLINED로_정규화한다() throws Exception {
        PortOnePayment payment = findPaymentByResponse("CANCELLED");

        assertThat(payment.status()).isEqualTo("DECLINED");
    }

    @Test
    void findByPaymentId_실패한_결제_응답이면_DECLINED로_정규화한다() throws Exception {
        PortOnePayment payment = findPaymentByResponse("FAILED");

        assertThat(payment.status()).isEqualTo("DECLINED");
    }

    @Test
    void findByPaymentId_원문_바이트가_다르면_서로_다른_SHA_256_해시를_반환한다() throws Exception {
        byte[] firstResponseBody = paymentResponseBody(
            "{\"id\":\"payment-1\",\"transactionId\":\"transaction-1\",\"storeId\":\"store-1\","
                + "\"amount\":{\"total\":20000},\"currency\":\"KRW\",\"status\":\"PAID\"}"
        );
        byte[] secondResponseBody = paymentResponseBody(
            "{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":20000},"
                + "\"storeId\":\"store-1\",\"transactionId\":\"transaction-1\",\"id\":\"payment-1\"}"
        );

        PortOnePayment firstPayment = findPaymentByResponse(firstResponseBody);
        PortOnePayment secondPayment = findPaymentByResponse(secondResponseBody);

        assertThat(firstPayment.status()).isEqualTo(secondPayment.status());
        assertThat(firstPayment.resultHash()).isEqualTo(PortOnePaymentAdapter.hash(firstResponseBody));
        assertThat(secondPayment.resultHash()).isEqualTo(PortOnePaymentAdapter.hash(secondResponseBody));
        assertThat(firstPayment.resultHash()).isNotEqualTo(secondPayment.resultHash());
    }

    @Test
    void findByPaymentId_취소_이력이_있으면_마지막_취소의_상태와_원문_해시를_보존한다() throws Exception {
        byte[] responseBody = paymentResponseBody("""
            {"id":"payment-1","transactionId":"transaction-1","storeId":"store-1",
            "amount":{"total":20000},"currency":"KRW","status":"CANCELLED",
            "cancellations":[
              {"id":"cancel-1","status":"FAILED"},
              {"id":"cancel-2","status":"SUCCEEDED"}
            ]}
            """);

        PortOnePayment payment = findPaymentByResponse(responseBody);

        assertThat(payment.cancellation()).isEqualTo(new PortOneCancellation(
            "cancel-2",
            "SUCCEEDED",
            PortOnePaymentAdapter.hash(responseBody)
        ));
    }

    @Test
    void hash_성공과_명시적_실패_응답_원문이_다르면_서로_다른_SHA_256을_반환한다() {
        byte[] succeededResponse = "{\"cancellation\":{\"id\":\"cancel-1\",\"status\":\"SUCCEEDED\"}}"
            .getBytes(StandardCharsets.UTF_8);
        byte[] failedResponse = "{\"cancellation\":{\"id\":\"cancel-2\",\"status\":\"FAILED\"}}"
            .getBytes(StandardCharsets.UTF_8);

        String succeededHash = PortOnePaymentAdapter.hash(succeededResponse);
        String failedHash = PortOnePaymentAdapter.hash(failedResponse);

        assertThat(succeededHash)
            .isEqualTo("348114deb878d7f0476517062eddb997092aaf354b6beea252a16ab33381a4a1");
        assertThat(failedHash).isNotEqualTo(succeededHash);
        assertThat(succeededHash).doesNotContain("cancel-1", "SUCCEEDED");
        assertThat(failedHash).doesNotContain("cancel-2", "FAILED");
    }

    @Test
    void cancelPayment_비동기_취소_응답이면_요청과_원문_해시를_보존한다() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        PortOneProperties properties = mock(PortOneProperties.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] responseBody = "{\"cancellation\":{\"id\":\"cancel-1\",\"status\":\"REQUESTED\"}}"
            .getBytes(StandardCharsets.UTF_8);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(httpClient.<byte[]>send(any(), any())).thenReturn(response);
        when(properties.getApiSecret()).thenReturn("secret");

        PortOneCancellation cancellation = new PortOnePaymentAdapter(properties, httpClient)
            .cancelPayment("payment-1", 20_000L, "고객 요청");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(requestCaptor.capture(), any());
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("https://api.portone.io/payments/payment-1/cancel");
        assertThat(request.headers().firstValue("Authorization")).contains("PortOne secret");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(readRequestBody(request)).isEqualTo("{\"amount\":20000,\"reason\":\"고객 요청\"}");
        assertThat(cancellation.cancellationId()).isEqualTo("cancel-1");
        assertThat(cancellation.status()).isEqualTo("REQUESTED");
        assertThat(cancellation.resultHash()).isEqualTo(PortOnePaymentAdapter.hash(responseBody));
        assertThat(cancellation.isSucceeded()).isFalse();
        assertThat(cancellation.isExplicitlyFailed()).isFalse();
    }

    @Test
    void cancelPayment_HTTP_오류_응답이면_상태와_원문_해시를_보존한다() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        PortOneProperties properties = mock(PortOneProperties.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] responseBody = "{\"code\":\"INVALID_REQUEST\"}".getBytes(StandardCharsets.UTF_8);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn(responseBody);
        when(httpClient.<byte[]>send(any(), any())).thenReturn(response);
        when(properties.getApiSecret()).thenReturn("secret");

        org.assertj.core.api.ThrowableAssert.ThrowingCallable action = () -> new PortOnePaymentAdapter(
            properties,
            httpClient
        ).cancelPayment("payment-1", 20_000L, "고객 요청");

        org.assertj.core.api.Assertions.assertThatThrownBy(action)
            .isInstanceOf(PortOneResponseException.class)
            .satisfies(exception -> {
                PortOneResponseException responseException = (PortOneResponseException) exception;
                assertThat(responseException.getExternalStatus()).isEqualTo("HTTP_400");
                assertThat(responseException.getResultHash()).isEqualTo(PortOnePaymentAdapter.hash(responseBody));
            });
    }

    @Test
    void cancelPayment_정상이지만_형식이_잘못된_응답이면_원문_해시를_보존한다() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        PortOneProperties properties = mock(PortOneProperties.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] responseBody = "not-json".getBytes(StandardCharsets.UTF_8);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(httpClient.<byte[]>send(any(), any())).thenReturn(response);
        when(properties.getApiSecret()).thenReturn("secret");

        org.assertj.core.api.ThrowableAssert.ThrowingCallable action = () -> new PortOnePaymentAdapter(
            properties,
            httpClient
        ).cancelPayment("payment-1", 20_000L, "고객 요청");

        org.assertj.core.api.Assertions.assertThatThrownBy(action)
            .isInstanceOf(PortOneResponseException.class)
            .satisfies(exception -> {
                PortOneResponseException responseException = (PortOneResponseException) exception;
                assertThat(responseException.getExternalStatus()).isEqualTo("INVALID_RESPONSE");
                assertThat(responseException.getResultHash()).isEqualTo(PortOnePaymentAdapter.hash(responseBody));
            });
    }

    private PortOnePayment findPaymentByResponse(String status) throws Exception {
        return findPaymentByResponse(paymentResponseBody(("""
            {\"id\":\"payment-1\",\"transactionId\":\"transaction-1\",\"storeId\":\"store-1\",\
            \"amount\":{\"total\":20000},\"currency\":\"KRW\",\"status\":\"%s\"}
            """).formatted(status)));
    }

    private PortOnePayment findPaymentByResponse(byte[] responseBody) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        PortOneProperties properties = mock(PortOneProperties.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(httpClient.<byte[]>send(any(), any())).thenReturn(response);
        when(properties.getApiSecret()).thenReturn("secret");

        return new PortOnePaymentAdapter(properties, httpClient)
            .findByPaymentId("payment-1");
    }

    private byte[] paymentResponseBody(String responseBody) {
        return responseBody.getBytes(StandardCharsets.UTF_8);
    }

    private String readRequestBody(HttpRequest request) {
        CompletableFuture<String> body = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {

            private final ByteArrayOutputStream output = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(output.toString(StandardCharsets.UTF_8));
            }
        });
        return body.join();
    }
}
