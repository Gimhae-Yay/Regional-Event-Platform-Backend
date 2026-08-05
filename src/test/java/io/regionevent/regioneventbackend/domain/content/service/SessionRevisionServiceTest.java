package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class SessionRevisionServiceTest {

    private static final Long REVISION_ID = 52L;

    private final SessionRevisionRepository sessionRevisionRepository = mock(SessionRevisionRepository.class);
    private final SessionRevisionService sessionRevisionService = new SessionRevisionService(sessionRevisionRepository);

    @Test
    void 심사_대기_미삭제_수정_요청만_상세_조회한다() {
        SessionRevision revision = mock(SessionRevision.class);
        when(sessionRevisionRepository.findPendingReviewDetailById(REVISION_ID, SessionRevisionStatus.PENDING))
            .thenReturn(Optional.of(revision));

        assertThat(sessionRevisionService.findPendingReviewDetailById(REVISION_ID)).isSameAs(revision);
    }

    @Test
    void 종결_수정_요청이나_소프트_삭제_콘텐츠는_찾을수없음으로_처리한다() {
        when(sessionRevisionRepository.findPendingReviewDetailById(REVISION_ID, SessionRevisionStatus.PENDING))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionRevisionService.findPendingReviewDetailById(REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }
}
