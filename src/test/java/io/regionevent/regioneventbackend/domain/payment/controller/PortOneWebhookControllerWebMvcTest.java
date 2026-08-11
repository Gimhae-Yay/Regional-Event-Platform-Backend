package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.regionevent.regioneventbackend.domain.payment.service.ReceivePortOneWebhookUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;

class PortOneWebhookControllerWebMvcTest {

    private static final String PATH = "/api/v1/webhooks/portone";
    private static final String WEBHOOK_ID = "webhook-id";
    private static final String WEBHOOK_TIMESTAMP = "1785983465";
    private static final String WEBHOOK_SIGNATURE = "v1,signature";

    private final ReceivePortOneWebhookUseCase receivePortOneWebhookUseCase = org.mockito.Mockito.mock(
        ReceivePortOneWebhookUseCase.class
    );

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PortOneWebhookController(receivePortOneWebhookUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void receive_validWebhook_returnsSuccessAndForwardsRawHeadersAndBody() throws Exception {
        String rawBody = validPaymentEvent();

        mockMvc.perform(webhookRequest(rawBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("웹훅 수신에 성공했습니다."))
            .andExpect(jsonPath("$.data").isEmpty());

        verify(receivePortOneWebhookUseCase).receive(
            WEBHOOK_ID,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            rawBody
        );
    }

    @Test
    void receive_invalidSignature_returnsUnauthorizedResponse() throws Exception {
        doThrow(new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID))
            .when(receivePortOneWebhookUseCase)
            .receive(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(webhookRequest(validPaymentEvent()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void receive_portOneLookupFails_returnsInternalServerErrorResponse() throws Exception {
        doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR))
            .when(receivePortOneWebhookUseCase)
            .receive(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(webhookRequest(validPaymentEvent()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.statusCode").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhookRequest(
        String rawBody
    ) {
        return post(PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header("webhook-id", WEBHOOK_ID)
            .header("webhook-timestamp", WEBHOOK_TIMESTAMP)
            .header("webhook-signature", WEBHOOK_SIGNATURE)
            .content(rawBody);
    }

    private String validPaymentEvent() {
        return """
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
    }
}
