package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentSessionService {

    private final ContentSessionRepository contentSessionRepository;

    public ContentSessionService(ContentSessionRepository contentSessionRepository) {
        this.contentSessionRepository = contentSessionRepository;
    }

    public ContentSession findPublicSession(Long sessionId) {
        return contentSessionRepository.findBySessionIdAndContentStatus(
            sessionId,
            ContentStatus.PUBLISHED
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public void reserveCapacity(Long sessionId, int quantity) {
        int updatedCount = contentSessionRepository.decreaseRemainingCapacityIfReservable(
            sessionId,
            quantity,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.RESERVATION_HOLD_CONFLICT);
        }
    }
}
