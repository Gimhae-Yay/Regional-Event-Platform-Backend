package io.regionevent.regioneventbackend.domain.region.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.region.dto.GetPublicRegionsResponse;
import io.regionevent.regioneventbackend.domain.region.service.GetPublicRegionsUseCase;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionStaticInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/regions")
public class PublicRegionController {

    private static final Logger log = LoggerFactory.getLogger(PublicRegionController.class);
    private static final String SUCCESS_MESSAGE = "공개 지역 목록 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";
    private static final int FAILURE_RESULT_COUNT = 0;

    private final GetPublicRegionsUseCase getPublicRegionsUseCase;

    public PublicRegionController(GetPublicRegionsUseCase getPublicRegionsUseCase) {
        this.getPublicRegionsUseCase = getPublicRegionsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetPublicRegionsResponse>> getPublicRegions(
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        try {
            List<PublicRegionStaticInfo> regions = getPublicRegionsUseCase.get();
            logResult(requestId, regions.size(), SUCCESS_RESULT_CODE);
            return ApiResponse.success(
                HttpStatus.OK,
                SUCCESS_MESSAGE,
                GetPublicRegionsResponse.from(regions)
            ).toResponseEntity();
        } catch (BusinessException exception) {
            logResult(requestId, FAILURE_RESULT_COUNT, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(requestId, FAILURE_RESULT_COUNT, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void logResult(
        String requestId,
        int resultCount,
        String resultCode
    ) {
        log.info(
            "Public region list read. requestId={}, resultCount={}, resultCode={}",
            requestId,
            resultCount,
            resultCode
        );
    }
}
