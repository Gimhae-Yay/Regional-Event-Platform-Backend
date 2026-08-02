package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
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

    public ContentLog recordApproved(Content content, AppUser actor, Instant approvedAt) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.APPROVED,
            null,
            approvedAt
        ));
    }

    public ContentLog findLatestApproved(Long contentId) {
        return contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            contentId,
            ContentLogStatus.APPROVED
        ).orElseThrow(() -> new IllegalStateException("approved content log must exist"));
    }
}
