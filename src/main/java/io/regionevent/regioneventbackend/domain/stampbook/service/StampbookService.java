package io.regionevent.regioneventbackend.domain.stampbook.service;

import org.springframework.stereotype.Service;

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

    public void validateDraft(Stampbook stampbook) {
        if (stampbook.getStatus() != StampbookStatus.DRAFT) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
    }
}
