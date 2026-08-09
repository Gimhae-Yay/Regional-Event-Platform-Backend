package io.regionevent.regioneventbackend.domain.stampbook.controller;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.UpdateStampbookRequest;
import io.regionevent.regioneventbackend.domain.stampbook.dto.UpdateStampbookResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookUseCase.UpdateStampbookCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/stampbooks")
public class UpdateStampbookController {

    private static final String SUCCESS_MESSAGE = "스탬프북 수정에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final UpdateStampbookUseCase updateStampbookUseCase;

    public UpdateStampbookController(UpdateStampbookUseCase updateStampbookUseCase) {
        this.updateStampbookUseCase = updateStampbookUseCase;
    }

    @PatchMapping("/{stampbookId}")
    public ResponseEntity<ApiResponse<UpdateStampbookResponse>> updateStampbook(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId,
        @Valid @RequestBody UpdateStampbookRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        UpdateStampbookResult result = updateStampbookUseCase.update(
            userId,
            toCommand(stampbookId, request),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            UpdateStampbookResponse.from(result)
        ).toResponseEntity();
    }

    private UpdateStampbookCommand toCommand(
        String stampbookId,
        UpdateStampbookRequest request
    ) {
        List<Long> contentIds = request.contentIds() == null
            ? null
            : request.contentIds().stream().map(this::parsePositiveId).toList();
        Long rewardCouponPolicyId = request.rewardCouponPolicyId() == null
            ? null
            : parsePositiveId(request.rewardCouponPolicyId());
        return new UpdateStampbookCommand(
            parsePositiveId(stampbookId),
            contentIds,
            rewardCouponPolicyId,
            request.reason()
        );
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
