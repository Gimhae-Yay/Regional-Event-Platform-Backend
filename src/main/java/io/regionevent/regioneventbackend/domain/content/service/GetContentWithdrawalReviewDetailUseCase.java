package io.regionevent.regioneventbackend.domain.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetContentWithdrawalReviewDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(
        GetContentWithdrawalReviewDetailUseCase.class
    );

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentWithdrawalRequestService contentWithdrawalRequestService;

    public GetContentWithdrawalReviewDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentWithdrawalRequestService contentWithdrawalRequestService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentWithdrawalRequestService = contentWithdrawalRequestService;
    }

    @Transactional(readOnly = true)
    public ContentWithdrawalReviewDetailResult get(
        Long authenticatedUserId,
        Long withdrawalRequestId
    ) {
        Long regionId = null;
        Long identifiedWithdrawalRequestId = null;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(
                authenticatedUserId
            );
            ContentWithdrawalRequest request = contentWithdrawalRequestService
                .findReviewDetailById(withdrawalRequestId);
            identifiedWithdrawalRequestId = identifyRequest(request, withdrawalRequestId);
            validatePending(request);
            Content content = validateContentRegion(request);
            validateRegion(content, regionId);
            validatePublishedContent(content);
            ContentWithdrawalReviewDetailResult result = toResult(request, content);
            logResult(regionId, identifiedWithdrawalRequestId, "SUCCESS");
            return result;
        } catch (BusinessException exception) {
            logResult(
                regionId,
                identifiedWithdrawalRequestId,
                exception.getErrorCode().code()
            );
            throw exception;
        } catch (RuntimeException exception) {
            logResult(
                regionId,
                identifiedWithdrawalRequestId,
                ErrorCode.INTERNAL_SERVER_ERROR.code()
            );
            throw exception;
        }
    }

    private Long identifyRequest(
        ContentWithdrawalRequest request,
        Long withdrawalRequestId
    ) {
        Long identifiedRequestId = request.getContentWithdrawalRequestId();
        if (identifiedRequestId == null || !identifiedRequestId.equals(withdrawalRequestId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return identifiedRequestId;
    }

    private void validatePending(ContentWithdrawalRequest request) {
        if (request.getStatus() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (request.getStatus() != ContentWithdrawalRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private Content validateContentRegion(ContentWithdrawalRequest request) {
        Content content = request.getContent();
        if (content == null
            || content.getContentId() == null
            || content.getRegion() == null
            || content.getRegion().getRegionId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return content;
    }

    private void validateRegion(Content content, Long authorizedRegionId) {
        if (!content.getRegion().getRegionId().equals(authorizedRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePublishedContent(Content content) {
        if (content.getDeletedAt() != null || content.getStatus() != ContentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (content.getContentType() == null
            || content.getTitle() == null
            || content.getPublishAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ContentWithdrawalReviewDetailResult toResult(
        ContentWithdrawalRequest request,
        Content content
    ) {
        if (request.getRequestReason() == null
            || request.getRequestReason().isBlank()
            || request.getRequestedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new ContentWithdrawalReviewDetailResult(
            request.getContentWithdrawalRequestId(),
            request.getStatus(),
            new ContentWithdrawalReviewDetailResult.Content(
                content.getContentId(),
                content.getContentType(),
                content.getTitle(),
                content.getStatus(),
                content.getPublishAt()
            ),
            toRequester(request.getRequestedBy()),
            request.getRequestReason(),
            request.getRequestedAt()
        );
    }

    private ContentWithdrawalReviewDetailResult.Requester toRequester(AppUser requester) {
        if (requester == null) {
            return null;
        }
        if (requester.getUserId() == null
            || requester.getName() == null
            || requester.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new ContentWithdrawalReviewDetailResult.Requester(
            requester.getUserId(),
            requester.getName()
        );
    }

    private void logResult(Long regionId, Long withdrawalRequestId, String resultCode) {
        log.info(
            "Content withdrawal review detail queried. requestId={}, regionId={}, withdrawalRequestId={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            withdrawalRequestId,
            resultCode
        );
    }
}
