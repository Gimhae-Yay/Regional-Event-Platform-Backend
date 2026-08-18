package io.regionevent.regioneventbackend.global.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/internal/performance/fixtures")
@ConditionalOnProperty(prefix = "performance.fixture", name = "enabled", havingValue = "true")
public class PerformanceFixtureResetController {

    private static final Logger log = LoggerFactory.getLogger(PerformanceFixtureResetController.class);
    private static final String FIXTURE_RESET_TOKEN_HEADER = "X-Performance-Fixture-Token";
    private static final String RESET_SUCCESS_MESSAGE = "성능 fixture 초기화에 성공했습니다.";

    private final PerformanceFixtureResetService performanceFixtureResetService;

    public PerformanceFixtureResetController(PerformanceFixtureResetService performanceFixtureResetService) {
        this.performanceFixtureResetService = performanceFixtureResetService;
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<PerformanceFixtureResetResult>> reset(
        @RequestHeader(value = FIXTURE_RESET_TOKEN_HEADER, required = false) String fixtureResetToken
    ) {
        PerformanceFixtureResetResult result = performanceFixtureResetService.reset(fixtureResetToken);
        log.info(
            "Performance fixture reset completed. requestId={}, fixtureVersion={}",
            RequestIdFilter.currentRequestId(),
            result.fixtureVersion()
        );
        return ApiResponse.success(HttpStatus.OK, RESET_SUCCESS_MESSAGE, result).toResponseEntity();
    }
}
