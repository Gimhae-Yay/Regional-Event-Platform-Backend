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

    @Transactional(propagation = Propagation.MANDATORY)
    public Visit findForCouponIssue(Long visitId) {
        return visitRepository.findByVisitId(visitId)
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

    @Transactional(readOnly = true)
    public Optional<Long> findReservationIdByVisitId(Long visitId) {
        if (visitId == null || visitId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return visitRepository.findReservationIdByVisitId(visitId);
    }

    @Transactional(readOnly = true)
    public Optional<Visit> findMissionProgressSource(Long visitId) {
        return findValidMissionProgressSource(visitId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Visit> findMissionProgressSourceInCurrentTransaction(Long visitId) {
        return findValidMissionProgressSource(visitId);
    }

    @Transactional(readOnly = true)
    public Optional<Visit> findStampbookProgressSource(Long visitId) {
        return findValidStampbookProgressSource(visitId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Visit> findStampbookProgressSourceInCurrentTransaction(Long visitId) {
        if (visitId == null || visitId <= 0) {
            return Optional.empty();
        }
        return visitRepository.findStampbookProgressSourceByVisitIdForUpdate(visitId)
            .filter(this::isValidStampbookProgressSource);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Visit create(Visit visit) {
        return visitRepository.saveAndFlush(visit);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unlinkAuthorByUserId(Long userId) {
        visitRepository.unlinkAuthorByUserId(userId);
    }

    private Optional<Visit> findValidMissionProgressSource(Long visitId) {
        if (visitId == null || visitId <= 0) {
            return Optional.empty();
        }
        return visitRepository.findMissionProgressSourceByVisitId(visitId)
            .filter(visit -> visit.getUser() != null && visit.getAuthorUnlinkedAt() == null);
    }

    private Optional<Visit> findValidStampbookProgressSource(Long visitId) {
        if (visitId == null || visitId <= 0) {
            return Optional.empty();
        }
        return visitRepository.findStampbookProgressSourceByVisitId(visitId)
            .filter(this::isValidStampbookProgressSource);
    }

    private boolean isValidStampbookProgressSource(Visit visit) {
        return visit.getUser() != null && visit.getAuthorUnlinkedAt() == null;
    }
}
