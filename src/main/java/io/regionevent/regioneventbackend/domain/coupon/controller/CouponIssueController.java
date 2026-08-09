package io.regionevent.regioneventbackend.domain.coupon.controller;

import java.util.regex.Pattern;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.CouponIssueRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.CouponIssueResponse;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssueUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/coupon-policies")
public class CouponIssueController {

    private static final String SUCCESS_MESSAGE = "쿠폰 발급에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CouponIssueUseCase couponIssueUseCase;

    public CouponIssueController(CouponIssueUseCase couponIssueUseCase) {
        this.couponIssueUseCase = couponIssueUseCase;
    }

    @PostMapping("/{couponPolicyId}/coupons")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issue(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponPolicyId,
        @Valid @RequestBody CouponIssueRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CouponIssuanceType issueSourceType = toIssueSourceType(request.issueSourceType());
        if (issueSourceType == CouponIssuanceType.MISSION_REWARD) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            CouponIssueResponse.from(couponIssueUseCase.issue(
                userId,
                toPositiveId(couponPolicyId),
                new CouponIssueUseCase.CouponIssueCommand(issueSourceType, toPositiveId(request.sourceId())),
                UUID.fromString(requestId)
            ))
        ).toResponseEntity();
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

    private CouponIssuanceType toIssueSourceType(String value) {
        try {
            return CouponIssuanceType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
