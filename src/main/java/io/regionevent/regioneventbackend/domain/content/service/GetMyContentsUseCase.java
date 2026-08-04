package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.repository.MyContentProjection;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;

@Service
public class GetMyContentsUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;

    public GetMyContentsUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
    }

    @Transactional(readOnly = true)
    public MyContentListResult get(Long authenticatedUserId) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        List<MyContentListResult.Content> contents = contentService.findMyContents(
            operator.user().getUserId(),
            operator.region().getRegionId()
        ).stream()
            .map(GetMyContentsUseCase::toResult)
            .toList();
        return new MyContentListResult(contents);
    }

    private static MyContentListResult.Content toResult(MyContentProjection projection) {
        return new MyContentListResult.Content(
            projection.contentId(),
            projection.contentType(),
            projection.title(),
            projection.status(),
            projection.createdAt()
        );
    }
}
