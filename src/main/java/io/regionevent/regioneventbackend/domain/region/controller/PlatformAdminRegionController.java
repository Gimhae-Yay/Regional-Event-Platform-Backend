package io.regionevent.regioneventbackend.domain.region.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.region.dto.CreateRegionRequest;
import io.regionevent.regioneventbackend.domain.region.dto.CreateRegionResponse;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionResult;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase.CreateRegionCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/regions")
public class PlatformAdminRegionController {

    private static final String SUCCESS_MESSAGE = "지역 생성에 성공했습니다.";

    private final CreateRegionUseCase createRegionUseCase;

    public PlatformAdminRegionController(CreateRegionUseCase createRegionUseCase) {
        this.createRegionUseCase = createRegionUseCase;
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
}
