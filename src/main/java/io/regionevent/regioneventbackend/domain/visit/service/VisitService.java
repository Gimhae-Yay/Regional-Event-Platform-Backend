package io.regionevent.regioneventbackend.domain.visit.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class VisitService {

    private final VisitRepository visitRepository;

    public VisitService(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    public Visit findById(Long visitId) {
        return visitRepository.findById(visitId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
