package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyStampbooksUseCaseTest {

    private static final Long USER_ID = 100L;

    @Test
    void 활성_VISITOR는_본인_스탬프북_목록을_조회한다() {
        UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
        StampbookReadService stampbookReadService = mock(StampbookReadService.class);
        List<MyStampbookListResult> expected = List.of(mock(MyStampbookListResult.class));
        when(stampbookReadService.findMyStampbooks(USER_ID)).thenReturn(expected);
        GetMyStampbooksUseCase useCase = new GetMyStampbooksUseCase(
            userRoleAssignmentService,
            stampbookReadService
        );

        List<MyStampbookListResult> actual = useCase.findAll(USER_ID);

        assertThat(actual).isSameAs(expected);
        verify(userRoleAssignmentService).findActiveVisitor(USER_ID);
        verify(stampbookReadService).findMyStampbooks(USER_ID);
    }

    @Test
    void 활성_VISITOR가_아니면_스탬프북_목록을_조회하지_않는다() {
        UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
        StampbookReadService stampbookReadService = mock(StampbookReadService.class);
        when(userRoleAssignmentService.findActiveVisitor(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyStampbooksUseCase useCase = new GetMyStampbooksUseCase(
            userRoleAssignmentService,
            stampbookReadService
        );

        assertThatThrownBy(() -> useCase.findAll(USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(userRoleAssignmentService).findActiveVisitor(USER_ID);
        verifyNoInteractions(stampbookReadService);
    }
}
