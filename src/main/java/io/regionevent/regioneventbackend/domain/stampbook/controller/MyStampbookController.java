package io.regionevent.regioneventbackend.domain.stampbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.GetMyStampbooksResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampbooksUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/stampbooks")
public class MyStampbookController {

    private static final String SUCCESS_MESSAGE = "내 스탬프북 목록 조회에 성공했습니다.";

    private final GetMyStampbooksUseCase getMyStampbooksUseCase;

    public MyStampbookController(GetMyStampbooksUseCase getMyStampbooksUseCase) {
        this.getMyStampbooksUseCase = getMyStampbooksUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetMyStampbooksResponse>> getMyStampbooks(
        @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyStampbooksResponse.from(getMyStampbooksUseCase.findAll(userId))
        ).toResponseEntity();
    }
}
