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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
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
class SubmitContentUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final SubmitContentUseCase submitContentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    SubmitContentUseCaseMySqlTest(
        SubmitContentUseCase submitContentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.submitContentUseCase = submitContentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
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
    @Timeout(10)
    void sameRejectedContent_whenSubmittedConcurrently_recordsPendingOnce() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<SubmitAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<SubmitAttempt> first = executorService.submit(
                () -> submitAfterStart(fixture, ready, start)
            );
            Future<SubmitAttempt> second = executorService.submit(
                () -> submitAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(5, TimeUnit.SECONDS),
                second.get(5, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null)
            .singleElement()
            .satisfies(attempt -> assertThat(attempt.result().status()).isEqualTo(ContentStatus.PENDING));
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() == ErrorCode.CONTENT_STATE_CONFLICT)
            .hasSize(1);
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content ->
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING)
            );
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING, ContentLogStatus.REJECTED, ContentLogStatus.PENDING);
        List<AuditEvent> auditEvents = auditEventRepository.findAll()
            .stream()
            .filter(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .toList();

        assertThat(auditEvents)
            .extracting(AuditEvent::getResult)
            .containsExactlyInAnyOrder(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
        assertThat(auditEvents)
            .allSatisfy(auditEvent ->
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(actorLink ->
                        assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.operatorId())
                    )
            );
    }

    private SubmitAttempt submitAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return new SubmitAttempt(submitContentUseCase.submit(
                fixture.operatorId(),
                fixture.contentId(),
                UUID.randomUUID()
            ), null);
        } catch (BusinessException exception) {
            return new SubmitAttempt(null, exception.getErrorCode());
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("R" + suffix, "region", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "User",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.REJECTED,
                "Gimhae culture experience",
                "A local culture experience in Gimhae.",
                "Gimhae civic hall",
                "Every day 10:00-18:00",
                "055-123-4567",
                "Please follow safety guidance.",
                "Age 7 and up",
                "Comfortable clothes",
                "Cancellation is available until one day before.",
                Instant.parse("2026-08-05T00:00:00Z")
            ));
            Instant submittedAt = Instant.parse("2026-08-01T00:00:00Z");
            content.assignRepresentativeImage(saveLinkedImage(region, operator, suffix, submittedAt), submittedAt);
            contentRepository.save(content);
            contentLogRepository.save(new ContentLog(
                content,
                operator,
                ContentLogStatus.PENDING,
                null,
                submittedAt
            ));
            contentLogRepository.save(new ContentLog(
                content,
                operator,
                ContentLogStatus.REJECTED,
                "Need more details.",
                Instant.parse("2026-08-02T00:00:00Z")
            ));
            Instant startsAt = Instant.parse("2026-08-10T01:00:00Z");
            contentSessionRepository.save(new ContentSession(
                content,
                region,
                startsAt,
                startsAt.plusSeconds(7_200),
                startsAt.minusSeconds(1_800),
                startsAt.plusSeconds(5_400),
                20
            ));
            return new Fixture(operator.getUserId(), content.getContentId());
        });
    }

    private ImageObject saveLinkedImage(
        Region region,
        AppUser operator,
        String suffix,
        Instant linkedAt
    ) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "contents/" + suffix + "/representative.jpg",
            operator,
            region,
            "image/jpeg",
            1024L,
            "checksum-" + suffix,
            linkedAt.plusSeconds(3600)
        );
        ImageObject savedImageObject = imageObjectRepository.save(imageObject);
        savedImageObject.markLinked(linkedAt);
        return imageObjectRepository.save(savedImageObject);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent submit did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent submit was interrupted", exception);
        }
    }

    private record SubmitAttempt(
        SubmitContentResult result,
        ErrorCode errorCode
    ) {
    }

    private record Fixture(
        Long operatorId,
        Long contentId
    ) {
    }
}
