package io.regionevent.regioneventbackend.domain.region.controller;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.region.dto.CreateRegionRequest;
import io.regionevent.regioneventbackend.domain.region.dto.CreateRegionResponse;
import io.regionevent.regioneventbackend.domain.region.dto.GetPlatformAdminRegionsResponse;
import io.regionevent.regioneventbackend.domain.region.dto.UpdateRegionStatusRequest;
import io.regionevent.regioneventbackend.domain.region.dto.UpdateRegionStatusResponse;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionResult;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase.CreateRegionCommand;
import io.regionevent.regioneventbackend.domain.region.service.GetPlatformAdminRegionsUseCase;
import io.regionevent.regioneventbackend.domain.region.service.PlatformAdminRegionListInfo;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusResult;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusUseCase;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusUseCase.UpdateRegionStatusCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/regions")
public class PlatformAdminRegionController {

    private static final String SUCCESS_MESSAGE = "지역 생성에 성공했습니다.";
    private static final String LIST_SUCCESS_MESSAGE = "전체 지역 조회에 성공했습니다.";
    private static final String UPDATE_STATUS_SUCCESS_MESSAGE = "지역 공개 여부 요청을 처리했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CreateRegionUseCase createRegionUseCase;
    private final GetPlatformAdminRegionsUseCase getPlatformAdminRegionsUseCase;
    private final UpdateRegionStatusUseCase updateRegionStatusUseCase;

    public PlatformAdminRegionController(
        CreateRegionUseCase createRegionUseCase,
        GetPlatformAdminRegionsUseCase getPlatformAdminRegionsUseCase,
        UpdateRegionStatusUseCase updateRegionStatusUseCase
    ) {
        this.createRegionUseCase = createRegionUseCase;
        this.getPlatformAdminRegionsUseCase = getPlatformAdminRegionsUseCase;
        this.updateRegionStatusUseCase = updateRegionStatusUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetPlatformAdminRegionsResponse>> getRegions(
        @AuthenticationPrincipal Long actorUserId,
        @RequestParam(required = false) String isPublic
    ) {
        List<PlatformAdminRegionListInfo> regions = getPlatformAdminRegionsUseCase.get(
            actorUserId,
            toIsPublic(isPublic)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            LIST_SUCCESS_MESSAGE,
            GetPlatformAdminRegionsResponse.from(regions)
        ).toResponseEntity();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRegionResponse>> createRegion(
        @AuthenticationPrincipal Long actorUserId,
        @Valid @RequestBody CreateRegionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateRegionResult result = createRegionUseCase.create(
            actorUserId,
            new CreateRegionCommand(
                request.regionCode(),
                request.name(),
                request.reasonCode(),
                request.evidenceReference()
            ),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            CreateRegionResponse.from(result)
        ).toResponseEntity();
    }

    @PatchMapping("/{regionId}/status")
    public ResponseEntity<ApiResponse<UpdateRegionStatusResponse>> updateRegionStatus(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String regionId,
        @Valid @RequestBody UpdateRegionStatusRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        UpdateRegionStatusResult result = updateRegionStatusUseCase.update(
            actorUserId,
            toRegionId(regionId),
            new UpdateRegionStatusCommand(
                request.isPublic(),
                request.reasonCode(),
                request.evidenceReference()
            ),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            UPDATE_STATUS_SUCCESS_MESSAGE,
            UpdateRegionStatusResponse.from(result)
        ).toResponseEntity();
    }

    private Long toRegionId(String value) {
        Long regionId;
        try {
            regionId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        return regionId;
    }

    private Boolean toIsPublic(String value) {
        if (value == null) {
            return null;
        }
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new BusinessException(ErrorCode.INVALID_TYPE);
    }
}
