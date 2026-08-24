package io.regionevent.regioneventbackend.domain.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.operator.dto.MyOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

class GetMyOperatorApplicationUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T01:15:30Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-21T03:20:10Z");

    private AppUserService appUserService;
    private OperatorApplicationService operatorApplicationService;
    private GetMyOperatorApplicationUseCase useCase;
    private AppUser applicant;

    @BeforeEach
    void setUp() {
        appUserService = mock(AppUserService.class);
        operatorApplicationService = mock(OperatorApplicationService.class);
        useCase = new GetMyOperatorApplicationUseCase(appUserService, operatorApplicationService);
        applicant = mock(AppUser.class);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(applicant);
    }

    @Test
    void get_신청이없음_operatorApplication을_null로반환한다() {
        when(operatorApplicationService.findLatestApplication(applicant)).thenReturn(Optional.empty());

        MyOperatorApplicationResponse response = useCase.get(USER_ID);

        assertThat(response.operatorApplication()).isNull();
        verify(appUserService).findActiveUser(USER_ID);
        verify(operatorApplicationService).findLatestApplication(applicant);
    }

    @Test
    void get_PENDING_심사시각과반려사유를_null로반환한다() {
        OperatorApplication application = application(OperatorApplicationStatus.PENDING, null);
        when(operatorApplicationService.findLatestApplication(applicant))
            .thenReturn(Optional.of(application));

        MyOperatorApplicationResponse.ApplicationResponse response = useCase.get(USER_ID).operatorApplication();

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.reviewedAt()).isNull();
        assertThat(response.rejectedReason()).isNull();
    }

    @Test
    void get_REJECTED_수정시각과반려사유를반환한다() {
        OperatorApplication application = application(
            OperatorApplicationStatus.REJECTED,
            "사업자 정보를 확인할 수 없습니다."
        );
        when(operatorApplicationService.findLatestApplication(applicant)).thenReturn(Optional.of(application));

        MyOperatorApplicationResponse.ApplicationResponse response = useCase.get(USER_ID).operatorApplication();

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(response.rejectedReason()).isEqualTo("사업자 정보를 확인할 수 없습니다.");
    }

    @Test
    void get_APPROVED_수정시각을반환하고반려사유를_null로반환한다() {
        OperatorApplication application = application(OperatorApplicationStatus.APPROVED, null);
        when(operatorApplicationService.findLatestApplication(applicant))
            .thenReturn(Optional.of(application));

        MyOperatorApplicationResponse.ApplicationResponse response = useCase.get(USER_ID).operatorApplication();

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(response.rejectedReason()).isNull();
    }

    @Test
    void get_연결된최신신청이_CANCELLED_과거신청으로대체하지않고불변식위반으로처리한다() {
        OperatorApplication application = application(OperatorApplicationStatus.CANCELLED, null);
        when(operatorApplicationService.findLatestApplication(applicant))
            .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> useCase.get(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("linked operator application must not be cancelled");

        verify(operatorApplicationService).findLatestApplication(applicant);
    }

    private OperatorApplication application(OperatorApplicationStatus status, String rejectedReason) {
        OperatorApplication application = mock(OperatorApplication.class);
        Region region = mock(Region.class);
        when(application.getOperatorApplicationId()).thenReturn(21L);
        when(application.getStatus()).thenReturn(status);
        when(application.getRequestedRegion()).thenReturn(region);
        when(application.getCreatedAt()).thenReturn(CREATED_AT);
        when(application.getUpdatedAt()).thenReturn(REVIEWED_AT);
        when(application.getRejectedReason()).thenReturn(rejectedReason);
        when(region.getRegionId()).thenReturn(1L);
        when(region.getName()).thenReturn("김해시");
        return application;
    }
}
