package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    RequestContentWithdrawalUseCase.class,
    ContentWithdrawalRequestService.class,
    ContentWithdrawalRequestHasher.class,
    OperatorAuthorizationService.class,
    RegionService.class,
    ContentService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class RequestContentWithdrawalAuditAtomicityTest {

    private final RequestContentWithdrawalUseCase useCase;
    private final ContentWithdrawalRequestRepository requestRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @MockitoBean
    private ContentService contentService;

    @Autowired
    RequestContentWithdrawalAuditAtomicityTest(
        RequestContentWithdrawalUseCase useCase,
        ContentWithdrawalRequestRepository requestRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository
    ) {
        this.useCase = useCase;
        this.requestRepository = requestRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Test
    void 성공_감사_저장에_실패하면_철회_요청도_롤백한다() {
        Fixture fixture = createFixture();
        when(contentService.findContentRegionId(fixture.content().getContentId()))
            .thenReturn(fixture.content().getRegion().getRegionId());
        when(contentService.findWithdrawalRequestTargetForUpdate(fixture.content().getContentId()))
            .thenReturn(fixture.content());
        when(contentService.findCurrentDatabaseTime())
            .thenReturn(Instant.parse("2026-08-16T04:00:00Z"));
        doThrow(new IllegalStateException("audit failure"))
            .when(recordAuditEventUseCase)
            .record(any());

        assertThatThrownBy(() -> useCase.request(
            fixture.operator().getUserId(),
            fixture.content().getContentId(),
            "request-key",
            "운영 계획 변경",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit failure");

        assertThat(requestRepository.count()).isZero();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operator,
            UserRole.OPERATOR,
            region
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        return new Fixture(operator, content);
    }

    private record Fixture(AppUser operator, Content content) {
    }
}
