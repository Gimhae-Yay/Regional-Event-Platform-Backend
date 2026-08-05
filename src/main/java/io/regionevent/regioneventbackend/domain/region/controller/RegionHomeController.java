package io.regionevent.regioneventbackend.domain.region.controller;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.region.dto.GetRegionHomeResponse;
import io.regionevent.regioneventbackend.domain.region.service.GetRegionHomeUseCase;
import io.regionevent.regioneventbackend.domain.region.service.RegionHomeResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionHomeController {

    private static final Logger log = LoggerFactory.getLogger(RegionHomeController.class);
    private static final String SUCCESS_MESSAGE = "지역 홈 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");
    private static final int FAILURE_RESULT_COUNT = 0;

    private final GetRegionHomeUseCase getRegionHomeUseCase;

    public RegionHomeController(GetRegionHomeUseCase getRegionHomeUseCase) {
        this.getRegionHomeUseCase = getRegionHomeUseCase;
    }

    @GetMapping("/{regionId}/home")
    public ResponseEntity<ApiResponse<GetRegionHomeResponse>> getRegionHome(
        @PathVariable String regionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long parsedRegionId = null;
        try {
            parsedRegionId = toRegionId(regionId);
            RegionHomeResult result = getRegionHomeUseCase.get(parsedRegionId);
            logResult(
                requestId,
                parsedRegionId,
                result.ongoingContents().size(),
                result.upcomingContents().size(),
                SUCCESS_RESULT_CODE
            );
            return ApiResponse.success(
                HttpStatus.OK,
                SUCCESS_MESSAGE,
                GetRegionHomeResponse.from(result)
            ).toResponseEntity();
        } catch (BusinessException exception) {
            logResult(
                requestId,
                parsedRegionId,
                FAILURE_RESULT_COUNT,
                FAILURE_RESULT_COUNT,
                exception.getErrorCode().code()
            );
            throw exception;
        } catch (RuntimeException exception) {
            logResult(
                requestId,
                parsedRegionId,
                FAILURE_RESULT_COUNT,
                FAILURE_RESULT_COUNT,
                ErrorCode.INTERNAL_SERVER_ERROR.code()
            );
            throw exception;
        }
    }

    private Long toRegionId(String value) {
        Long regionId;
        try {
            regionId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return regionId;
    }

    private void logResult(
        String requestId,
        Long regionId,
        int ongoingCount,
        int upcomingCount,
        String resultCode
    ) {
        log.info(
            "Region home read. requestId={}, regionId={}, ongoingCount={}, upcomingCount={}, resultCode={}",
            requestId,
            regionId,
            ongoingCount,
            upcomingCount,
            resultCode
        );
    }
}
