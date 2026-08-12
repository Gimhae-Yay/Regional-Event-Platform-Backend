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

    private PortOnePayment findPaymentByResponse(String status) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        PortOneProperties properties = mock(PortOneProperties.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] responseBody = ("""
            {"id":"payment-1","transactionId":"transaction-1","storeId":"store-1",\
            "amount":{"total":20000},"currency":"KRW","status":"%s"}
            """).formatted(status).getBytes(StandardCharsets.UTF_8);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(httpClient.<byte[]>send(any(), any())).thenReturn(response);
        when(properties.getApiSecret()).thenReturn("secret");

        return new PortOnePaymentAdapter(properties, httpClient)
            .findByPaymentId("payment-1");
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
