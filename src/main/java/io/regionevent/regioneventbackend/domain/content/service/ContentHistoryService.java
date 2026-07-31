package io.regionevent.regioneventbackend.domain.content.service;

import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ContentHistoryService {

    private static final String WITHDRAWN_USER_DISPLAY_NAME = "탈퇴한 사용자";
    private static final EnumSet<ContentLogStatus> SYSTEM_PROCESSED_STATUSES = EnumSet.of(
        ContentLogStatus.PUBLISHED,
        ContentLogStatus.ENDED
    );

    private final ContentLogRepository contentLogRepository;

    public ContentHistoryService(ContentLogRepository contentLogRepository) {
        this.contentLogRepository = contentLogRepository;
    }

    @Transactional(readOnly = true)
    public ContentHistoryResult findAllByContentId(Long contentId) {
        List<ContentHistoryResult.History> histories = contentLogRepository
            .findByContentContentIdOrderByDateAscIdAsc(contentId)
            .stream()
            .map(this::toHistory)
            .toList();
        return new ContentHistoryResult(contentId, histories);
    }

    private ContentHistoryResult.History toHistory(ContentLog contentLog) {
        return new ContentHistoryResult.History(
            contentLog.getStatus(),
            contentLog.getReason(),
            contentLog.getDate(),
            toActor(contentLog)
        );
    }

    private ContentHistoryResult.Actor toActor(ContentLog contentLog) {
        AppUser actor = contentLog.getActor();
        if (actor != null) {
            return new ContentHistoryResult.Actor(actor.getUserId(), actor.getName());
        }
        if (SYSTEM_PROCESSED_STATUSES.contains(contentLog.getStatus())) {
            return null;
        }
        return new ContentHistoryResult.Actor(null, WITHDRAWN_USER_DISPLAY_NAME);
    }
}
