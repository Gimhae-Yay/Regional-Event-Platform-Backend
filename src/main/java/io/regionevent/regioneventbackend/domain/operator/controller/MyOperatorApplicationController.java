package io.regionevent.regioneventbackend.domain.operator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.operator.dto.MyOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.service.GetMyOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/operator-application")
public class MyOperatorApplicationController {

    private static final String SUCCESS_MESSAGE = "본인 운영자 신청 현황 조회에 성공했습니다.";

    private final GetMyOperatorApplicationUseCase getMyOperatorApplicationUseCase;

    public MyOperatorApplicationController(GetMyOperatorApplicationUseCase getMyOperatorApplicationUseCase) {
        this.getMyOperatorApplicationUseCase = getMyOperatorApplicationUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyOperatorApplicationResponse>> get(
        @AuthenticationPrincipal Long userId
    ) {
        MyOperatorApplicationResponse response = getMyOperatorApplicationUseCase.get(userId);
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
