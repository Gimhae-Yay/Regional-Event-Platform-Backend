package io.regionevent.regioneventbackend.domain.operator.controller;

import static io.regionevent.regioneventbackend.domain.operator.controller.OperatorApplicationIdParser.toOperatorApplicationId;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.operator.dto.OperatorApplicationDetailResponse;
import io.regionevent.regioneventbackend.domain.operator.service.GetOperatorApplicationDetailUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/operator-requests")
public class OperatorApplicationDetailController {

    private static final String SUCCESS_MESSAGE = "운영자 승인 요청 상세 조회에 성공했습니다.";

    private final GetOperatorApplicationDetailUseCase getOperatorApplicationDetailUseCase;

    public OperatorApplicationDetailController(
        GetOperatorApplicationDetailUseCase getOperatorApplicationDetailUseCase
    ) {
        this.getOperatorApplicationDetailUseCase = getOperatorApplicationDetailUseCase;
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<OperatorApplicationDetailResponse>> getDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String requestId
    ) {
        OperatorApplicationDetailResponse response = getOperatorApplicationDetailUseCase.get(
            userId,
            toOperatorApplicationId(requestId)
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response));
    }
}
