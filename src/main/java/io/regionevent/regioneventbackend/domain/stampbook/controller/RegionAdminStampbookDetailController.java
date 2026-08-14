package io.regionevent.regioneventbackend.domain.stampbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.RegionAdminStampbookDetailResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetRegionAdminStampbookDetailUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/stampbooks")
public class RegionAdminStampbookDetailController {

    private static final String SUCCESS_MESSAGE = "스탬프북 심사 상세 조회에 성공했습니다.";

    private final GetRegionAdminStampbookDetailUseCase getRegionAdminStampbookDetailUseCase;

    public RegionAdminStampbookDetailController(
        GetRegionAdminStampbookDetailUseCase getRegionAdminStampbookDetailUseCase
    ) {
        this.getRegionAdminStampbookDetailUseCase = getRegionAdminStampbookDetailUseCase;
    }

    @GetMapping("/{stampbookId}")
    public ResponseEntity<ApiResponse<RegionAdminStampbookDetailResponse>> getDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId
    ) {
        RegionAdminStampbookDetailResponse response = RegionAdminStampbookDetailResponse.from(
            getRegionAdminStampbookDetailUseCase.find(
                userId,
                RegionAdminStampbookIdParser.parseRequired(stampbookId)
            )
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
