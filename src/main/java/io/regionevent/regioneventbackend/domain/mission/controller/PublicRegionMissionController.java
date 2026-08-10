package io.regionevent.regioneventbackend.domain.mission.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.GetPublicRegionMissionsResponse;
import io.regionevent.regioneventbackend.domain.mission.service.GetPublicRegionMissionsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.PublicRegionMissionListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/regions")
public class PublicRegionMissionController {

    private static final String SUCCESS_MESSAGE = "공개 미션 목록 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");
    private static final String DEFAULT_PAGE_VALUE = "0";
    private static final String DEFAULT_SIZE_VALUE = "20";
    private static final int MIN_PAGE = 0;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final GetPublicRegionMissionsUseCase getPublicRegionMissionsUseCase;

    public PublicRegionMissionController(GetPublicRegionMissionsUseCase getPublicRegionMissionsUseCase) {
        this.getPublicRegionMissionsUseCase = getPublicRegionMissionsUseCase;
    }

    @GetMapping("/{regionId}/missions")
    public ResponseEntity<ApiResponse<GetPublicRegionMissionsResponse>> getPublicRegionMissions(
        @AuthenticationPrincipal Long userId,
        @PathVariable String regionId,
        @RequestParam(defaultValue = DEFAULT_PAGE_VALUE) String page,
        @RequestParam(defaultValue = DEFAULT_SIZE_VALUE) String size
    ) {
        PublicRegionMissionListResult result = getPublicRegionMissionsUseCase.get(
            toRegionId(regionId),
            userId,
            toPage(page),
            toSize(size)
        );
        return ApiResponse
            .success(HttpStatus.OK, SUCCESS_MESSAGE, GetPublicRegionMissionsResponse.from(result))
            .toResponseEntity();
    }

    private Long toRegionId(String value) {
        Long regionId = toLong(value);
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return regionId;
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

    private Long toLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }

    private int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
