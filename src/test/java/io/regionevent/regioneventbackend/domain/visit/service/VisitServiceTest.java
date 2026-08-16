package io.regionevent.regioneventbackend.domain.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

class VisitServiceTest {

    private static final Long VISIT_ID = 10L;

    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final VisitService visitService = new VisitService(visitRepository);

    @Test
    void findMissionProgressSource_식별자가유효하지않으면정상무변경을반환한다() {
        assertThat(visitService.findMissionProgressSource(null)).isEmpty();
        assertThat(visitService.findMissionProgressSource(0L)).isEmpty();

        verify(visitRepository, never()).findMissionProgressSourceByVisitId(VISIT_ID);
    }

    @Test
    void findMissionProgressSource_작성자연결이해제된방문이면정상무변경을반환한다() {
        Visit visit = mock(Visit.class);
        when(visit.getUser()).thenReturn(null);
        when(visit.getAuthorUnlinkedAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));
        when(visitRepository.findMissionProgressSourceByVisitId(VISIT_ID)).thenReturn(Optional.of(visit));

        assertThat(visitService.findMissionProgressSource(VISIT_ID)).isEmpty();
    }

    @Test
    void findMissionProgressSource_영속사용자가연결된방문이면반환한다() {
        Visit visit = mock(Visit.class);
        when(visit.getUser()).thenReturn(mock(AppUser.class));
        when(visitRepository.findMissionProgressSourceByVisitId(VISIT_ID)).thenReturn(Optional.of(visit));

        assertThat(visitService.findMissionProgressSource(VISIT_ID)).containsSame(visit);
    }

    @Test
    void findStampbookProgressSourceInCurrentTransaction_영속사용자가연결된방문을잠금조회한다() {
        Visit visit = mock(Visit.class);
        when(visit.getUser()).thenReturn(mock(AppUser.class));
        when(visitRepository.findStampbookProgressSourceByVisitIdForUpdate(VISIT_ID))
            .thenReturn(Optional.of(visit));

        assertThat(visitService.findStampbookProgressSourceInCurrentTransaction(VISIT_ID)).containsSame(visit);

        verify(visitRepository).findStampbookProgressSourceByVisitIdForUpdate(VISIT_ID);
    }
}
