package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class GetContentHistoryUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long CONTENT_ID = 10L;
    private static final Long REGION_ID = 20L;

    private final ContentHistoryTargetService contentHistoryTargetService =
        mock(ContentHistoryTargetService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentHistoryService contentHistoryService = mock(ContentHistoryService.class);
    private final GetContentHistoryUseCase getContentHistoryUseCase = new GetContentHistoryUseCase(
        contentHistoryTargetService,
        regionAdminAuthorizationService,
        contentHistoryService
    );

    @Test
    void get_authorizesTargetRegionBeforeReturningHistory() {
        ContentHistoryTarget target = new ContentHistoryTarget(CONTENT_ID, REGION_ID);
        ContentHistoryResult expected = new ContentHistoryResult(CONTENT_ID, List.of());
        when(contentHistoryTargetService.findById(CONTENT_ID)).thenReturn(target);
        when(contentHistoryService.findAllByContentId(CONTENT_ID)).thenReturn(expected);

        ContentHistoryResult result = getContentHistoryUseCase.get(USER_ID, CONTENT_ID);

        assertThat(result).isSameAs(expected);
        InOrder inOrder = inOrder(
            contentHistoryTargetService,
            regionAdminAuthorizationService,
            contentHistoryService
        );
        inOrder.verify(contentHistoryTargetService).findById(CONTENT_ID);
        inOrder.verify(regionAdminAuthorizationService).authorize(USER_ID, REGION_ID);
        inOrder.verify(contentHistoryService).findAllByContentId(CONTENT_ID);
    }
}
