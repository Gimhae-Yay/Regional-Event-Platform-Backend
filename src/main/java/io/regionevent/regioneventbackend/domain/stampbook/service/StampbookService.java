package io.regionevent.regioneventbackend.domain.stampbook.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;

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
}
