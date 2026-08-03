package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CapacityHoldService {

    private final CapacityHoldRepository capacityHoldRepository;

    public CapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
        this.capacityHoldRepository = capacityHoldRepository;
    }

    public CapacityHold createActiveHold(
        AppUser user,
        ContentSession contentSession,
        int quantity,
        Instant createdAt,
        Instant expiresAt
    ) {
        return capacityHoldRepository.save(new CapacityHold(
            contentSession.getRegion(),
            contentSession,
            user,
            quantity,
            CapacityHoldStatus.ACTIVE,
            expiresAt,
            null,
            null,
            null,
            createdAt
        ));
    }

    @Transactional(readOnly = true)
    public CapacityHold findOwnedHold(Long holdId, AppUser user) {
        CapacityHold capacityHold = capacityHoldRepository.findByHoldId(holdId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (capacityHold.getUser() == null
            || !capacityHold.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return capacityHold;
    }

    @Transactional(
        propagation = Propagation.MANDATORY,
        noRollbackFor = ReservationConfirmationConflictException.class
    )
    public CapacityHold consumeIfConfirmable(Long holdId, Long userId) {
        int updatedCount = capacityHoldRepository.consumeIfConfirmable(
            holdId,
            userId
        );
        if (updatedCount == 0) {
            throw new ReservationConfirmationConflictException();
        }
        return capacityHoldRepository.findByHoldId(holdId)
            .orElseThrow(() -> new IllegalStateException("consumed capacity hold does not exist"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int invalidateActiveHoldsForSession(
        ContentSession contentSession,
        String invalidationReason,
        Instant invalidatedAt
    ) {
        List<CapacityHold> activeHolds = capacityHoldRepository.findActiveBySessionIdForUpdate(
            contentSession.getSessionId()
        );
        int releasedQuantity = activeHolds.stream()
            .mapToInt(CapacityHold::getQuantity)
            .sum();
        activeHolds.forEach(capacityHold -> capacityHold.invalidate(invalidationReason, invalidatedAt));
        capacityHoldRepository.saveAllAndFlush(activeHolds);
        return releasedQuantity;
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredActiveHoldIds() {
        return capacityHoldRepository.findExpiredActiveHoldIds();
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveHoldIdsForStartedSessions() {
        return capacityHoldRepository.findActiveHoldIdsForStartedSessions();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireAndReleaseCapacityIfActive(Long holdId) {
        if (capacityHoldRepository.findExpiredActiveHoldIdForUpdate(holdId).isEmpty()) {
            return false;
        }
        capacityHoldRepository.expireAndReleaseCapacityIfActive(holdId);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TerminatedCapacityHold> expireOrInvalidateExpiredHoldIfActive(
        Long holdId,
        String invalidationReason
    ) {
        validateInvalidationReason(invalidationReason);
        if (capacityHoldRepository.findExpiredActiveHoldIdForUpdate(holdId).isEmpty()) {
            return Optional.empty();
        }
        capacityHoldRepository.expireOrInvalidateExpiredHoldAndReleaseCapacityIfActive(
            holdId,
            invalidationReason
        );
        CapacityHold capacityHold = capacityHoldRepository.findByHoldId(holdId)
            .orElseThrow(() -> new IllegalStateException("terminated capacity hold does not exist"));
        return Optional.of(TerminatedCapacityHold.from(capacityHold));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TerminatedCapacityHold> invalidateAndReleaseCapacityIfActive(
        Long holdId,
        String invalidationReason
    ) {
        validateInvalidationReason(invalidationReason);
        if (capacityHoldRepository.findActiveByHoldIdForUpdate(holdId).isEmpty()) {
            return Optional.empty();
        }
        capacityHoldRepository.invalidateAndReleaseCapacityIfActive(
            holdId,
            invalidationReason
        );
        CapacityHold capacityHold = capacityHoldRepository.findByHoldId(holdId)
            .orElseThrow(() -> new IllegalStateException("invalidated capacity hold does not exist"));
        return Optional.of(TerminatedCapacityHold.from(capacityHold));
    }

    private void validateInvalidationReason(String invalidationReason) {
        if (invalidationReason == null || invalidationReason.isBlank()) {
            throw new IllegalArgumentException("invalidationReason must not be null or blank");
        }
    }

    public record TerminatedCapacityHold(
        Long holdId,
        Region region,
        CapacityHoldStatus nextStatus,
        String reasonCode,
        Instant occurredAt
    ) {

        private static TerminatedCapacityHold from(CapacityHold capacityHold) {
            if (capacityHold.getStatus() != CapacityHoldStatus.EXPIRED
                && capacityHold.getStatus() != CapacityHoldStatus.INVALIDATED) {
                throw new IllegalStateException("capacity hold must be expired or invalidated");
            }
            if (capacityHold.getTerminalAt() == null) {
                throw new IllegalStateException("terminated capacity hold must have terminalAt");
            }
            return new TerminatedCapacityHold(
                capacityHold.getHoldId(),
                capacityHold.getRegion(),
                capacityHold.getStatus(),
                capacityHold.getInvalidationReason(),
                capacityHold.getTerminalAt()
            );
        }
    }
}
