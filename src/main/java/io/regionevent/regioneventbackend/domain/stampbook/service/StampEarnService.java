package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampEarnRepository;

@Service
public class StampEarnService {

    private final StampEarnRepository stampEarnRepository;

    public StampEarnService(StampEarnRepository stampEarnRepository) {
        this.stampEarnRepository = stampEarnRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean existsByVisitId(
        Long stampbookProgressId,
        Long visitId
    ) {
        return stampEarnRepository.existsByStampbookProgressStampbookProgressIdAndVisitVisitId(
            stampbookProgressId,
            visitId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean existsByContentId(
        Long stampbookProgressId,
        Long contentId
    ) {
        return stampEarnRepository.existsByStampbookProgressStampbookProgressIdAndContentContentId(
            stampbookProgressId,
            contentId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StampEarn create(StampEarn stampEarn) {
        return stampEarnRepository.saveAndFlush(stampEarn);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<StampEarn> findAllByProgressIdForUpdate(Long stampbookProgressId) {
        return List.copyOf(stampEarnRepository.findAllByStampbookProgressIdForUpdate(
            stampbookProgressId
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countByProgressId(Long stampbookProgressId) {
        return stampEarnRepository.countByStampbookProgressStampbookProgressId(stampbookProgressId);
    }
}
