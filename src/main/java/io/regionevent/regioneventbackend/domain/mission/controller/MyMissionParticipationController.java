package io.regionevent.regioneventbackend.domain.mission.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.MyMissionParticipationResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetMyMissionParticipationsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.MyMissionParticipationListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.response.PageResponse;

@RestController
@RequestMapping("/api/v1/me/mission-participations")
public class MyMissionParticipationController {

    private static final String SUCCESS_MESSAGE = "내 미션 참여 목록 조회에 성공했습니다.";

    private final GetMyMissionParticipationsUseCase getMyMissionParticipationsUseCase;

    public MyMissionParticipationController(GetMyMissionParticipationsUseCase getMyMissionParticipationsUseCase) {
        this.getMyMissionParticipationsUseCase = getMyMissionParticipationsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MyMissionParticipationResponse>>> getMyMissionParticipations(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") String page,
        @RequestParam(defaultValue = "20") String size
    ) {
        MyMissionParticipationListResult result = getMyMissionParticipationsUseCase.get(
            userId,
            toStatus(status),
            toPage(page),
            toSize(size)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            toResponse(result)
        ).toResponseEntity();
    }

    private PageResponse<MyMissionParticipationResponse> toResponse(MyMissionParticipationListResult result) {
        return new PageResponse<>(
            result.content().stream().map(MyMissionParticipationResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
    }

    private MissionParticipationStatus toStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MissionParticipationStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private int toPage(String value) {
        int page = toInt(value);
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return page;
    }

    private int toSize(String value) {
        int size = toInt(value);
        if (size < 1 || size > 100) {
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
