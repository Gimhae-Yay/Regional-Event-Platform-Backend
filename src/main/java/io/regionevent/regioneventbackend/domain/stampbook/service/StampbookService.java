package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class StampbookService {

    private final StampbookRepository stampbookRepository;

    public StampbookService(StampbookRepository stampbookRepository) {
        this.stampbookRepository = stampbookRepository;
    }

    public Stampbook create(
        Region region,
        CouponPolicy rewardCouponPolicy
    ) {
        return stampbookRepository.saveAndFlush(new Stampbook(region, rewardCouponPolicy));
    }

    public Stampbook findForUpdate(Long stampbookId) {
        if (stampbookId == null || stampbookId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stampbookRepository.findByStampbookIdForUpdate(stampbookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Stampbook> findPublishedByTargetContentIdForUpdate(Long contentId) {
        if (contentId == null || contentId <= 0) {
            throw new IllegalArgumentException("contentId must be positive");
        }
        return stampbookRepository.findPublishedByTargetContentIdForUpdate(contentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Instant findCurrentDatabaseTime() {
        BigDecimal epochSeconds = stampbookRepository.findCurrentEpochSeconds();
        long seconds = epochSeconds.longValue();
        long nanos = epochSeconds.remainder(BigDecimal.ONE)
            .movePointRight(9)
            .longValue();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    public void validateDraft(Stampbook stampbook) {
        if (stampbook.getStatus() != StampbookStatus.DRAFT) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
    }

    public void validatePublished(Stampbook stampbook) {
        if (stampbook.getStatus() != StampbookStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
    }

    public boolean existsPublishedRewardCouponPolicy(Long couponPolicyId) {
        return stampbookRepository.existsByRewardCouponPolicyCouponPolicyIdAndStatus(
            couponPolicyId,
            StampbookStatus.PUBLISHED
        );
    }
}
