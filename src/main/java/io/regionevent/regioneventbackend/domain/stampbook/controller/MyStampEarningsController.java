package io.regionevent.regioneventbackend.domain.stampbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.GetMyStampEarningsResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampEarningsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/stampbooks")
public class MyStampEarningsController {

    private static final String SUCCESS_MESSAGE = "내 스탬프 적립 이력 조회에 성공했습니다.";

    private final GetMyStampEarningsUseCase getMyStampEarningsUseCase;

    public MyStampEarningsController(GetMyStampEarningsUseCase getMyStampEarningsUseCase) {
        this.getMyStampEarningsUseCase = getMyStampEarningsUseCase;
    }

    @GetMapping("/{stampbookId}/earnings")
    public ResponseEntity<ApiResponse<GetMyStampEarningsResponse>> getMyStampEarnings(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyStampEarningsResponse.from(getMyStampEarningsUseCase.find(
                userId,
                MyStampbookRequestIdParser.parseRequired(stampbookId)
            ))
        ).toResponseEntity();
    }
}
