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

    public ContentLog recordApproved(Content content, AppUser actor, Instant approvedAt) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.APPROVED,
            null,
            approvedAt
        ));
    }

    public ContentLog recordPublished(Content content, Instant publishedAt) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            null,
            ContentLogStatus.PUBLISHED,
            null,
            publishedAt
        ));
    }

    public ContentLog recordRejected(
        Content content,
        AppUser actor,
        Instant rejectedAt,
        String reason
    ) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.REJECTED,
            reason,
            rejectedAt
        ));
    }

    public ContentLog findLatestApproved(Long contentId) {
        return contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            contentId,
            ContentLogStatus.APPROVED
        ).orElseThrow(() -> new IllegalStateException("approved content log must exist"));
    }

    public ContentLog findLatestRejected(Long contentId) {
        return contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            contentId,
            ContentLogStatus.REJECTED
        ).orElseThrow(() -> new IllegalStateException("rejected content log must exist"));
    }

    public ContentLog recordEnded(Content content, AppUser actor, Instant endedAt) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.ENDED,
            null,
            endedAt
        ));
    }

    public ContentLog recordDeleted(
        Content content,
        AppUser actor,
        Instant deletedAt,
        String reason
    ) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.DELETED,
            reason,
            deletedAt
        ));
    }

    public ContentLog recordSuspended(
        Content content,
        AppUser actor,
        Instant suspendedAt,
        String reason
    ) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.SUSPENDED,
            reason,
            suspendedAt
        ));
    }

    public ContentLog recordWithdrawn(
        Content content,
        AppUser actor,
        Instant withdrawnAt,
        String reason
    ) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            ContentLogStatus.WITHDRAWN,
            reason,
            withdrawnAt
        ));
    }

    public ContentLog findLatestEnded(Long contentId) {
        return contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            contentId,
            ContentLogStatus.ENDED
        )
            .orElseThrow(() -> new IllegalStateException("ended content log must exist"));
    }
}
