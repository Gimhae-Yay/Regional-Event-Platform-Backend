package io.regionevent.regioneventbackend.domain.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetMyContentsResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetMyContentsUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class MyContentController {

    private static final String GET_MY_CONTENTS_SUCCESS_MESSAGE = "내 콘텐츠 목록 조회에 성공했습니다.";

    private final GetMyContentsUseCase getMyContentsUseCase;

    public MyContentController(GetMyContentsUseCase getMyContentsUseCase) {
        this.getMyContentsUseCase = getMyContentsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetMyContentsResponse>> getMyContents(
        Authentication authentication
    ) {
        GetMyContentsResponse response = GetMyContentsResponse.from(
            getMyContentsUseCase.findMyContents(toAuthenticatedUserId(authentication))
        );
        return ApiResponse
            .success(HttpStatus.OK, GET_MY_CONTENTS_SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
