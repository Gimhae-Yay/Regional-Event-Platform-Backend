package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@ExtendWith(MockitoExtension.class)
class GetPendingRegionAdminStampbooksUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;

    @Mock
    private RegionAdminAuthorizationService regionAdminAuthorizationService;

    @Mock
    private StampbookReadService stampbookReadService;

    @InjectMocks
    private GetPendingRegionAdminStampbooksUseCase getPendingRegionAdminStampbooksUseCase;

    @Test
    void 담당지역관리자의_심사대기_스탬프북만_조회한다() {
        List<PendingRegionAdminStampbookResult> expected = List.of(
            new PendingRegionAdminStampbookResult(
                101L,
                REGION_ID,
                StampbookStatus.PENDING_REVIEW,
                2,
                301L,
                Instant.parse("2026-08-14T02:20:00Z")
            )
        );
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(stampbookReadService.findPendingRegionAdminStampbooks(REGION_ID)).thenReturn(expected);

        List<PendingRegionAdminStampbookResult> results = getPendingRegionAdminStampbooksUseCase
            .find(USER_ID);

        assertThat(results).isEqualTo(expected);
        verify(regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
        verify(stampbookReadService).findPendingRegionAdminStampbooks(REGION_ID);
    }
}
