package io.regionevent.regioneventbackend.domain.stampbook.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.CreateStampbookRequest;
import io.regionevent.regioneventbackend.domain.stampbook.dto.CreateStampbookResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookUseCase.CreateStampbookCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/stampbooks")
public class CreateStampbookController {

    private static final String SUCCESS_MESSAGE = "스탬프북 생성에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CreateStampbookUseCase createStampbookUseCase;

    public CreateStampbookController(CreateStampbookUseCase createStampbookUseCase) {
        this.createStampbookUseCase = createStampbookUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateStampbookResponse>> createStampbook(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateStampbookRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateStampbookResult result = createStampbookUseCase.create(
            userId,
            toCommand(request),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            CreateStampbookResponse.from(result)
        ).toResponseEntity();
    }

    private CreateStampbookCommand toCommand(CreateStampbookRequest request) {
        return new CreateStampbookCommand(
            parsePositiveId(request.regionId()),
            request.contentIds().stream().map(this::parsePositiveId).toList(),
            parsePositiveId(request.rewardCouponPolicyId()),
            request.reason()
        );
    }

    private Long parsePositiveId(String value) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
