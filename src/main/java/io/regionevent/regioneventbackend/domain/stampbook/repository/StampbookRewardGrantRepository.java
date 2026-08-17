package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;

public interface StampbookRewardGrantRepository extends JpaRepository<StampbookRewardGrant, Long> {

    @EntityGraph(attributePaths = {
        "stampbookProgress",
        "stampbookProgress.stampbook",
        "stampbookProgress.user",
        "couponPolicy",
        "couponPolicy.region"
    })
    Optional<StampbookRewardGrant> findByStampbookRewardGrantId(Long stampbookRewardGrantId);

    Optional<StampbookRewardGrant> findByStampbookProgressStampbookProgressId(Long stampbookProgressId);
}
