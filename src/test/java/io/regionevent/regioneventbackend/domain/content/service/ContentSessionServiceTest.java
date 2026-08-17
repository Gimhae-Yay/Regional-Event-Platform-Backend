package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ContentSessionServiceTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-08-16T00:00:00Z");
    private static final String CANCELLATION_REASON = "기상 악화";

    private final ContentSessionRepository contentSessionRepository = mock(ContentSessionRepository.class);
    private final ContentSessionService contentSessionService = new ContentSessionService(contentSessionRepository);

    @ParameterizedTest
    @EnumSource(
        value = ContentSessionStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = "SCHEDULED"
    )
    void cancel_SCHEDULED_아닌_회차면_취소_불가_오류를_던진다(ContentSessionStatus sessionStatus) {
        ContentSession contentSession = mock(ContentSession.class);
        AppUser operator = mock(AppUser.class);
        when(contentSession.getStatus()).thenReturn(sessionStatus);

        assertThatThrownBy(() -> contentSessionService.cancel(
            contentSession,
            operator,
            CANCELLED_AT,
            CANCELLATION_REASON
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_CANCELLABLE)
        );

        verify(contentSession).getStatus();
        verifyNoMoreInteractions(contentSession);
        verifyNoInteractions(contentSessionRepository);
    }
}
