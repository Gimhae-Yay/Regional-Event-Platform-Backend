package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ResubmitContentRevisionUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final ContentRevisionService contentRevisionService;
    private final ContentLogService contentLogService;
    private final Clock clock;

    public ResubmitContentRevisionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        ContentRevisionService contentRevisionService,
        ContentLogService contentLogService,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.contentRevisionService = contentRevisionService;
        this.contentLogService = contentLogService;
        this.clock = clock;
    }

    @Transactional
    public ResubmitContentRevisionResult resubmit(Long authenticatedUserId, Long sourceRevisionId) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        Long contentId = contentRevisionService.findContentIdByRevisionId(sourceRevisionId);
        Content content = contentService.findRevisionResubmissionTargetForUpdate(contentId);
        ContentRevision sourceRevision = contentRevisionService.findResubmissionSourceForUpdate(sourceRevisionId);
        validateOperatorScope(operator, content);
        contentRevisionService.validateLatestRejectedRevision(sourceRevision);
        validateContentState(content, sourceRevision);

        Instant submittedAt = clock.instant();
        ContentRevision resubmittedRevision = contentRevisionService.resubmitRejectedRevision(
            content,
            sourceRevision,
            operator.user(),
            submittedAt
        );
        return ResubmitContentRevisionResult.from(sourceRevision, resubmittedRevision);
    }

    private void validateOperatorScope(AuthorizedOperator operator, Content content) {
        if (!content.isOwnedBy(operator.user().getUserId())
            || !content.isScopedTo(operator.region().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateContentState(Content content, ContentRevision sourceRevision) {
        if (content.getStatus() == ContentStatus.PUBLISHED && sourceRevision.getPublishAt() == null) {
            return;
        }
        if (content.getStatus() == ContentStatus.PENDING
            && sourceRevision.getPublishAt() != null
            && contentLogService.hasApprovedToPendingRevisionHistory(content)) {
            return;
        }
        throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
    }
}
