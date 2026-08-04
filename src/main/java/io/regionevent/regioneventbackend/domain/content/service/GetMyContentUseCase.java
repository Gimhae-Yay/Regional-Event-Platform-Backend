package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetMyContentUseCase {

    private final ContentService contentService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;
    private final ContentLogRepository contentLogRepository;

    public GetMyContentUseCase(
        ContentService contentService,
        OperatorAuthorizationService operatorAuthorizationService,
        RepresentativeImageViewUrlService representativeImageViewUrlService,
        ContentLogRepository contentLogRepository
    ) {
        this.contentService = contentService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
        this.contentLogRepository = contentLogRepository;
    }

    @Transactional(readOnly = true)
    public MyContentDetailResult get(Long authenticatedUserId, Long contentId) {
        Content content = contentService.findMyContentDetail(contentId);
        operatorAuthorizationService.authorizeOwnedContent(
            authenticatedUserId,
            content.getOperator(),
            content.getRegion()
        );

        RepresentativeImageViewUrl representativeImageViewUrl = createRepresentativeImageViewUrl(content);
        return toResult(
            content,
            representativeImageViewUrl,
            findRejectionReason(content)
        );
    }

    private RepresentativeImageViewUrl createRepresentativeImageViewUrl(Content content) {
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        if (representativeImageObject == null
            || content.getRepresentativeImageAssignedAt() == null
            || !representativeImageObject.isScopedTo(content.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return representativeImageViewUrlService.createViewUrl(representativeImageObject);
    }

    private String findRejectionReason(Content content) {
        if (content.getStatus() != ContentStatus.REJECTED) {
            return null;
        }
        return contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            content.getContentId(),
            ContentLogStatus.REJECTED
        )
            .map(ContentLog::getReason)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private MyContentDetailResult toResult(
        Content content,
        RepresentativeImageViewUrl representativeImageViewUrl,
        String rejectionReason
    ) {
        return new MyContentDetailResult(
            content.getContentId(),
            content.getContentType(),
            content.getStatus(),
            content.getTitle(),
            content.getDescription(),
            representativeImageViewUrl.url(),
            representativeImageViewUrl.expiresAt(),
            content.getLocationText(),
            content.getOperatingHoursText(),
            content.getContactText(),
            content.getPrecautions(),
            content.getAgeRequirement(),
            content.getMaterials(),
            content.getCancellationPolicyText(),
            content.getPublishAt(),
            rejectionReason,
            content.getCreatedAt(),
            content.getUpdatedAt()
        );
    }
}
