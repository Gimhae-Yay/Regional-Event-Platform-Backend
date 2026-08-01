package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
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

    @Transactional(propagation = Propagation.MANDATORY)
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
}
