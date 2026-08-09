package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService.CreateCouponPolicyCommand;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateCouponPolicyUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long CONTENT_ID = 200L;
    private static final Long REGION_ID = 10L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-08T00:00:00.123456Z");

    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final AppUserService appUserService = mock(AppUserService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final Clock clock = mock(Clock.class);
    private final CreateCouponPolicyUseCase useCase = new CreateCouponPolicyUseCase(
        operatorAuthorizationService,
        appUserService,
        contentService,
        couponPolicyService,
        clock
    );

    @Test
    void create_유효한_요청이면_DRAFT_쿠폰_정책을_생성한다() {
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        CouponPolicy couponPolicy = couponPolicy(content, region);
        AuthorizedOperator operator = authorizedOperator();
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(content);
        when(content.getRegion()).thenReturn(region);
        when(couponPolicyService.create(any(CreateCouponPolicyCommand.class))).thenReturn(couponPolicy);
        when(clock.instant()).thenReturn(CREATED_AT);

        CreateCouponPolicyResult result = useCase.create(USER_ID, CONTENT_ID, request());

        ArgumentCaptor<CreateCouponPolicyCommand> captor = ArgumentCaptor.forClass(
            CreateCouponPolicyCommand.class
        );
        verify(couponPolicyService).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("재방문 할인");
        assertThat(captor.getValue().issueSourceType()).isEqualTo(CouponIssuanceType.VISIT);
        assertThat(result.couponPolicyId()).isEqualTo(300L);
        assertThat(result.status()).isEqualTo(CouponPolicyStatus.DRAFT);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);

        InOrder lockOrder = inOrder(appUserService, operatorAuthorizationService, contentService);
        lockOrder.verify(appUserService).findActiveUserForUpdate(USER_ID);
        lockOrder.verify(operatorAuthorizationService).requireAuthorizedOperatorForUpdate(USER_ID);
        lockOrder.verify(contentService).findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID);
    }

    @Test
    void create_최소결제금액이_할인금액보다_작으면_정책충돌을_반환한다() {
        CreateCouponPolicyRequest invalidRequest = new CreateCouponPolicyRequest(
            "200",
            "재방문 할인",
            null,
            "VISIT",
            3_000L,
            2_000L,
            30,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T00:00:00Z"),
            null
        );

        assertThatThrownBy(() -> useCase.create(USER_ID, CONTENT_ID, invalidRequest))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_CONFLICT)
            );

        verify(appUserService, never()).findActiveUserForUpdate(any());
        verify(operatorAuthorizationService, never()).requireAuthorizedOperatorForUpdate(any());
        verify(couponPolicyService, never()).create(any());
    }

    @Test
    void create_지원하지_않는_발급경로면_입력오류를_반환한다() {
        CreateCouponPolicyRequest invalidRequest = new CreateCouponPolicyRequest(
            "200",
            "재방문 할인",
            null,
            "MANUAL",
            3_000L,
            10_000L,
            30,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T00:00:00Z"),
            null
        );

        assertThatThrownBy(() -> useCase.create(USER_ID, CONTENT_ID, invalidRequest))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        verify(appUserService, never()).findActiveUserForUpdate(any());
        verify(operatorAuthorizationService, never()).requireAuthorizedOperatorForUpdate(any());
        verify(couponPolicyService, never()).create(any());
    }

    @Test
    void create_활성_회원이_아니면_권한오류를_반환한다() {
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(USER_ID, CONTENT_ID, request()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(operatorAuthorizationService, never()).requireAuthorizedOperatorForUpdate(any());
        verify(couponPolicyService, never()).create(any());
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

    private CouponPolicy couponPolicy(Content content, Region region) {
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        when(couponPolicy.getCouponPolicyId()).thenReturn(300L);
        when(couponPolicy.getContent()).thenReturn(content);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getName()).thenReturn("재방문 할인");
        when(couponPolicy.getStatus()).thenReturn(CouponPolicyStatus.DRAFT);
        when(couponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.VISIT);
        when(couponPolicy.getDiscountAmount()).thenReturn(3_000L);
        when(couponPolicy.getMinimumPaymentAmount()).thenReturn(10_000L);
        when(couponPolicy.getValidDays()).thenReturn(30);
        when(couponPolicy.getIssueStartsAt()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        when(couponPolicy.getIssueEndsAt()).thenReturn(Instant.parse("2026-08-31T00:00:00Z"));
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return couponPolicy;
    }

    private CreateCouponPolicyRequest request() {
        return new CreateCouponPolicyRequest(
            "200",
            "  재방문 할인  ",
            "방문 혜택",
            "VISIT",
            3_000L,
            10_000L,
            30,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T00:00:00Z"),
            1_000L
        );
    }
}
