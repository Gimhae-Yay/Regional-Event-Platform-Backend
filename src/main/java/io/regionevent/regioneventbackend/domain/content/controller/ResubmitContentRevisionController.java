package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.ResubmitContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.ResubmitContentRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.ResubmitContentRevisionUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/content-revisions")
public class ResubmitContentRevisionController {

    private static final String SUCCESS_MESSAGE = "콘텐츠 수정본 재제출에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final ResubmitContentRevisionUseCase resubmitContentRevisionUseCase;

    public ResubmitContentRevisionController(
        ResubmitContentRevisionUseCase resubmitContentRevisionUseCase
    ) {
        this.resubmitContentRevisionUseCase = resubmitContentRevisionUseCase;
    }

    @PostMapping("/{revisionId}/resubmit")
    public ResponseEntity<ApiResponse<ResubmitContentRevisionResponse>> resubmitContentRevision(
        Authentication authentication,
        @PathVariable String revisionId
    ) {
        ResubmitContentRevisionResult result = resubmitContentRevisionUseCase.resubmit(
            toAuthenticatedUserId(authentication),
            toRevisionId(revisionId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            ResubmitContentRevisionResponse.from(result)
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
