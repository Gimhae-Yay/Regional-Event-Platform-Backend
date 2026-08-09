package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookRewardGrantService;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;

class CouponIssueUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long POLICY_ID = 200L;
    private static final Long VISIT_ID = 300L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CouponIssueDuplicateReadService couponIssueDuplicateReadService = mock(
        CouponIssueDuplicateReadService.class
    );
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final CouponIssueUseCase useCase = new CouponIssueUseCase(
        couponIssueDuplicateReadService,
        mock(AppUserService.class),
        mock(CouponPolicyService.class),
        mock(VisitService.class),
        mock(StampbookRewardGrantService.class),
        mock(CouponIssuanceService.class),
        mock(CouponService.class),
        mock(CouponStatusHistoryService.class),
        mock(RecordAuditEventUseCase.class),
        mock(RecordFailedAuditEventUseCase.class),
        transactionManager,
        Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void 동일_발급_식별자_충돌이면_기존_발급_결과를_반환한다() {
        CouponIssueUseCase.CouponIssueCommand command = command();
        CouponIssueResult duplicateResult = result(true);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        String identityHash = CouponIssuanceHasher.hashVisitIssue(POLICY_ID, USER_ID);
        when(couponIssueDuplicateReadService.find(identityHash)).thenReturn(Optional.of(duplicateResult));

        CouponIssueResult result = useCase.issue(USER_ID, POLICY_ID, command, REQUEST_ID);

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
