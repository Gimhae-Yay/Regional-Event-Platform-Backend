package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.service.ReceivePortOneWebhookUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/webhooks")
public class PortOneWebhookController {

    private final ReceivePortOneWebhookUseCase receivePortOneWebhookUseCase;

    public PortOneWebhookController(ReceivePortOneWebhookUseCase receivePortOneWebhookUseCase) {
        this.receivePortOneWebhookUseCase = receivePortOneWebhookUseCase;
    }

    @PostMapping("/portone")
    public ResponseEntity<ApiResponse<Void>> receive(
        @RequestHeader(name = "webhook-id", required = false) String webhookId,
        @RequestHeader(name = "webhook-timestamp", required = false) String webhookTimestamp,
        @RequestHeader(name = "webhook-signature", required = false) String webhookSignature,
        @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) MediaType contentType,
        @RequestBody String rawBody
    ) {
        validateJsonContentType(contentType);
        receivePortOneWebhookUseCase.receive(webhookId, webhookTimestamp, webhookSignature, rawBody);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
            HttpStatus.OK,
            "웹훅 수신에 성공했습니다."
        ));
    }

    private void validateJsonContentType(MediaType contentType) {
        if (contentType == null || !MediaType.APPLICATION_JSON.includes(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
