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

import io.regionevent.regioneventbackend.domain.stampbook.dto.RejectRegionAdminStampbookRequest;
import io.regionevent.regioneventbackend.domain.stampbook.dto.RejectRegionAdminStampbookResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookUseCase.RejectRegionAdminStampbookCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/stampbooks")
public class RejectRegionAdminStampbookController {

    private static final String SUCCESS_MESSAGE = "스탬프북 반려에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final RejectRegionAdminStampbookUseCase rejectRegionAdminStampbookUseCase;

    public RejectRegionAdminStampbookController(
        RejectRegionAdminStampbookUseCase rejectRegionAdminStampbookUseCase
    ) {
        this.rejectRegionAdminStampbookUseCase = rejectRegionAdminStampbookUseCase;
    }

    @PostMapping("/{stampbookId}/reject")
    public ResponseEntity<ApiResponse<RejectRegionAdminStampbookResponse>> reject(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId,
        @Valid @RequestBody RejectRegionAdminStampbookRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RejectRegionAdminStampbookResult result = rejectRegionAdminStampbookUseCase.reject(
            userId,
            new RejectRegionAdminStampbookCommand(
                parsePositiveId(stampbookId),
                request.reason()
            ),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            RejectRegionAdminStampbookResponse.from(result)
        ).toResponseEntity();
    }

    private Long parsePositiveId(String value) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
