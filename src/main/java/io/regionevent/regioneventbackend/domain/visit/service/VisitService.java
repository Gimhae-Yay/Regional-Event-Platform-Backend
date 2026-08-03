package io.regionevent.regioneventbackend.domain.visit.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Visit findByIdForCheckInResult(Long visitId) {
        return visitRepository.findByVisitId(visitId)
            .orElseThrow(() -> new IllegalStateException("check-in result visit does not exist"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Visit> findByReservationId(Long reservationId) {
        return visitRepository.findByReservationReservationId(reservationId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Visit create(Visit visit) {
        return visitRepository.saveAndFlush(visit);
    }
}
