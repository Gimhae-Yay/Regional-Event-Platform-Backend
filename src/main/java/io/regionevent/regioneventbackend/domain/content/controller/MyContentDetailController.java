package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetMyContentResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetMyContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.MyContentDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping({"/api/v1/operator/contents", "/operator/contents"})
public class MyContentDetailController {

    private static final String SUCCESS_MESSAGE = "내 콘텐츠 상세 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("[1-9]\\d*");

    private final GetMyContentUseCase getMyContentUseCase;

    public MyContentDetailController(GetMyContentUseCase getMyContentUseCase) {
        this.getMyContentUseCase = getMyContentUseCase;
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<GetMyContentResponse>> getMyContent(
        Authentication authentication,
        @PathVariable String contentId
    ) {
        MyContentDetailResult result = getMyContentUseCase.get(
            toAuthenticatedUserId(authentication),
            parsePositiveId(contentId)
        );
        return ApiResponse
            .success(HttpStatus.OK, SUCCESS_MESSAGE, GetMyContentResponse.from(result))
            .toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
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
