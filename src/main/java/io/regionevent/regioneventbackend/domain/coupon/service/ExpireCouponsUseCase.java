package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;

@Service
public class ExpireCouponsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireCouponsUseCase.class);

    private static final int BATCH_SIZE = 100;
    private static final String EXPIRATION_SCHEDULE_REASON = "EXPIRATION_SCHEDULE";

    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final TransactionTemplate batchTransactionTemplate;

    public ExpireCouponsUseCase(
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        batchTransactionTemplate = new TransactionTemplate(transactionManager);
        batchTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CouponExpirationResult execute() {
        UUID requestId = UUID.randomUUID();
        int processedBatchCount = 0;
        int candidateCouponCount = 0;
        int expiredCouponCount = 0;
        int skippedCouponCount = 0;
        int failedBatchCount = 0;

        while (true) {
            CouponExpirationBatchResult batchResult;
            try {
                batchResult = batchTransactionTemplate.execute(status -> expireBatch(requestId));
            } catch (RuntimeException exception) {
                failedBatchCount++;
                log.error(
                    "Coupon expiration batch failed. requestId={}, processedBatchCount={}",
                    requestId,
                    processedBatchCount,
                    exception
                );
                break;
            }

            if (batchResult.candidateCouponCount() == 0) {
                log.info("Coupon expiration batch skipped. requestId={}, candidateCouponCount=0", requestId);
                break;
            }

            processedBatchCount++;
            candidateCouponCount += batchResult.candidateCouponCount();
            expiredCouponCount += batchResult.expiredCouponCount();
            skippedCouponCount += batchResult.skippedCouponCount();
            log.info(
                "Coupon expiration batch finished. requestId={}, batchNumber={}, candidateCouponCount={}, "
                    + "expiredCouponCount={}, skippedCouponCount={}",
                requestId,
                processedBatchCount,
                batchResult.candidateCouponCount(),
                batchResult.expiredCouponCount(),
                batchResult.skippedCouponCount()
            );

            if (batchResult.candidateCouponCount() < BATCH_SIZE) {
                break;
            }
        }

        return new CouponExpirationResult(
            requestId,
            processedBatchCount,
            candidateCouponCount,
            expiredCouponCount,
            skippedCouponCount,
            failedBatchCount
        );
    }

    private CouponExpirationBatchResult expireBatch(UUID requestId) {
        List<Long> couponIds = couponService.findExpirationCandidateIds(BATCH_SIZE);
        if (couponIds.isEmpty()) {
            return CouponExpirationBatchResult.EMPTY;
        }

        Instant occurredAt = couponService.findCurrentTimestamp();
        int expiredCouponCount = 0;
        for (Long couponId : couponIds) {
            Coupon coupon = couponService.expireIfAvailableAndExpired(couponId);
            if (coupon == null) {
                continue;
            }
            couponStatusHistoryService.create(new CouponStatusHistory(
                coupon,
                CouponStatus.AVAILABLE,
                CouponStatus.EXPIRED,
                EXPIRATION_SCHEDULE_REASON,
                "SYSTEM",
                occurredAt
            ));
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                coupon.getCouponPolicy().getRegion(),
                AuditEventTargetType.COUPON,
                coupon.getCouponId(),
                CouponStatus.AVAILABLE.name(),
                CouponStatus.EXPIRED.name(),
                AuditEventResult.SUCCESS,
                EXPIRATION_SCHEDULE_REASON,
                null,
                occurredAt
            ));
            expiredCouponCount++;
        }
        return new CouponExpirationBatchResult(couponIds.size(), expiredCouponCount);
    }

    private record CouponExpirationBatchResult(
        int candidateCouponCount,
        int expiredCouponCount
    ) {

        private static final CouponExpirationBatchResult EMPTY = new CouponExpirationBatchResult(0, 0);

        private int skippedCouponCount() {
            return candidateCouponCount - expiredCouponCount;
        }
    }
}
