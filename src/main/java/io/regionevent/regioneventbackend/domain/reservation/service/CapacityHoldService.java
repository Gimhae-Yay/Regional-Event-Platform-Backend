package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
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

    public CapacityHold findOwnedHold(Long holdId, AppUser actor) {
        CapacityHold capacityHold = capacityHoldRepository.findById(holdId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOwnership(capacityHold, actor);
        return capacityHold;
    }

    public CapacityHold findOwnedHoldIfPresent(Long holdId, AppUser actor) {
        return capacityHoldRepository.findById(holdId)
            .filter(capacityHold -> isOwnedBy(capacityHold, actor))
            .orElse(null);
    }

    public Instant consumeIfConfirmable(CapacityHold capacityHold, AppUser actor) {
        Instant confirmedAt = capacityHoldRepository.findCurrentTimestamp();
        int consumedCount = capacityHoldRepository.consumeIfConfirmable(
            capacityHold.getHoldId(),
            actor.getUserId(),
            CapacityHoldStatus.ACTIVE,
            CapacityHoldStatus.CONSUMED,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED,
            confirmedAt
        );
        if (consumedCount == 0) {
            return null;
        }
        capacityHold.consume(confirmedAt);
        return confirmedAt;
    }

    private static void validateOwnership(CapacityHold capacityHold, AppUser actor) {
        if (!isOwnedBy(capacityHold, actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static boolean isOwnedBy(CapacityHold capacityHold, AppUser actor) {
        return capacityHold.getUser() != null && actor.getUserId().equals(capacityHold.getUser().getUserId());
    }
}
