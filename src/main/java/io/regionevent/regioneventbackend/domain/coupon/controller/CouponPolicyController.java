package io.regionevent.regioneventbackend.domain.coupon.controller;

import java.util.regex.Pattern;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.dto.PublishCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.PublishCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/coupon-policies")
public class CouponPolicyController {

    private static final String SUCCESS_MESSAGE = "쿠폰 정책 생성에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CreateCouponPolicyUseCase createCouponPolicyUseCase;
    private final PublishCouponPolicyUseCase publishCouponPolicyUseCase;

    public CouponPolicyController(
        CreateCouponPolicyUseCase createCouponPolicyUseCase,
        PublishCouponPolicyUseCase publishCouponPolicyUseCase
    ) {
        this.createCouponPolicyUseCase = createCouponPolicyUseCase;
        this.publishCouponPolicyUseCase = publishCouponPolicyUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateCouponPolicyResponse>> createCouponPolicy(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateCouponPolicyRequest request
    ) {
        CreateCouponPolicyResult result = createCouponPolicyUseCase.create(
            userId,
            toContentId(request.contentId()),
            request
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            CreateCouponPolicyResponse.from(result)
        ).toResponseEntity();
    }

    @PostMapping("/{couponPolicyId}/publish")
    public ResponseEntity<ApiResponse<PublishCouponPolicyResponse>> publishCouponPolicy(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponPolicyId,
        @Valid @RequestBody PublishCouponPolicyRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        PublishCouponPolicyResult result = publishCouponPolicyUseCase.publish(
            userId,
            toCouponPolicyId(couponPolicyId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            "쿠폰 정책 공개에 성공했습니다.",
            PublishCouponPolicyResponse.from(result)
        ).toResponseEntity();
    }

    private Long toContentId(String value) {
        return toPositiveId(value);
    }

    private Long toCouponPolicyId(String value) {
        return toPositiveId(value);
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
