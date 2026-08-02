package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RejectContentRevisionUseCaseMySqlTest {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final String FIRST_REASON = "첫 번째 동시 반려 사유";
    private static final String SECOND_REASON = "두 번째 동시 반려 사유";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final RejectContentRevisionUseCase rejectContentRevisionUseCase;
    private final ApproveContentRevisionUseCase approveContentRevisionUseCase;
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
    RejectContentRevisionUseCaseMySqlTest(
        RejectContentRevisionUseCase rejectContentRevisionUseCase,
        ApproveContentRevisionUseCase approveContentRevisionUseCase,
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
        this.rejectContentRevisionUseCase = rejectContentRevisionUseCase;
        this.approveContentRevisionUseCase = approveContentRevisionUseCase;
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
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @Timeout(15)
    void 같은_공개_수정본의_동시_반려는_한_건만_성공하고_반려_정보와_감사가_한_번만_저장된다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        List<Attempt> attempts = race(
            () -> reject(fixture, FIRST_REASON),
            () -> reject(fixture, SECOND_REASON)
        );

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null)
            .singleElement()
            .satisfies(attempt -> assertThat(attempt.result())
                .isEqualTo(ContentRevisionStatus.EDIT_REJECTED));
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() != null)
            .extracting(Attempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_STATE_CONFLICT);

        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(revision.getReviewedAt()).isNotNull();
        assertThat(revision.getReviewReason()).isIn(FIRST_REASON, SECOND_REASON);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT reviewed_by_user_id FROM content_revision WHERE content_revision_id = ?",
            Long.class,
            fixture.revisionId()
        )).isEqualTo(fixture.adminId());
        assertThat(contentRevisionRepository.existsByContentContentIdAndStatus(
            fixture.contentId(),
            ContentRevisionStatus.EDIT_REQUESTED
        )).isFalse();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(content.getTitle()).isEqualTo("원본 제목");
        assertThat(content.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertSingleSuccessAudit(fixture);
    }

    @Test
    @Timeout(15)
    void 공개_수정본의_승인과_반려_경합에서는_먼저_종결한_전이만_원본에_반영된다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        OriginalContent original = findOriginalContent(fixture.contentId());
        List<Attempt> attempts = race(
            () -> approve(fixture),
            () -> reject(fixture, FIRST_REASON)
        );

        assertOneTerminalTransitionAndConflict(attempts);
        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        if (revision.getStatus() == ContentRevisionStatus.EDIT_REJECTED) {
            assertOriginalContentIsUnchanged(content, original);
            assertThat(revision.getReviewReason()).isEqualTo(FIRST_REASON);
        } else {
            assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_APPROVED);
            assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
            assertThat(content.getTitle()).isEqualTo("후보 제목");
            assertThat(content.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        }
        assertSingleSuccessAudit(fixture);
    }

    @Test
    @Timeout(15)
    void 공개_전_수정본의_승인과_반려_경합에서는_반려가_먼저_종결하면_원본이_PENDING으로_유지된다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT, true);
        OriginalContent original = findOriginalContent(fixture.contentId());
        List<Attempt> attempts = race(
            () -> approve(fixture),
            () -> reject(fixture, FIRST_REASON)
        );

        assertOneTerminalTransitionAndConflict(attempts);
        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        if (revision.getStatus() == ContentRevisionStatus.EDIT_REJECTED) {
            assertOriginalContentIsUnchanged(content, original);
            assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
            assertThat(revision.getReviewReason()).isEqualTo(FIRST_REASON);
        } else {
            assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_APPROVED);
            assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
            assertThat(content.getTitle()).isEqualTo("후보 제목");
            assertThat(content.getPublishAt()).isEqualTo(CANDIDATE_PUBLISH_AT);
        }
        assertSingleSuccessAudit(fixture);
    }

    private List<Attempt> race(
        ConcurrentAction firstAction,
        ConcurrentAction secondAction
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executorService.submit(() -> executeAfterStart(firstAction, ready, start));
            Future<Attempt> second = executorService.submit(() -> executeAfterStart(secondAction, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private Attempt executeAfterStart(
        ConcurrentAction action,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return new Attempt(action.execute(), null);
        } catch (BusinessException exception) {
            return new Attempt(null, exception.getErrorCode());
        }
    }

    private ContentRevisionStatus reject(Fixture fixture, String reason) {
        return rejectContentRevisionUseCase.reject(
            fixture.adminId(),
            fixture.revisionId(),
            reason,
            UUID.randomUUID()
        ).revisionStatus();
    }

    private ContentRevisionStatus approve(Fixture fixture) {
        return approveContentRevisionUseCase.approve(
            fixture.adminId(),
            fixture.revisionId(),
            UUID.randomUUID()
        ).revisionStatus();
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
            contentRevisionRepository.saveAndFlush(revision);
            return new Fixture(
                admin.getUserId(),
                content.getContentId(),
                revision.getContentRevisionId()
            );
        });
    }

    private ImageObject saveLinkedImage(String suffix, AppUser operator, Region region) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/mysql-rejection-" + suffix + ".webp",
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

    private OriginalContent findOriginalContent(Long contentId) {
        Content content = contentRepository.findById(contentId).orElseThrow();
        return new OriginalContent(
            content.getStatus(),
            content.getTitle(),
            content.getPublishAt(),
            content.getVersionNo()
        );
    }

    private void assertOneTerminalTransitionAndConflict(List<Attempt> attempts) {
        assertThat(attempts).filteredOn(attempt -> attempt.result() != null).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() != null)
            .extracting(Attempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_STATE_CONFLICT);
    }

    private void assertOriginalContentIsUnchanged(Content content, OriginalContent original) {
        assertThat(content.getStatus()).isEqualTo(original.status());
        assertThat(content.getTitle()).isEqualTo(original.title());
        assertThat(content.getPublishAt()).isEqualTo(original.publishAt());
        assertThat(content.getVersionNo()).isEqualTo(original.versionNo());
    }

    private void assertSingleSuccessAudit(Fixture fixture) {
        List<AuditEvent> auditEvents = auditEventRepository.findAll().stream()
            .filter(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .toList();
        assertThat(auditEvents).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getPreviousState()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED.name());
            assertThat(auditEvent.getNextState()).isIn(
                ContentRevisionStatus.EDIT_REJECTED.name(),
                ContentRevisionStatus.EDIT_APPROVED.name()
            );
        });
        assertThat(jdbcTemplate.queryForList(
            "SELECT user_id FROM audit_event_actor_link WHERE audit_event_id = ?",
            Long.class,
            auditEvents.getFirst().getAuditEventId()
        )).containsExactly(fixture.adminId());
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

        ContentRevisionStatus execute();
    }

    private record Attempt(
        ContentRevisionStatus result,
        ErrorCode errorCode
    ) {
    }

    private record OriginalContent(
        ContentStatus status,
        String title,
        Instant publishAt,
        int versionNo
    ) {
    }

    private record Fixture(
        Long adminId,
        Long contentId,
        Long revisionId
    ) {
    }
}
