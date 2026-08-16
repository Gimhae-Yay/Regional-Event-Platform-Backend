package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import(AtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
@Sql(statements = """
    CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR "io.regionevent.regioneventbackend.support.jpa.H2MySqlCompatibilityFunctions.unixTimestamp"
    """)
class ApproveContentWithdrawalAuditAtomicityTest {

    private final ApproveContentWithdrawalUseCase useCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditEventRepository auditEventRepository;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    ApproveContentWithdrawalAuditAtomicityTest(
        ApproveContentWithdrawalUseCase useCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        AuditEventRepository auditEventRepository
    ) {
        this.useCase = useCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Test
    void 성공_감사_기록에_실패하면_승인과_연계_상태를_함께_롤백한다() {
        Fixture fixture = createFixture();
        when(recordAuditEventUseCase.record(any(AuditEventCommand.class)))
            .thenReturn(null)
            .thenReturn(null)
            .thenThrow(new IllegalStateException("audit storage failure"));

        assertThatThrownBy(() -> useCase.approve(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(withdrawalRequestRepository.findById(fixture.withdrawalRequestId()))
            .hasValueSatisfying(request -> {
                assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
                assertThat(request.getReviewedAt()).isNull();
                assertThat(request.getReviewedBy()).isNull();
            });
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> {
                assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
                assertThat(revision.getInvalidatedAt()).isNull();
                assertThat(revision.getInvalidationReason()).isNull();
            });
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        AppUser operator = saveUser("operator-" + suffix);
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
            now.minusSeconds(86_400)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PUBLISHED,
            null,
            now.minusSeconds(86_400)
        ));
        ContentRevision revision = contentRevisionRepository.saveAndFlush(new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "수정 제목",
            "수정 설명",
            "김해문화의전당",
            "매일 11:00~19:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            0,
            null,
            now.minusSeconds(3_600),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        ContentWithdrawalRequest request = withdrawalRequestRepository.saveAndFlush(
            ContentWithdrawalRequest.createPending(
                content,
                operator,
                "a".repeat(64),
                "운영 계획 변경",
                now.minusSeconds(1_800)
            )
        );
        return new Fixture(
            admin.getUserId(),
            content.getContentId(),
            revision.getContentRevisionId(),
            request.getContentWithdrawalRequestId()
        );
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record Fixture(
        Long adminId,
        Long contentId,
        Long revisionId,
        Long withdrawalRequestId
    ) {
    }
}
