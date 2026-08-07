package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

class ContentHistoryServiceTest {

    private static final Long CONTENT_ID = 10L;
    private static final Instant PROCESSED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final ContentLogRepository contentLogRepository = mock(ContentLogRepository.class);
    private final ContentHistoryService contentHistoryService = new ContentHistoryService(contentLogRepository);

    @Test
    void findAllByContentId_mapsActiveSystemAndWithdrawnActors() {
        ContentLog activeActorLog = contentLog(ContentLogStatus.REJECTED, activeActor());
        ContentLog systemLog = contentLog(ContentLogStatus.PUBLISHED, null);
        ContentLog withdrawnActorLog = contentLog(ContentLogStatus.APPROVED, null);
        when(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(CONTENT_ID))
            .thenReturn(List.of(activeActorLog, systemLog, withdrawnActorLog));

        ContentHistoryResult result = contentHistoryService.findAllByContentId(CONTENT_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.histories()).hasSize(3);
        assertThat(result.histories().get(0).actor())
            .isEqualTo(new ContentHistoryResult.Actor(30L, "김해 지역 관리자"));
        assertThat(result.histories().get(1).actor()).isNull();
        assertThat(result.histories().get(2).actor())
            .isEqualTo(new ContentHistoryResult.Actor(null, "탈퇴한 사용자"));
        assertThatThrownBy(() -> result.histories().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findAllByContentId_whenHistoryIsEmpty_returnsEmptyList() {
        when(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(CONTENT_ID))
            .thenReturn(List.of());

        ContentHistoryResult result = contentHistoryService.findAllByContentId(CONTENT_ID);

        assertThat(result.histories()).isEmpty();
    }

    private ContentLog contentLog(ContentLogStatus status, AppUser actor) {
        ContentLog contentLog = mock(ContentLog.class);
        when(contentLog.getStatus()).thenReturn(status);
        when(contentLog.getReason()).thenReturn(status == ContentLogStatus.REJECTED ? "반려 사유" : null);
        when(contentLog.getDate()).thenReturn(PROCESSED_AT);
        when(contentLog.getActor()).thenReturn(actor);
        return contentLog;
    }

    private AppUser activeActor() {
        AppUser actor = mock(AppUser.class);
        when(actor.getUserId()).thenReturn(30L);
        when(actor.getName()).thenReturn("김해 지역 관리자");
        return actor;
    }
}
