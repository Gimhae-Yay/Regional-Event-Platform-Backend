package io.regionevent.regioneventbackend.domain.stampbook.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.GetPendingRegionAdminStampbooksResponse;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetPendingRegionAdminStampbooksUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.PendingRegionAdminStampbookResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/stampbooks")
public class RegionAdminStampbookListController {

    private static final String SUCCESS_MESSAGE = "스탬프북 심사 대기 목록 조회에 성공했습니다.";

    private final GetPendingRegionAdminStampbooksUseCase getPendingRegionAdminStampbooksUseCase;

    public RegionAdminStampbookListController(
        GetPendingRegionAdminStampbooksUseCase getPendingRegionAdminStampbooksUseCase
    ) {
        this.getPendingRegionAdminStampbooksUseCase = getPendingRegionAdminStampbooksUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetPendingRegionAdminStampbooksResponse>> getPendingStampbooks(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status
    ) {
        validateStatus(status);
        List<PendingRegionAdminStampbookResult> results = getPendingRegionAdminStampbooksUseCase.get(
            userId
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPendingRegionAdminStampbooksResponse.from(results)
        ).toResponseEntity();
    }

    private void validateStatus(String status) {
        if (!StampbookStatus.PENDING_REVIEW.name().equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
