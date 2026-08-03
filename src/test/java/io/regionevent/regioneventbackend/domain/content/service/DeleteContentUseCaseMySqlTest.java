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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
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
class DeleteContentUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final DeleteContentUseCase deleteContentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private ImageStorageGateway imageStorageGateway;

    @Autowired
    DeleteContentUseCaseMySqlTest(
        DeleteContentUseCase deleteContentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.deleteContentUseCase = deleteContentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void concurrentDeletion_commitsOnlyFirstDeletionAndRecordsIndependentFailureAudit() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Attempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executorService.submit(() -> deleteAfterStart(fixture, ready, start));
            Future<Attempt> second = executorService.submit(() -> deleteAfterStart(fixture, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() != null)
            .extracting(Attempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_DELETE_CONFLICT);
        assertThat(contentRepository.findById(fixture.contentId()))
            .get()
            .satisfies(content -> {
                assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
                assertThat(content.getDeletedAt()).isNotNull();
                assertThat(content.getRepresentativeImageObject()).isNull();
            });
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.APPROVED, ContentLogStatus.DELETED);
        assertThat(auditEventRepository.findAll())
            .extracting(auditEvent -> auditEvent.getResult())
            .containsExactlyInAnyOrder(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
        assertThat(auditEventActorLinkRepository.count()).isOne();
    }

    @Test
    @Timeout(15)
    void automaticPublicationRace_allowsOnlyConditionalTransitionOrDeletionToCommit() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Attempt deletionAttempt;
        int publicationCount;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> deletion = executorService.submit(() -> deleteAfterStart(fixture, ready, start));
            Future<Integer> publication = executorService.submit(
                () -> publishAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            deletionAttempt = deletion.get(10, TimeUnit.SECONDS);
            publicationCount = publication.get(10, TimeUnit.SECONDS);
        }

        assertThat((deletionAttempt.result() == null ? 0 : 1) + publicationCount).isOne();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        if (publicationCount == 1) {
            assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
            assertThat(content.getDeletedAt()).isNull();
            assertThat(deletionAttempt.errorCode()).isEqualTo(ErrorCode.CONTENT_DELETE_CONFLICT);
        } else {
            assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
            assertThat(content.getDeletedAt()).isNotNull();
            assertThat(deletionAttempt.result()).isNotNull();
        }
    }

    private Attempt deleteAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            DeleteContentResult result = deleteContentUseCase.delete(
                fixture.adminId(),
                fixture.contentId(),
                "행사 준비가 취소되었습니다.",
                UUID.randomUUID()
            );
            return new Attempt(result, null);
        } catch (BusinessException exception) {
            return new Attempt(null, exception.getErrorCode());
        }
    }

    private int publishAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        Integer updatedCount = transactionTemplate.execute(status ->
            contentRepository.updateStatusIfExpected(
                fixture.contentId(),
                ContentStatus.APPROVED,
                ContentStatus.PUBLISHED,
                Instant.now()
            )
        );
        return updatedCount == null ? 0 : updatedCount;
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("DELETE-" + suffix, "김해시", true));
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
            ImageObject imageObject = ImageObject.createUploadCandidate(
                "content/delete-" + suffix + ".webp",
                operator,
                region,
                "image/webp",
                1024L,
                "checksum",
                Instant.now().plusSeconds(3600)
            );
            imageObject.markLinked(Instant.now());
            imageObjectRepository.save(imageObject);
            Content content = new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.APPROVED,
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
            );
            content.assignRepresentativeImage(imageObject, Instant.now());
            contentRepository.save(content);
            contentLogRepository.save(new ContentLog(
                content,
                admin,
                ContentLogStatus.APPROVED,
                null,
                Instant.now()
            ));
            return new Fixture(admin.getUserId(), content.getContentId());
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent deletion did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent deletion was interrupted", exception);
        }
    }

    private record Attempt(
        DeleteContentResult result,
        ErrorCode errorCode
    ) {
    }

    private record Fixture(
        Long adminId,
        Long contentId
    ) {
    }
}
