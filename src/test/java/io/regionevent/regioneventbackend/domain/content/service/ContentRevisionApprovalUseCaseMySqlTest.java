package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentRevisionApprovalUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final ApproveContentRevisionUseCase approveContentRevisionUseCase;
    private final RejectContentRevisionUseCase rejectContentRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ContentRevisionApprovalUseCaseMySqlTest(
        ApproveContentRevisionUseCase approveContentRevisionUseCase,
        RejectContentRevisionUseCase rejectContentRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ImageObjectRepository imageObjectRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.approveContentRevisionUseCase = approveContentRevisionUseCase;
        this.rejectContentRevisionUseCase = rejectContentRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void 승인과_반려가_경합하면_하나의_종결_전이만_커밋한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        ConcurrentOutcomes outcomes = race(
            () -> approve(fixture),
            () -> reject(fixture)
        );

        assertThat(outcomes.values()).hasSize(2).contains("CONFLICT");
        assertThat(outcomes.values()).anyMatch(
            outcome -> outcome.equals("APPROVED") || outcome.equals("REJECTED")
        );
        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        if (revision.getStatus() == ContentRevisionStatus.EDIT_APPROVED) {
            assertThat(content.getTitle()).isEqualTo("후보 제목");
            assertThat(revision.getReviewReason()).isNull();
        } else {
            assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
            assertThat(content.getTitle()).isEqualTo("원본 제목");
            assertThat(revision.getReviewReason()).isEqualTo("경합 반려 사유");
        }
        assertSingleSuccessAudit(fixture);
    }

    @Test
    @Timeout(15)
    void 승인과_철회가_경합하면_하나의_종결_전이만_커밋한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        ConcurrentOutcomes outcomes = race(
            () -> approve(fixture),
            () -> withdraw(fixture)
        );

        assertThat(outcomes.values()).hasSize(2).contains("CONFLICT");
        assertThat(outcomes.values()).anyMatch(
            outcome -> outcome.equals("APPROVED") || outcome.equals("WITHDRAWN")
        );
        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        if (revision.getStatus() == ContentRevisionStatus.EDIT_APPROVED) {
            assertThat(content.getTitle()).isEqualTo("후보 제목");
            assertThat(successAuditCount(fixture)).isEqualTo(1);
        } else {
            assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_WITHDRAWN);
            assertThat(revision.getWithdrawalReason()).isEqualTo("경합 철회 사유");
            assertThat(content.getTitle()).isEqualTo("원본 제목");
            assertThat(successAuditCount(fixture)).isZero();
        }
    }

    @Test
    @Timeout(15)
    void 승인과_자동_공개가_경합하면_원본_상태와_수정본을_함께_보호한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT, true);
        ConcurrentOutcomes outcomes = race(
            () -> approve(fixture),
            () -> autoPublish(fixture)
        );

        assertThat(outcomes.values()).hasSize(2).contains("CONFLICT");
        assertThat(outcomes.values()).anyMatch(
            outcome -> outcome.equals("APPROVED") || outcome.equals("PUBLISHED")
        );
        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        if (revision.getStatus() == ContentRevisionStatus.EDIT_APPROVED) {
            assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
            assertThat(content.getPublishAt()).isEqualTo(CANDIDATE_PUBLISH_AT);
            assertThat(content.getTitle()).isEqualTo("후보 제목");
            assertThat(successAuditCount(fixture)).isEqualTo(1);
        } else {
            assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
            assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
            assertThat(content.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
            assertThat(content.getTitle()).isEqualTo("원본 제목");
            assertThat(successAuditCount(fixture)).isZero();
        }
    }

    private ConcurrentOutcomes race(
        ConcurrentAction firstAction,
        ConcurrentAction secondAction
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<String> first = executorService.submit(() -> executeAfterStart(firstAction, ready, start));
            Future<String> second = executorService.submit(() -> executeAfterStart(secondAction, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return new ConcurrentOutcomes(List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            ));
        }
    }

    private String executeAfterStart(
        ConcurrentAction action,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return action.execute();
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.CONTENT_STATE_CONFLICT) {
                return "CONFLICT";
            }
            throw exception;
        }
    }

    private String approve(Fixture fixture) {
        approveContentRevisionUseCase.approve(
            fixture.adminId(),
            fixture.revisionId(),
            UUID.randomUUID()
        );
        return "APPROVED";
    }

    private String reject(Fixture fixture) {
        rejectContentRevisionUseCase.reject(
            fixture.adminId(),
            fixture.revisionId(),
            "경합 반려 사유",
            UUID.randomUUID()
        );
        return "REJECTED";
    }

    private String withdraw(Fixture fixture) {
        int updated = transactionTemplate.execute(status -> jdbcTemplate.update(
            """
                UPDATE content_revision
                SET status = 'EDIT_WITHDRAWN',
                    withdrawn_at = ?,
                    withdrawn_by_user_id = ?,
                    withdrawal_reason = ?
                WHERE content_revision_id = ?
                  AND status = 'EDIT_REQUESTED'
                """,
            Timestamp.from(SUBMITTED_AT.plusSeconds(120)),
            fixture.operatorId(),
            "경합 철회 사유",
            fixture.revisionId()
        ));
        return updated == 1 ? "WITHDRAWN" : "CONFLICT";
    }

    private String autoPublish(Fixture fixture) {
        int updated = transactionTemplate.execute(status -> jdbcTemplate.update(
            """
                UPDATE content
                SET status = 'PUBLISHED',
                    version_no = version_no + 1
                WHERE content_id = ?
                  AND status = 'PENDING'
                  AND deleted_at IS NULL
                """,
            fixture.contentId()
        ));
        return updated == 1 ? "PUBLISHED" : "CONFLICT";
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        Instant candidatePublishAt,
        boolean prePublicationHistory
    ) {
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
            ImageObject originalImage = saveLinkedImage("original-" + suffix, operator, region);
            Content content = new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                contentStatus,
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
            );
            content.assignRepresentativeImage(originalImage, SUBMITTED_AT.minusSeconds(600));
            contentRepository.save(content);
            if (prePublicationHistory) {
                contentLogRepository.save(new ContentLog(
                    content,
                    admin,
                    ContentLogStatus.APPROVED,
                    null,
                    SUBMITTED_AT.minusSeconds(120)
                ));
                contentLogRepository.save(new ContentLog(
                    content,
                    operator,
                    ContentLogStatus.PENDING,
                    null,
                    SUBMITTED_AT.minusSeconds(60)
                ));
            }
            ImageObject candidateImage = saveLinkedImage("candidate-" + suffix, operator, region);
            ContentRevision revision = new ContentRevision(
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
                candidatePublishAt,
                SUBMITTED_AT,
                null,
                null,
                null,
                null,
                null,
                null
            );
            revision.assignCandidateImage(candidateImage, SUBMITTED_AT);
            contentRevisionRepository.save(revision);
            contentRevisionRepository.flush();
            return new Fixture(
                admin.getUserId(),
                operator.getUserId(),
                content.getContentId(),
                revision.getContentRevisionId()
            );
        });
    }

    private ImageObject saveLinkedImage(String suffix, AppUser operator, Region region) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/mysql-approval-" + suffix + ".webp",
            operator,
            region,
            "image/webp",
            1L,
            "checksum-" + suffix,
            SUBMITTED_AT.plusSeconds(3_600)
        );
        imageObject.markLinked(SUBMITTED_AT.minusSeconds(1));
        return imageObjectRepository.save(imageObject);
    }

    private void assertSingleSuccessAudit(Fixture fixture) {
        assertThat(successAuditCount(fixture)).isEqualTo(1);
    }

    private long successAuditCount(Fixture fixture) {
        return auditEventRepository.findAll().stream()
            .filter(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .count();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent transition did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent transition was interrupted", exception);
        }
    }

    @FunctionalInterface
    private interface ConcurrentAction {

        String execute();
    }

    private record ConcurrentOutcomes(List<String> values) {
    }

    private record Fixture(
        Long adminId,
        Long operatorId,
        Long contentId,
        Long revisionId
    ) {
    }
}
