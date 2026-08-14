package io.regionevent.regioneventbackend.domain.audit.service;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.StampbookReviewRequestAuditProjection;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class StampbookReviewRequestReadService {

    private static final int MAX_REQUEST_REASON_LENGTH = 500;

    private final AuditEventRepository auditEventRepository;

    public StampbookReviewRequestReadService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public StampbookReviewRequest findLatest(
        Long stampbookId,
        Long regionId
    ) {
        StampbookReviewRequestAuditProjection projection = auditEventRepository
            .findLatestStampbookReviewRequestAuditProjections(
                stampbookId,
                regionId,
                StampbookStatus.DRAFT.name(),
                StampbookStatus.PENDING_REVIEW.name(),
                PageRequest.of(0, 1)
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        return toStampbookReviewRequest(projection);
    }

    private StampbookReviewRequest toStampbookReviewRequest(
        StampbookReviewRequestAuditProjection projection
    ) {
        Instant requestedAt = projection.requestedAt();
        String requestReason = projection.requestReason();
        String normalizedRequestReason = requestReason == null ? null : requestReason.strip();
        if (requestedAt == null
            || normalizedRequestReason == null
            || normalizedRequestReason.isEmpty()
            || normalizedRequestReason.length() > MAX_REQUEST_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new StampbookReviewRequest(requestedAt, normalizedRequestReason);
    }

    public record StampbookReviewRequest(
        Instant requestedAt,
        String requestReason
    ) {
    }
}
