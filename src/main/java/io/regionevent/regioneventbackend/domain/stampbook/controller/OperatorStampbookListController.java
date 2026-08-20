package io.regionevent.regioneventbackend.domain.stampbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.GetOperatorStampbooksResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetOperatorStampbooksUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/stampbooks")
public class OperatorStampbookListController {

    private static final String SUCCESS_MESSAGE = "운영자 스탬프북 목록 조회에 성공했습니다.";

    private final GetOperatorStampbooksUseCase getOperatorStampbooksUseCase;

    public OperatorStampbookListController(GetOperatorStampbooksUseCase getOperatorStampbooksUseCase) {
        this.getOperatorStampbooksUseCase = getOperatorStampbooksUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetOperatorStampbooksResponse>> findStampbooks(
        @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetOperatorStampbooksResponse.from(getOperatorStampbooksUseCase.findAll(userId))
        ).toResponseEntity();
    }
}
