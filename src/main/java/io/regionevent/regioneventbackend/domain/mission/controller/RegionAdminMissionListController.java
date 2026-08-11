package io.regionevent.regioneventbackend.domain.mission.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionSummaryResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.RegionAdminMissionListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.response.PageResponse;

@RestController
@RequestMapping("/api/v1/region-admin/missions")
public class RegionAdminMissionListController {

    private static final String SUCCESS_MESSAGE = "지역 미션 목록 조회에 성공했습니다.";
    private static final String DEFAULT_PAGE_VALUE = "0";
    private static final String DEFAULT_SIZE_VALUE = "20";
    private static final int MIN_PAGE = 0;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final GetRegionAdminMissionsUseCase getRegionAdminMissionsUseCase;

    public RegionAdminMissionListController(GetRegionAdminMissionsUseCase getRegionAdminMissionsUseCase) {
        this.getRegionAdminMissionsUseCase = getRegionAdminMissionsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RegionAdminMissionSummaryResponse>>> getMissions(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = DEFAULT_PAGE_VALUE) String page,
        @RequestParam(defaultValue = DEFAULT_SIZE_VALUE) String size
    ) {
        RegionAdminMissionListResult result = getRegionAdminMissionsUseCase.get(
            userId,
            toStatus(status),
            toPage(page),
            toSize(size)
        );
        PageResponse<RegionAdminMissionSummaryResponse> response = new PageResponse<>(
            result.content().stream().map(RegionAdminMissionSummaryResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }

    private MissionStatus toStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MissionStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private int toPage(String value) {
        int page = toInt(value);
        if (page < MIN_PAGE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return page;
    }

    private int toSize(String value) {
        int size = toInt(value);
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return size;
    }

    private int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
