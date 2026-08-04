package io.regionevent.regioneventbackend.domain.reservation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.GetQrExceptionResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.GetRegionAdminQrExceptionUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.QrExceptionDetailResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping({
    "/api/v1/region-admin/qr-exceptions",
    "/region-admin/qr-exceptions"
})
public class RegionAdminQrExceptionController {

    private static final Logger log = LoggerFactory.getLogger(RegionAdminQrExceptionController.class);
    private static final String SUCCESS_MESSAGE = "QR 예외 상세 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

    private final GetRegionAdminQrExceptionUseCase getRegionAdminQrExceptionUseCase;

    public RegionAdminQrExceptionController(
        GetRegionAdminQrExceptionUseCase getRegionAdminQrExceptionUseCase
    ) {
        this.getRegionAdminQrExceptionUseCase = getRegionAdminQrExceptionUseCase;
    }

    @GetMapping("/{exceptionId}")
    public ResponseEntity<ApiResponse<GetQrExceptionResponse>> get(
        Authentication authentication,
        @PathVariable String exceptionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long parsedExceptionId = null;
        try {
            parsedExceptionId = RegionAdminQrExceptionRequestIdParser.parseRequired(exceptionId);
        } catch (BusinessException exception) {
            logResult(requestId, null, parsedExceptionId, exception.getErrorCode().code());
            throw exception;
        }
        Long userId = (Long) authentication.getPrincipal();
        QrExceptionDetailResult result = getRegionAdminQrExceptionUseCase.get(userId, parsedExceptionId);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetQrExceptionResponse.from(result)
        ).toResponseEntity();
    }

    private void logResult(
        String requestId,
        Long regionId,
        Long exceptionId,
        String resultCode
    ) {
        log.info(
            "QR exception detail read. requestId={}, regionId={}, exceptionId={}, resultCode={}",
            requestId,
            regionId,
            exceptionId,
            resultCode
        );
    }
}
