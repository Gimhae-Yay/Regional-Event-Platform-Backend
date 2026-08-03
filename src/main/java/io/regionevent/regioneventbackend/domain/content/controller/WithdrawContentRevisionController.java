package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.WithdrawContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.WithdrawContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.WithdrawContentRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.WithdrawContentRevisionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/content-revisions")
public class WithdrawContentRevisionController {

    private static final String SUCCESS_MESSAGE = "콘텐츠 수정본 철회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final WithdrawContentRevisionUseCase withdrawContentRevisionUseCase;

    public WithdrawContentRevisionController(WithdrawContentRevisionUseCase withdrawContentRevisionUseCase) {
        this.withdrawContentRevisionUseCase = withdrawContentRevisionUseCase;
    }

    @PostMapping("/{revisionId}/withdraw")
    public ResponseEntity<ApiResponse<WithdrawContentRevisionResponse>> withdrawContentRevision(
        Authentication authentication,
        @PathVariable String revisionId,
        @Valid @RequestBody WithdrawContentRevisionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        WithdrawContentRevisionResult result = withdrawContentRevisionUseCase.withdraw(
            toAuthenticatedUserId(authentication),
            toRevisionId(revisionId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            WithdrawContentRevisionResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long toRevisionId(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
