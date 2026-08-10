package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyStampbookDetailUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long STAMPBOOK_ID = 101L;

    @Test
    void 활성_VISITOR는_본인_스탬프북_상세를_조회한다() {
        UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
        StampbookReadService stampbookReadService = mock(StampbookReadService.class);
        MyStampbookDetailResult expected = mock(MyStampbookDetailResult.class);
        when(stampbookReadService.findMyStampbookDetail(USER_ID, STAMPBOOK_ID)).thenReturn(expected);
        GetMyStampbookDetailUseCase useCase = new GetMyStampbookDetailUseCase(
            userRoleAssignmentService,
            stampbookReadService
        );

        MyStampbookDetailResult actual = useCase.find(USER_ID, STAMPBOOK_ID);

        assertThat(actual).isSameAs(expected);
        verify(userRoleAssignmentService).findActiveVisitor(USER_ID);
        verify(stampbookReadService).findMyStampbookDetail(USER_ID, STAMPBOOK_ID);
    }

    @Test
    void 활성_VISITOR가_아니면_스탬프북_상세를_조회하지_않는다() {
        UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
        StampbookReadService stampbookReadService = mock(StampbookReadService.class);
        when(userRoleAssignmentService.findActiveVisitor(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyStampbookDetailUseCase useCase = new GetMyStampbookDetailUseCase(
            userRoleAssignmentService,
            stampbookReadService
        );

        assertThatThrownBy(() -> useCase.find(USER_ID, STAMPBOOK_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(userRoleAssignmentService).findActiveVisitor(USER_ID);
        verifyNoInteractions(stampbookReadService);
    }
}
