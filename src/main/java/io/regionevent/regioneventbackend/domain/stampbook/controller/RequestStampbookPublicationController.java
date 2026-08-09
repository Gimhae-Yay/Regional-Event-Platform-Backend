package io.regionevent.regioneventbackend.domain.stampbook.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.RequestStampbookPublicationRequest;
import io.regionevent.regioneventbackend.domain.stampbook.dto.RequestStampbookPublicationResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationUseCase.RequestStampbookPublicationCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/stampbooks")
public class RequestStampbookPublicationController {

    private static final String SUCCESS_MESSAGE = "스탬프북 공개 심사 요청에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final RequestStampbookPublicationUseCase requestStampbookPublicationUseCase;

    public RequestStampbookPublicationController(
        RequestStampbookPublicationUseCase requestStampbookPublicationUseCase
    ) {
        this.requestStampbookPublicationUseCase = requestStampbookPublicationUseCase;
    }

    @PostMapping("/{stampbookId}/publish")
    public ResponseEntity<ApiResponse<RequestStampbookPublicationResponse>> requestPublication(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId,
        @Valid @RequestBody RequestStampbookPublicationRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RequestStampbookPublicationResult result = requestStampbookPublicationUseCase.request(
            userId,
            new RequestStampbookPublicationCommand(
                parsePositiveId(stampbookId),
                request.reason()
            ),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            RequestStampbookPublicationResponse.from(result)
        ).toResponseEntity();
    }

    private Long parsePositiveId(String value) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
