package io.regionevent.regioneventbackend.domain.coupon.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.EndCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.EndCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.service.EndCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.EndCouponPolicyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/coupon-policies")
public class EndCouponPolicyController {

    private static final String SUCCESS_MESSAGE = "쿠폰 정책 종료에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final EndCouponPolicyUseCase endCouponPolicyUseCase;

    public EndCouponPolicyController(EndCouponPolicyUseCase endCouponPolicyUseCase) {
        this.endCouponPolicyUseCase = endCouponPolicyUseCase;
    }

    @PostMapping("/{couponPolicyId}/end")
    public ResponseEntity<ApiResponse<EndCouponPolicyResponse>> end(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponPolicyId,
        @Valid @RequestBody EndCouponPolicyRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        EndCouponPolicyResult result = endCouponPolicyUseCase.end(
            userId,
            parsePositiveId(couponPolicyId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            EndCouponPolicyResponse.from(result)
        ).toResponseEntity();
    }

    private Long parsePositiveId(String value) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
