package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
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

    @Transactional(readOnly = true)
    public List<ContentSession> findCurrentSessionsByContentId(Long contentId) {
        return contentSessionRepository.findByContentContentIdOrderByStartsAtAscSessionIdAsc(contentId);
    }

    public List<ContentSession> createPendingSessions(
        Content content,
        Region region,
        List<CreateContentSessionCommand> commands
    ) {
        List<ContentSession> sessions = commands.stream()
            .map(command -> new ContentSession(
                content,
                region,
                command.startsAt(),
                command.endsAt(),
                command.checkinOpenAt(),
                command.checkinCloseAt(),
                command.capacity()
            ))
            .toList();
        return contentSessionRepository.saveAllAndFlush(sessions);
    }

    @Transactional(readOnly = true)
    public PublicSessionReservationInfo findPublicScheduledReservationInfo(Long sessionId) {
        return contentSessionRepository.findPublicScheduledReservationInfo(
            sessionId,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        ).map(PublicSessionReservationInfo::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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

    public record CreateContentSessionCommand(
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity
    ) {
    }
}
