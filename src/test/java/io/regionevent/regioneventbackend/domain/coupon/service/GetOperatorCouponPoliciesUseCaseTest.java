package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetOperatorCouponPoliciesUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long COUPON_POLICY_ID = 300L;

    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final GetOperatorCouponPoliciesUseCase useCase = new GetOperatorCouponPoliciesUseCase(
        operatorAuthorizationService,
        couponPolicyService
    );

    @Test
    void findAll_소유지역의_정책요약을_반환한다() {
        AuthorizedOperator operator = authorizedOperator();
        CouponPolicy newerPolicy = couponPolicy(301L);
        CouponPolicy olderPolicy = couponPolicy(COUPON_POLICY_ID);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findAllByContentOperatorUserIdAndContentRegionId(USER_ID, REGION_ID))
            .thenReturn(List.of(newerPolicy, olderPolicy));

        List<OperatorCouponPolicySummary> result = useCase.findAll(USER_ID);

        assertThat(result).extracting(OperatorCouponPolicySummary::couponPolicyId)
            .containsExactly(301L, COUPON_POLICY_ID);
        verify(couponPolicyService).findAllByContentOperatorUserIdAndContentRegionId(USER_ID, REGION_ID);
    }

    @Test
    void findAll_정책이_없으면_빈목록을_반환한다() {
        AuthorizedOperator operator = authorizedOperator();
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findAllByContentOperatorUserIdAndContentRegionId(USER_ID, REGION_ID))
            .thenReturn(List.of());

        assertThat(useCase.findAll(USER_ID)).isEmpty();
    }

    @Test
    void find_소유정책이면_상세와_발급현황을_반환한다() {
        CouponPolicy couponPolicy = couponPolicy(COUPON_POLICY_ID);
        when(couponPolicyService.find(COUPON_POLICY_ID)).thenReturn(couponPolicy);

        OperatorCouponPolicyDetail result = useCase.find(USER_ID, COUPON_POLICY_ID);

        assertThat(result.couponPolicyId()).isEqualTo(COUPON_POLICY_ID);
        assertThat(result.issuedCount()).isEqualTo(42L);
        Content content = couponPolicy.getContent();
        verify(operatorAuthorizationService).authorizeOwnedContent(
            USER_ID,
            content.getOperator(),
            content.getRegion()
        );
    }

    @Test
    void find_다른운영자의_정책이면_권한오류를_반환한다() {
        CouponPolicy couponPolicy = couponPolicy(COUPON_POLICY_ID);
        when(couponPolicyService.find(COUPON_POLICY_ID)).thenReturn(couponPolicy);
        when(operatorAuthorizationService.authorizeOwnedContent(any(), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.find(USER_ID, COUPON_POLICY_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    @Test
    void find_정책이_없으면_권한검증_전에_대상없음오류를_반환한다() {
        when(couponPolicyService.find(COUPON_POLICY_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.find(USER_ID, COUPON_POLICY_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(operatorAuthorizationService, never()).authorizeOwnedContent(any(), any(), any());
    }

    private AuthorizedOperator authorizedOperator() {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        return new AuthorizedOperator(user, region, assignment);
    }

    private CouponPolicy couponPolicy(Long couponPolicyId) {
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        Region region = mock(Region.class);
        when(couponPolicy.getCouponPolicyId()).thenReturn(couponPolicyId);
        when(couponPolicy.getContent()).thenReturn(content);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getName()).thenReturn("재방문 할인");
        when(couponPolicy.getDescription()).thenReturn("방문 혜택");
        when(couponPolicy.getStatus()).thenReturn(CouponPolicyStatus.PUBLISHED);
        when(couponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.VISIT);
        when(couponPolicy.getDiscountAmount()).thenReturn(3_000L);
        when(couponPolicy.getMinimumPaymentAmount()).thenReturn(10_000L);
        when(couponPolicy.getValidDays()).thenReturn(30);
        when(couponPolicy.getIssueStartsAt()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        when(couponPolicy.getIssueEndsAt()).thenReturn(Instant.parse("2026-08-31T00:00:00Z"));
        when(couponPolicy.getTotalIssueLimit()).thenReturn(1_000L);
        when(couponPolicy.getIssuedCount()).thenReturn(42L);
        when(couponPolicy.getPublishedAt()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        when(content.getContentId()).thenReturn(200L);
        when(content.getOperator()).thenReturn(operator);
        when(content.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return couponPolicy;
    }
}
