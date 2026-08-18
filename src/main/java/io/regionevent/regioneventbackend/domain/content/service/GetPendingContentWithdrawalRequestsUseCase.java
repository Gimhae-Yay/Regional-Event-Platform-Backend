package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

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
public class GetPendingContentWithdrawalRequestsUseCase {

    private static final Logger log = LoggerFactory.getLogger(
        GetPendingContentWithdrawalRequestsUseCase.class
    );
    private static final String PENDING_STATUS = "PENDING";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentWithdrawalRequestService contentWithdrawalRequestService;

    public GetPendingContentWithdrawalRequestsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentWithdrawalRequestService contentWithdrawalRequestService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentWithdrawalRequestService = contentWithdrawalRequestService;
    }

    @Transactional(readOnly = true)
    public PendingContentWithdrawalRequestListResult get(
        Long authenticatedUserId,
        String status
    ) {
        Long regionId = null;
        int resultCount = 0;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(
                authenticatedUserId
            );
            validateStatus(status);
            List<ContentWithdrawalRequest> requests = contentWithdrawalRequestService
                .findPendingPublishedByRegionId(regionId);
            validateRequests(requests, regionId);
            List<PendingContentWithdrawalRequestListResult.WithdrawalRequest> results = requests
                .stream()
                .map(this::toResult)
                .toList();
            resultCount = results.size();
            logResult(regionId, resultCount, "SUCCESS");
            return new PendingContentWithdrawalRequestListResult(results);
        } catch (BusinessException exception) {
            logResult(regionId, resultCount, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, resultCount, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void validateStatus(String status) {
        if (!PENDING_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRequests(List<ContentWithdrawalRequest> requests, Long regionId) {
        if (requests == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        requests.forEach(request -> validateRequest(request, regionId));
    }

    private void validateRequest(ContentWithdrawalRequest request, Long regionId) {
        if (request == null
            || !isPositive(request.getContentWithdrawalRequestId())
            || request.getStatus() != ContentWithdrawalRequestStatus.PENDING
            || request.getRequestedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        Content content = request.getContent();
        if (content == null
            || !isPositive(content.getContentId())
            || content.getContentType() == null
            || content.getTitle() == null
            || content.getStatus() != ContentStatus.PUBLISHED
            || content.getDeletedAt() != null
            || !content.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        AppUser requester = request.getRequestedBy();
        if (requester != null
            && (!isPositive(requester.getUserId()) || requester.getName() == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isPositive(Long identifier) {
        return identifier != null && identifier > 0;
    }

    private PendingContentWithdrawalRequestListResult.WithdrawalRequest toResult(
        ContentWithdrawalRequest request
    ) {
        Content content = request.getContent();
        return new PendingContentWithdrawalRequestListResult.WithdrawalRequest(
            request.getContentWithdrawalRequestId(),
            content.getContentId(),
            content.getContentType(),
            content.getTitle(),
            content.getStatus(),
            toRequester(request.getRequestedBy()),
            request.getRequestedAt()
        );
    }

    private PendingContentWithdrawalRequestListResult.Requester toRequester(AppUser requester) {
        if (requester == null) {
            return null;
        }
        return new PendingContentWithdrawalRequestListResult.Requester(
            requester.getUserId(),
            requester.getName()
        );
    }

    private void logResult(Long regionId, int resultCount, String resultCode) {
        log.info(
            "Pending content withdrawal requests queried. "
                + "requestId={}, regionId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            resultCount,
            resultCode
        );
    }
}
