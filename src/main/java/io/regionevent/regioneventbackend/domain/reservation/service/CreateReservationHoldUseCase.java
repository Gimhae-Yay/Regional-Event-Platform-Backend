package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateReservationHoldUseCase {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    private final AppUserService appUserService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;

    public CreateReservationHoldUseCase(
        AppUserService appUserService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService
    ) {
        this.appUserService = appUserService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
    }

    @Transactional
    public CreateReservationHoldResponse create(Long userId, CreateReservationHoldRequest request) {
        Long sessionId = toPositiveSessionId(request.sessionId());
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        Long contentId = contentSessionService.findPublicContentId(sessionId);
        if (!contentService.lockPublishedCapacityHoldTarget(contentId)) {
            throw new BusinessException(ErrorCode.RESERVATION_HOLD_CONFLICT);
        }
        ContentSession contentSession = contentSessionService.findForUpdate(sessionId);
        Instant createdAt = capacityHoldService.findCurrentDatabaseInstant();

        contentSessionService.reserveCapacity(sessionId, request.quantity());
        Instant expiresAt = calculateExpiresAt(createdAt, contentSession.getStartsAt());
        CapacityHold capacityHold = capacityHoldService.createActiveHold(
            user,
            contentSession,
            request.quantity(),
            createdAt,
            expiresAt
        );

        return new CreateReservationHoldResponse(
            capacityHold.getHoldId().toString(),
            sessionId.toString(),
            capacityHold.getQuantity(),
            capacityHold.getStatus().name(),
            capacityHold.getExpiresAt(),
            capacityHold.getCreatedAt()
        );
    }

    private Long toPositiveSessionId(String value) {
        try {
            Long sessionId = Long.valueOf(value);
            if (sessionId <= 0) {
                throw new NumberFormatException();
            }
            return sessionId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Instant calculateExpiresAt(Instant createdAt, Instant sessionStartsAt) {
        Instant durationExpiresAt = createdAt.plus(HOLD_DURATION);
        return durationExpiresAt.isBefore(sessionStartsAt) ? durationExpiresAt : sessionStartsAt;
    }
}
