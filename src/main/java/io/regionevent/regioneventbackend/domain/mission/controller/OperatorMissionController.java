package io.regionevent.regioneventbackend.domain.mission.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.CreateOperatorMissionRequest;
import io.regionevent.regioneventbackend.domain.mission.dto.CreateOperatorMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.dto.SubmitOperatorMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.dto.UpdateOperatorMissionRequest;
import io.regionevent.regioneventbackend.domain.mission.dto.UpdateOperatorMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionUseCase.CreateOperatorMissionCommand;
import io.regionevent.regioneventbackend.domain.mission.service.SubmitOperatorMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.SubmitOperatorMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.UpdateOperatorMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.UpdateOperatorMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.UpdateOperatorMissionUseCase.UpdateOperatorMissionCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/missions")
public class OperatorMissionController {

    private static final String CREATE_SUCCESS_MESSAGE = "미션 생성에 성공했습니다.";
    private static final String UPDATE_SUCCESS_MESSAGE = "미션 수정에 성공했습니다.";
    private static final String SUBMIT_SUCCESS_MESSAGE = "미션 검토 요청에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CreateOperatorMissionUseCase createOperatorMissionUseCase;
    private final UpdateOperatorMissionUseCase updateOperatorMissionUseCase;
    private final SubmitOperatorMissionUseCase submitOperatorMissionUseCase;

    public OperatorMissionController(
        CreateOperatorMissionUseCase createOperatorMissionUseCase,
        UpdateOperatorMissionUseCase updateOperatorMissionUseCase,
        SubmitOperatorMissionUseCase submitOperatorMissionUseCase
    ) {
        this.createOperatorMissionUseCase = createOperatorMissionUseCase;
        this.updateOperatorMissionUseCase = updateOperatorMissionUseCase;
        this.submitOperatorMissionUseCase = submitOperatorMissionUseCase;
    }

    @PatchMapping("/{missionId}")
    public ResponseEntity<ApiResponse<UpdateOperatorMissionResponse>> update(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId,
        @Valid @RequestBody UpdateOperatorMissionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        UpdateOperatorMissionResult result = updateOperatorMissionUseCase.update(
            userId,
            MissionIdParser.toMissionId(missionId),
            toCommand(request),
            UUID.fromString(requestId)
        );
        return ApiResponse
            .success(HttpStatus.OK, UPDATE_SUCCESS_MESSAGE, UpdateOperatorMissionResponse.from(result))
            .toResponseEntity();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOperatorMissionResponse>> create(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateOperatorMissionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateOperatorMissionResult result = createOperatorMissionUseCase.create(
            userId,
            toCommand(request),
            UUID.fromString(requestId)
        );
        return ApiResponse
            .success(HttpStatus.CREATED, CREATE_SUCCESS_MESSAGE, CreateOperatorMissionResponse.from(result))
            .toResponseEntity();
    }

    @PostMapping("/{missionId}/submit")
    public ResponseEntity<ApiResponse<SubmitOperatorMissionResponse>> submit(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        SubmitOperatorMissionResult result = submitOperatorMissionUseCase.submit(
            userId,
            MissionIdParser.toMissionId(missionId),
            UUID.fromString(requestId)
        );
        return ApiResponse
            .success(HttpStatus.OK, SUBMIT_SUCCESS_MESSAGE, SubmitOperatorMissionResponse.from(result))
            .toResponseEntity();
    }

    private CreateOperatorMissionCommand toCommand(CreateOperatorMissionRequest request) {
        return new CreateOperatorMissionCommand(
            request.conditionType(),
            request.requiredVisitCount(),
            request.targetContentIds() == null
                ? null
                : request.targetContentIds().stream()
                    .map(this::toPositiveId)
                    .toList(),
            toPositiveId(request.rewardCouponPolicyId()),
            request.endsAt()
        );
    }

    private UpdateOperatorMissionCommand toCommand(UpdateOperatorMissionRequest request) {
        return new UpdateOperatorMissionCommand(
            request.conditionType(),
            request.requiredVisitCount(),
            request.targetContentIds() == null
                ? null
                : request.targetContentIds().stream()
                    .map(this::toPositiveId)
                    .toList(),
            toPositiveId(request.rewardCouponPolicyId()),
            request.endsAt()
        );
    }

    private Long toPositiveId(String value) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
