package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetOperatorStampbooksUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;

    @Test
    void 활성_운영자는_본인_지역의_스탬프북만_조회한다() {
        OperatorAuthorizationService operatorAuthorizationService = mock(OperatorAuthorizationService.class);
        StampbookReadService stampbookReadService = mock(StampbookReadService.class);
        List<OperatorStampbookListResult> expected = List.of(mock(OperatorStampbookListResult.class));
        AuthorizedOperator operator = authorizedOperator();
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(stampbookReadService.findOperatorStampbooks(USER_ID, REGION_ID)).thenReturn(expected);
        GetOperatorStampbooksUseCase useCase = new GetOperatorStampbooksUseCase(
            operatorAuthorizationService,
            stampbookReadService
        );

        List<OperatorStampbookListResult> actual = useCase.findAll(USER_ID);

        assertThat(actual).isSameAs(expected);
        verify(operatorAuthorizationService).requireAuthorizedOperator(USER_ID);
        verify(stampbookReadService).findOperatorStampbooks(USER_ID, REGION_ID);
    }

    @Test
    void 활성_운영자가_아니면_스탬프북을_조회하지_않는다() {
        OperatorAuthorizationService operatorAuthorizationService = mock(OperatorAuthorizationService.class);
        StampbookReadService stampbookReadService = mock(StampbookReadService.class);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetOperatorStampbooksUseCase useCase = new GetOperatorStampbooksUseCase(
            operatorAuthorizationService,
            stampbookReadService
        );

        assertThatThrownBy(() -> useCase.findAll(USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(operatorAuthorizationService).requireAuthorizedOperator(USER_ID);
        verifyNoInteractions(stampbookReadService);
    }

    private AuthorizedOperator authorizedOperator() {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(roleAssignment.getRoleAssignmentId()).thenReturn(1L);
        return new AuthorizedOperator(user, region, roleAssignment);
    }
}
