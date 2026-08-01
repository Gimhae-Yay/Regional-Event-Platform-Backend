package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

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
}
