package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ContentLogService {

    private final ContentLogRepository contentLogRepository;

    public ContentLogService(ContentLogRepository contentLogRepository) {
        this.contentLogRepository = contentLogRepository;
    }

    public void recordPending(Content content, AppUser actor, Instant submittedAt) {
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.PENDING,
            null,
            submittedAt
        ));
    }

    public boolean hasApprovedToPendingRevisionHistory(Content content) {
        List<ContentLog> recentLogs = contentLogRepository
            .findTop2ByContentContentIdAndContentDeletedAtIsNullAndContentStatusOrderByDateDescIdDesc(
                content.getContentId(),
                ContentStatus.PENDING
            );
        if (recentLogs.size() < 2) {
            return false;
        }
        return recentLogs.get(0).getStatus() == ContentLogStatus.PENDING
            && recentLogs.get(1).getStatus() == ContentLogStatus.APPROVED;
    }
}
