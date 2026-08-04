package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RejectContentRevisionAuditRollbackMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final RejectContentRevisionUseCase rejectContentRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private AuditEventActorLinkRepository auditEventActorLinkRepository;

    @Autowired
    RejectContentRevisionAuditRollbackMySqlTest(
        RejectContentRevisionUseCase rejectContentRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.rejectContentRevisionUseCase = rejectContentRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void 감사_이벤트_저장_뒤_처리자_연결_실패하면_수정본_반려와_원본_변경이_함께_롤백된다() {
        Fixture fixture = createFixture();
        long auditEventCount = auditEventRepository.count();
        long actorLinkCount = countAuditEventActorLinks();
        doThrow(new IllegalStateException("audit actor link storage failure"))
            .when(auditEventActorLinkRepository)
            .save(any(AuditEventActorLink.class));

        assertThatThrownBy(() -> rejectContentRevisionUseCase.reject(
            fixture.adminId(),
            fixture.revisionId(),
            "감사 실패 반려 사유",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit actor link storage failure");

        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(revision.getReviewedAt()).isNull();
        assertThat(revision.getReviewedBy()).isNull();
        assertThat(revision.getReviewReason()).isNull();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(content.getTitle()).isEqualTo("원본 제목");
        assertThat(content.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(auditEventRepository.count()).isEqualTo(auditEventCount);
        assertThat(countAuditEventActorLinks()).isEqualTo(actorLinkCount);
    }

    private long countAuditEventActorLinks() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event_actor_link",
            Long.class
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = appUserRepository.save(new AppUser(
                "admin-" + suffix + "@example.com",
                "hashed-password",
                "관리자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "원본 제목",
                "원본 설명",
                "원본 장소",
                "원본 운영 시간",
                "055-1234-5678",
                "원본 주의사항",
                "만 7세 이상",
                "편한 복장",
                "원본 취소 정책",
                ORIGINAL_PUBLISH_AT
            ));
            ContentRevision revision = contentRevisionRepository.saveAndFlush(new ContentRevision(
                content,
                1,
                content.getVersionNo(),
                operator,
                ContentRevisionStatus.EDIT_REQUESTED,
                "후보 제목",
                "후보 설명",
                "후보 장소",
                "후보 운영 시간",
                "055-9876-5432",
                "후보 주의사항",
                "만 8세 이상",
                "운동화",
                "후보 취소 정책",
                null,
                SUBMITTED_AT,
                null,
                null,
                null,
                null,
                null,
                null
            ));
            return new Fixture(
                admin.getUserId(),
                content.getContentId(),
                revision.getContentRevisionId()
            );
        });
    }

    private record Fixture(
        Long adminId,
        Long contentId,
        Long revisionId
    ) {
    }
}
