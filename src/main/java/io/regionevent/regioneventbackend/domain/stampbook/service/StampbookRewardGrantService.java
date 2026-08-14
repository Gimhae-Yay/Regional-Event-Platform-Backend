package io.regionevent.regioneventbackend.domain.stampbook.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRewardGrantRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class StampbookRewardGrantService {

    private final StampbookRewardGrantRepository stampbookRewardGrantRepository;

    public StampbookRewardGrantService(StampbookRewardGrantRepository stampbookRewardGrantRepository) {
        this.stampbookRewardGrantRepository = stampbookRewardGrantRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StampbookRewardGrant create(StampbookRewardGrant rewardGrant) {
        return stampbookRewardGrantRepository.saveAndFlush(rewardGrant);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StampbookRewardGrant findForCouponIssue(Long stampbookRewardGrantId) {
        return stampbookRewardGrantRepository.findByStampbookRewardGrantId(stampbookRewardGrantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
