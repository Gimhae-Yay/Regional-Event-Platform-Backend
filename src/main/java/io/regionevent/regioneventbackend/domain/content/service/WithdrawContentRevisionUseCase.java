package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class WithdrawContentRevisionUseCase {

    private final ContentRevisionService contentRevisionService;
    private final ContentService contentService;
    private final RegionService regionService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public WithdrawContentRevisionUseCase(
        ContentRevisionService contentRevisionService,
        ContentService contentService,
        RegionService regionService,
        OperatorAuthorizationService operatorAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentRevisionService = contentRevisionService;
        this.contentService = contentService;
        this.regionService = regionService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public WithdrawContentRevisionResult withdraw(
        Long userId,
        Long revisionId,
        String reason,
        UUID requestId
    ) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Long contentId = contentRevisionService.findContentIdByRevisionId(revisionId);
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findApprovalTargetForUpdate(contentId);
        ContentRevision revision = contentRevisionService.findReviewTargetForUpdate(revisionId);
        validateOperatorScope(operator, region, content);

        if (revision.getStatus() == ContentRevisionStatus.EDIT_WITHDRAWN) {
            return WithdrawContentRevisionResult.from(revision);
        }
        if (revision.getStatus() != ContentRevisionStatus.EDIT_REQUESTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        Instant withdrawnAt = clock.instant();
        ContentRevision withdrawnRevision = contentRevisionService.withdraw(
            revision,
            operator.user(),
            withdrawnAt,
            reason
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentRevisionStatus.EDIT_REQUESTED.name(),
            ContentRevisionStatus.EDIT_WITHDRAWN.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(operator.roleAssignment()),
            withdrawnAt
        ));
        return WithdrawContentRevisionResult.from(withdrawnRevision);
    }

    private void validateOperatorScope(
        AuthorizedOperator operator,
        Region region,
        Content content
    ) {
        if (!content.isOwnedBy(operator.user().getUserId())
            || !operator.region().getRegionId().equals(region.getRegionId())
            || !content.isScopedTo(region.getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
