package io.regionevent.regioneventbackend.domain.mission.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.OperatorMissionSummaryResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetOperatorMissionsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.OperatorMissionListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.response.PageResponse;

@RestController
@RequestMapping("/api/v1/operator/missions")
public class OperatorMissionListController {

    private static final String SUCCESS_MESSAGE = "내 미션 목록 조회에 성공했습니다.";
    private static final String DEFAULT_PAGE_VALUE = "0";
    private static final String DEFAULT_SIZE_VALUE = "20";
    private static final int MIN_PAGE = 0;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final GetOperatorMissionsUseCase getOperatorMissionsUseCase;

    public OperatorMissionListController(GetOperatorMissionsUseCase getOperatorMissionsUseCase) {
        this.getOperatorMissionsUseCase = getOperatorMissionsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OperatorMissionSummaryResponse>>> getMissions(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = DEFAULT_PAGE_VALUE) String page,
        @RequestParam(defaultValue = DEFAULT_SIZE_VALUE) String size
    ) {
        OperatorMissionListResult result = getOperatorMissionsUseCase.get(
            userId,
            toStatus(status),
            toPage(page),
            toSize(size)
        );
        PageResponse<OperatorMissionSummaryResponse> response = new PageResponse<>(
            result.content().stream().map(OperatorMissionSummaryResponse::from).toList(),
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
