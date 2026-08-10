package io.regionevent.regioneventbackend.domain.stampbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.GetMyStampbookDetailResponse;
import io.regionevent.regioneventbackend.domain.stampbook.dto.GetMyStampbooksResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampbookDetailUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampbooksUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/stampbooks")
public class MyStampbookController {

    private static final String DETAIL_SUCCESS_MESSAGE = "내 스탬프북 상세 조회에 성공했습니다.";
    private static final String SUCCESS_MESSAGE = "내 스탬프북 목록 조회에 성공했습니다.";

    private final GetMyStampbooksUseCase getMyStampbooksUseCase;
    private final GetMyStampbookDetailUseCase getMyStampbookDetailUseCase;

    public MyStampbookController(
        GetMyStampbooksUseCase getMyStampbooksUseCase,
        GetMyStampbookDetailUseCase getMyStampbookDetailUseCase
    ) {
        this.getMyStampbooksUseCase = getMyStampbooksUseCase;
        this.getMyStampbookDetailUseCase = getMyStampbookDetailUseCase;
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

    @GetMapping("/{stampbookId}")
    public ResponseEntity<ApiResponse<GetMyStampbookDetailResponse>> getMyStampbookDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            DETAIL_SUCCESS_MESSAGE,
            GetMyStampbookDetailResponse.from(getMyStampbookDetailUseCase.find(
                userId,
                MyStampbookDetailRequestIdParser.parseRequired(stampbookId)
            ))
        ).toResponseEntity();
    }
}
