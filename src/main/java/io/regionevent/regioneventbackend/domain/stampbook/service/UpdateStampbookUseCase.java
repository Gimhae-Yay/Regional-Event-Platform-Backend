package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateStampbookUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final CouponPolicyService couponPolicyService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public UpdateStampbookUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        CouponPolicyService couponPolicyService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.couponPolicyService = couponPolicyService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public UpdateStampbookResult update(
        Long userId,
        UpdateStampbookCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Stampbook stampbook = stampbookService.findForUpdate(command.stampbookId());
        validateRegionScope(operator, stampbook);
        stampbookService.validateDraft(stampbook);

        List<Long> existingContentIds = stampbookContentService.findContentIds(
            stampbook.getStampbookId()
        );
        contentService.findOwnedContentsForStampbookCreation(
            existingContentIds,
            operator.user().getUserId(),
            stampbook.getRegion().getRegionId()
        );

        List<Content> replacementContents = findReplacementContents(command, operator, stampbook);
        CouponPolicy replacementRewardCouponPolicy = findReplacementRewardCouponPolicy(command, stampbook);
        Instant updatedAt = clock.instant();

        if (replacementContents != null) {
            stampbookContentService.replace(stampbook, replacementContents);
        }
        if (command.title() != null) {
            stampbook.updateTitle(command.title());
        }
        if (replacementRewardCouponPolicy != null) {
            stampbook.updateRewardCouponPolicy(replacementRewardCouponPolicy);
        }
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            stampbook.getRegion(),
            AuditEventTargetType.STAMPBOOK,
            stampbook.getStampbookId(),
            StampbookStatus.DRAFT.name(),
            StampbookStatus.DRAFT.name(),
            AuditEventResult.SUCCESS,
            null,
            command.reason(),
            null,
            new AuditEventActor(operator.roleAssignment()),
            updatedAt
        ));

        int targetCount = replacementContents == null
            ? existingContentIds.size()
            : replacementContents.size();
        return UpdateStampbookResult.from(stampbook, targetCount, updatedAt);
    }

    private List<Content> findReplacementContents(
        UpdateStampbookCommand command,
        AuthorizedOperator operator,
        Stampbook stampbook
    ) {
        if (command.contentIds() == null) {
            return null;
        }
        return contentService.findOwnedContentsForStampbookCreation(
            command.contentIds(),
            operator.user().getUserId(),
            stampbook.getRegion().getRegionId()
        );
    }

    private CouponPolicy findReplacementRewardCouponPolicy(
        UpdateStampbookCommand command,
        Stampbook stampbook
    ) {
        if (command.rewardCouponPolicyId() == null) {
            return null;
        }
        return couponPolicyService.findStampbookRewardPolicy(
            command.rewardCouponPolicyId(),
            stampbook.getRegion().getRegionId()
        );
    }

    private void validateCommand(UpdateStampbookCommand command) {
        if (command == null
            || command.stampbookId() == null
            || command.stampbookId() <= 0
            || command.reason() == null
            || command.reason().isBlank()
            || command.reason().length() > 500
            || command.title() == null && command.contentIds() == null && command.rewardCouponPolicyId() == null
            || command.title() != null && (command.title().isBlank() || command.title().length() > 100)
            || command.rewardCouponPolicyId() != null && command.rewardCouponPolicyId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (command.contentIds() == null) {
            return;
        }
        if (command.contentIds().isEmpty()
            || command.contentIds().stream().anyMatch(contentId -> contentId == null || contentId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<Long> distinctContentIds = new HashSet<>(command.contentIds());
        if (distinctContentIds.size() != command.contentIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Stampbook stampbook
    ) {
        if (!operator.region().getRegionId().equals(stampbook.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public record UpdateStampbookCommand(
        Long stampbookId,
        String title,
        List<Long> contentIds,
        Long rewardCouponPolicyId,
        String reason
    ) {

        public UpdateStampbookCommand {
            title = title == null ? null : title.strip();
            contentIds = contentIds == null ? null : List.copyOf(contentIds);
            reason = reason == null ? null : reason.strip();
        }
    }
}
