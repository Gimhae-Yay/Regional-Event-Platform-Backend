package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.service.ContentLogService;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentUseCase;
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
class ContentRejectionLogAtomicityTest {

    private final RejectContentUseCase rejectContentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    @MockitoBean
    private ContentLogService contentLogService;

    @Autowired
    ContentRejectionLogAtomicityTest(
        RejectContentUseCase rejectContentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        EntityManager entityManager
    ) {
        this.rejectContentUseCase = rejectContentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    @Test
    void rejectContent_whenLogRecordingFails_rollsBackContentAndAudit() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("content log storage failure"))
            .when(contentLogService)
            .recordRejected(any(Content.class), any(AppUser.class), any(Instant.class), any(String.class));

        assertThatThrownBy(() -> rejectContentUseCase.reject(
            fixture.admin().getUserId(),
            fixture.content().getContentId(),
            "반려 사유",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        Content content = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(content.getContentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING);
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region)
        );
        AppUser operator = saveUser("operator-" + suffix);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-05T00:00:00Z")
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PENDING,
            null,
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        return new Fixture(admin, content);
    }

    private AppUser saveUser(String identifierPrefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record Fixture(
        AppUser admin,
        Content content
    ) {
    }
}
