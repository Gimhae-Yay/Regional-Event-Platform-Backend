package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.UpdateContentRevisionUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/content-revisions")
public class UpdateContentRevisionController {

    private static final String SUCCESS_MESSAGE = "콘텐츠 수정본 편집에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final UpdateContentRevisionUseCase updateContentRevisionUseCase;

    public UpdateContentRevisionController(UpdateContentRevisionUseCase updateContentRevisionUseCase) {
        this.updateContentRevisionUseCase = updateContentRevisionUseCase;
    }

    @PutMapping("/{revisionId}")
    public ResponseEntity<ApiResponse<UpdateContentRevisionResponse>> updateContentRevision(
        Authentication authentication,
        @PathVariable String revisionId,
        @Valid @RequestBody UpdateContentRevisionRequest request
    ) {
        UpdateContentRevisionResponse response = updateContentRevisionUseCase.updateRevision(
            toAuthenticatedUserId(authentication),
            toRevisionId(revisionId),
            request
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
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
