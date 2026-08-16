package io.regionevent.regioneventbackend.domain.coupon.controller;

import java.util.regex.Pattern;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.dto.GetOperatorCouponPoliciesResponse;
import io.regionevent.regioneventbackend.domain.coupon.dto.GetOperatorCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.dto.PublishCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.PublishCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.dto.UpdateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.dto.UpdateCouponPolicyResponse;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.GetOperatorCouponPoliciesUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.UpdateCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.UpdateCouponPolicyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/coupon-policies")
public class CouponPolicyController {

    private static final String CREATE_SUCCESS_MESSAGE = "쿠폰 정책 생성에 성공했습니다.";
    private static final String LIST_SUCCESS_MESSAGE = "내 쿠폰 정책 목록 조회에 성공했습니다.";
    private static final String DETAIL_SUCCESS_MESSAGE = "내 쿠폰 정책 상세 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CreateCouponPolicyUseCase createCouponPolicyUseCase;
    private final GetOperatorCouponPoliciesUseCase getOperatorCouponPoliciesUseCase;
    private final PublishCouponPolicyUseCase publishCouponPolicyUseCase;
    private final UpdateCouponPolicyUseCase updateCouponPolicyUseCase;

    public CouponPolicyController(
        CreateCouponPolicyUseCase createCouponPolicyUseCase,
        GetOperatorCouponPoliciesUseCase getOperatorCouponPoliciesUseCase,
        PublishCouponPolicyUseCase publishCouponPolicyUseCase,
        UpdateCouponPolicyUseCase updateCouponPolicyUseCase
    ) {
        this.createCouponPolicyUseCase = createCouponPolicyUseCase;
        this.getOperatorCouponPoliciesUseCase = getOperatorCouponPoliciesUseCase;
        this.publishCouponPolicyUseCase = publishCouponPolicyUseCase;
        this.updateCouponPolicyUseCase = updateCouponPolicyUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateCouponPolicyResponse>> createCouponPolicy(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateCouponPolicyRequest request
    ) {
        CreateCouponPolicyResult result = createCouponPolicyUseCase.create(
            userId,
            toPositiveId(request.contentId(), ErrorCode.INVALID_INPUT),
            request
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            CREATE_SUCCESS_MESSAGE,
            CreateCouponPolicyResponse.from(result)
        ).toResponseEntity();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetOperatorCouponPoliciesResponse>> getCouponPolicies(
        @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            LIST_SUCCESS_MESSAGE,
            GetOperatorCouponPoliciesResponse.from(getOperatorCouponPoliciesUseCase.findAll(userId))
        ).toResponseEntity();
    }

    @GetMapping("/{couponPolicyId}")
    public ResponseEntity<ApiResponse<GetOperatorCouponPolicyResponse>> getCouponPolicy(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponPolicyId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            DETAIL_SUCCESS_MESSAGE,
            GetOperatorCouponPolicyResponse.from(getOperatorCouponPoliciesUseCase.find(
                userId,
                toPositiveId(couponPolicyId, ErrorCode.INVALID_INPUT)
            ))
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
            toPositiveId(couponPolicyId, ErrorCode.INVALID_INPUT),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            "쿠폰 정책 공개에 성공했습니다.",
            PublishCouponPolicyResponse.from(result)
        ).toResponseEntity();
    }

    @PatchMapping("/{couponPolicyId}")
    public ResponseEntity<ApiResponse<UpdateCouponPolicyResponse>> updateCouponPolicy(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponPolicyId,
        @RequestBody UpdateCouponPolicyRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        UpdateCouponPolicyResult result = updateCouponPolicyUseCase.update(
            userId,
            toPositiveId(couponPolicyId, ErrorCode.INVALID_TYPE),
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            "쿠폰 정책 수정에 성공했습니다.",
            UpdateCouponPolicyResponse.from(result)
        ).toResponseEntity();
    }

    private Long toPositiveId(
        String value,
        ErrorCode errorCode
    ) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(errorCode);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode, exception);
        }
    }
}
