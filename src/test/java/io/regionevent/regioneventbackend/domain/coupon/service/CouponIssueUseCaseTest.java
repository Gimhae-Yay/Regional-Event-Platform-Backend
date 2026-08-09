package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;

class CouponIssueUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long POLICY_ID = 200L;
    private static final Long VISIT_ID = 300L;

    private final CouponIssueTransactionService couponIssueTransactionService = mock(
        CouponIssueTransactionService.class
    );
    private final CouponIssueDuplicateReadService couponIssueDuplicateReadService = mock(
        CouponIssueDuplicateReadService.class
    );
    private final CouponIssueUseCase useCase = new CouponIssueUseCase(
        couponIssueTransactionService,
        couponIssueDuplicateReadService
    );

    @Test
    void 발급_트랜잭션이_성공하면_그_결과를_반환한다() {
        CouponIssueUseCase.CouponIssueCommand command = command();
        CouponIssueResult result = result(false);
        when(couponIssueTransactionService.issue(USER_ID, POLICY_ID, command)).thenReturn(result);

        assertThat(useCase.issue(USER_ID, POLICY_ID, command)).isSameAs(result);
    }

    @Test
    void 동일_발급_식별자_충돌이면_기존_발급_결과를_반환한다() {
        CouponIssueUseCase.CouponIssueCommand command = command();
        CouponIssueResult duplicateResult = result(true);
        when(couponIssueTransactionService.issue(USER_ID, POLICY_ID, command))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        String identityHash = CouponIssuanceHasher.hashVisitIssue(POLICY_ID, USER_ID);
        when(couponIssueDuplicateReadService.find(identityHash)).thenReturn(Optional.of(duplicateResult));

        CouponIssueResult result = useCase.issue(USER_ID, POLICY_ID, command);

        assertThat(result).isSameAs(duplicateResult);
        verify(couponIssueDuplicateReadService).find(identityHash);
    }

    private CouponIssueUseCase.CouponIssueCommand command() {
        return new CouponIssueUseCase.CouponIssueCommand(CouponIssuanceType.VISIT, VISIT_ID);
    }

    private CouponIssueResult result(boolean duplicate) {
        return new CouponIssueResult(
            400L, POLICY_ID, 10L, 20L, "방문 할인", CouponIssuanceType.VISIT, CouponStatus.AVAILABLE,
            3_000L, 10_000L, Instant.parse("2026-08-09T00:00:00Z"),
            Instant.parse("2026-09-08T00:00:00Z"), duplicate
        );
    }
}
