package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
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
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "content.publication.initial-delay=PT24H",
    "content.publication.fixed-delay=PT24H"
})
@Import(PublishApprovedContentsUseCaseMySqlTest.FailingPublicationServicesConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PublishApprovedContentsUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final PublishApprovedContentUseCase publishApprovedContentUseCase;
    private final PublishApprovedContentsUseCase publishApprovedContentsUseCase;
    private final CreateContentRevisionUseCase createContentRevisionUseCase;
    private final DeleteContentUseCase deleteContentUseCase;
    private final ContentService contentService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;
    private final MutableTestClock mutableTestClock;
    private final FailingContentService failingContentService;
    private final FailingContentLogService failingContentLogService;
    private final FailingRecordAuditEventUseCase failingRecordAuditEventUseCase;

    @MockitoBean
    private ImageStorageGateway imageStorageGateway;

    @Autowired
    PublishApprovedContentsUseCaseMySqlTest(
        PublishApprovedContentUseCase publishApprovedContentUseCase,
        PublishApprovedContentsUseCase publishApprovedContentsUseCase,
        CreateContentRevisionUseCase createContentRevisionUseCase,
        DeleteContentUseCase deleteContentUseCase,
        ContentService contentService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ImageObjectRepository imageObjectRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        ContentRevisionRepository contentRevisionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager,
        MutableTestClock mutableTestClock,
        FailingContentService failingContentService,
        FailingContentLogService failingContentLogService,
        FailingRecordAuditEventUseCase failingRecordAuditEventUseCase
    ) {
        this.publishApprovedContentUseCase = publishApprovedContentUseCase;
        this.publishApprovedContentsUseCase = publishApprovedContentsUseCase;
        this.createContentRevisionUseCase = createContentRevisionUseCase;
        this.deleteContentUseCase = deleteContentUseCase;
        this.contentService = contentService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        transactionTemplate = new TransactionTemplate(transactionManager);
        this.mutableTestClock = mutableTestClock;
        this.failingContentService = failingContentService;
        this.failingContentLogService = failingContentLogService;
        this.failingRecordAuditEventUseCase = failingRecordAuditEventUseCase;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @AfterEach
    void resetFailureInjection() {
        mutableTestClock.reset();
        failingContentService.resetFailureInjection();
        failingContentLogService.resetFailureInjection();
        failingRecordAuditEventUseCase.resetFailureInjection();
    }

    @Test
    void MySQL_현재_시각_전의_콘텐츠는_애플리케이션_시계가_앞서도_공개하지_않는다() {
        Instant databaseNow = contentService.findCurrentDatabaseTime();
        mutableTestClock.setInstant(databaseNow.plusSeconds(7_200));
        Fixture fixture = createFixture(databaseNow.plusSeconds(3_600));

        PublishApprovedContentsResult result = publishApprovedContentsUseCase.publishApprovedContents();

        assertThat(result.candidateContentCount()).isZero();
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED));
        assertPublishedStateIsNotRecorded(fixture.contentId());
    }

    @Test
    @Timeout(15)
    void 중복_스케줄러_실행에서도_상태_로그_성공감사는_한번만_기록된다() throws Exception {
        Fixture fixture = createFixture(contentService.findCurrentDatabaseTime().minusSeconds(1));
        failingContentService.blockPublicationTargetLookups(2);

        List<PublishApprovedContentsResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<PublishApprovedContentsResult> first = executorService.submit(
                publishApprovedContentsUseCase::publishApprovedContents
            );
            Future<PublishApprovedContentsResult> second = executorService.submit(
                publishApprovedContentsUseCase::publishApprovedContents
            );
            failingContentService.awaitPublicationTargetLookups();
            failingContentService.releasePublicationTargetLookups();
            results = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(results.stream().mapToInt(PublishApprovedContentsResult::publishedContentCount).sum())
            .isEqualTo(1);
        assertThat(results.stream().mapToInt(PublishApprovedContentsResult::failedContentCount).sum())
            .isZero();
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertPublishedStateIsRecordedOnce(fixture.contentId());
    }

    @Test
    @Timeout(15)
    void 자동_공개와_공개_전_수정_요청이_경합해도_두_상태_전이를_함께_커밋하지_않는다() throws Exception {
        Fixture fixture = createFixture(contentService.findCurrentDatabaseTime().minusSeconds(1));
        failingContentService.blockPublicationTargetLookups(1);

        PublishApprovedContentsResult publicationResult;
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            Future<PublishApprovedContentsResult> publication = executorService.submit(
                publishApprovedContentsUseCase::publishApprovedContents
            );
            failingContentService.awaitPublicationTargetLookups();
            createRevision(fixture);
            failingContentService.releasePublicationTargetLookups();
            publicationResult = publication.get(10, TimeUnit.SECONDS);
        }

        assertThat(publicationResult.candidateContentCount()).isEqualTo(1);
        assertThat(publicationResult.publishedContentCount()).isZero();
        assertThat(publicationResult.skippedContentCount()).isEqualTo(1);
        assertThat(publicationResult.failedContentCount()).isZero();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
        assertThat(countContentLogs(fixture.contentId(), ContentLogStatus.PUBLISHED)).isZero();
        assertThat(countContentLogs(fixture.contentId(), ContentLogStatus.PENDING)).isEqualTo(1);
        assertThat(contentRevisionRepository.findAll()).hasSize(1);
        assertThat(countSuccessAudits(fixture.contentId())).isEqualTo(1);
        assertThat(countTargetAudits(fixture.contentId(), AuditEventResult.FAILURE)).isZero();
    }

    @Test
    @Timeout(15)
    void 자동_공개와_삭제가_경합해도_공개와_소프트삭제를_함께_커밋하지_않는다() throws Exception {
        Fixture fixture = createFixture(contentService.findCurrentDatabaseTime().minusSeconds(1));
        failingContentService.blockPublicationTargetLookups(1);

        PublishApprovedContentsResult publicationResult;
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            Future<PublishApprovedContentsResult> publication = executorService.submit(
                publishApprovedContentsUseCase::publishApprovedContents
            );
            failingContentService.awaitPublicationTargetLookups();
            deleteContent(fixture);
            failingContentService.releasePublicationTargetLookups();
            publicationResult = publication.get(10, TimeUnit.SECONDS);
        }

        assertThat(publicationResult.candidateContentCount()).isEqualTo(1);
        assertThat(publicationResult.publishedContentCount()).isZero();
        assertThat(publicationResult.skippedContentCount()).isEqualTo(1);
        assertThat(publicationResult.failedContentCount()).isZero();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
        assertThat(content.getDeletedAt()).isNotNull();
        assertPublishedStateIsNotRecorded(fixture.contentId());
        assertThat(countTargetAudits(fixture.contentId(), AuditEventResult.FAILURE)).isZero();
    }

    @Test
    void 상태_로그_성공감사_저장_실패는_공개_처리를_롤백하고_실패감사만_별도_기록한다() {
        assertAtomicRollbackAfterFailure(FailurePoint.CONTENT_STATE);
        assertAtomicRollbackAfterFailure(FailurePoint.CONTENT_LOG);
        assertAtomicRollbackAfterFailure(FailurePoint.SUCCESS_AUDIT);
    }

    @Test
    void 후보_하나가_실패해도_다음_후보를_계속_처리한다() {
        Fixture failedFixture = createFixture(contentService.findCurrentDatabaseTime().minusSeconds(1));
        Fixture publishedFixture = createFixture(contentService.findCurrentDatabaseTime().minusSeconds(1));
        failingContentService.failNextPublish();

        PublishApprovedContentsResult result = publishApprovedContentsUseCase.publishApprovedContents();

        assertThat(result.candidateContentCount()).isEqualTo(2);
        assertThat(result.publishedContentCount()).isEqualTo(1);
        assertThat(result.failedContentCount()).isEqualTo(1);
        assertThat(contentRepository.findById(failedFixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED));
        assertThat(contentRepository.findById(publishedFixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertFailureAuditIsRecorded(failedFixture.contentId());
        assertPublishedStateIsRecordedOnce(publishedFixture.contentId());
    }

    @Test
    void 정상_건너뛰기는_실패감사로_기록하지_않는다() {
        Fixture fixture = createFixture(ContentStatus.PENDING, contentService.findCurrentDatabaseTime().minusSeconds(1));

        PublishApprovedContentResult result = publishApprovedContentUseCase.publish(
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(PublishApprovedContentResult.Status.SKIPPED);
        assertThat(countTargetAudits(fixture.contentId(), AuditEventResult.FAILURE)).isZero();
        assertPublishedStateIsNotRecorded(fixture.contentId());
    }

    private void assertAtomicRollbackAfterFailure(FailurePoint failurePoint) {
        Fixture fixture = createFixture(contentService.findCurrentDatabaseTime().minusSeconds(1));
        enableFailure(failurePoint);

        assertThatThrownBy(() -> publishApprovedContentUseCase.publish(
            fixture.contentId(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage(failurePoint.failureMessage());

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED));
        assertPublishedStateIsNotRecorded(fixture.contentId());
        assertFailureAuditIsRecorded(fixture.contentId());
    }

    private void enableFailure(FailurePoint failurePoint) {
        switch (failurePoint) {
            case CONTENT_STATE -> failingContentService.failNextPublish();
            case CONTENT_LOG -> failingContentLogService.failNextPublishedLog();
            case SUCCESS_AUDIT -> failingRecordAuditEventUseCase.failNextRecord();
        }
    }

    private void createRevision(Fixture fixture) {
        createContentRevisionUseCase.createRevision(
            fixture.operatorId(),
            fixture.contentId(),
            createRevisionRequest(),
            UUID.randomUUID().toString()
        );
    }

    private void deleteContent(Fixture fixture) {
        deleteContentUseCase.delete(
            fixture.adminId(),
            fixture.contentId(),
            "행사 준비가 취소되었습니다.",
            UUID.randomUUID()
        );
    }

    private CreateContentRevisionRequest createRevisionRequest() {
        return new CreateContentRevisionRequest(
            "수정 제목",
            "수정 설명",
            "수정 장소",
            "수정 운영 시간",
            "055-9876-5432",
            "수정 주의사항",
            "만 8세 이상",
            "운동화",
            "수정 취소 정책",
            OffsetDateTime.ofInstant(Instant.now().plusSeconds(7_200), ZoneOffset.ofHours(9)),
            null
        );
    }

    private Fixture createFixture(Instant publishAt) {
        return createFixture(ContentStatus.APPROVED, publishAt);
    }

    private Fixture createFixture(ContentStatus status, Instant publishAt) {
        return transactionTemplate.execute(transactionStatus -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant createdAt = Instant.now();
            Region region = regionRepository.saveAndFlush(new Region("P" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            AppUser operator = saveUser("operator-" + suffix);
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
            ImageObject imageObject = ImageObject.createUploadCandidate(
                "content/publication-" + suffix + ".webp",
                operator,
                region,
                "image/webp",
                1L,
                "checksum-" + suffix,
                createdAt.plusSeconds(3_600)
            );
            imageObject.markLinked(createdAt);
            imageObjectRepository.saveAndFlush(imageObject);
            Content content = contentRepository.saveAndFlush(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                status,
                "김해 가야 문화 체험",
                "김해 가야 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                publishAt
            ));
            content.assignRepresentativeImage(imageObject, createdAt);
            contentRepository.saveAndFlush(content);
            ContentLogStatus initialLogStatus = status == ContentStatus.PENDING
                ? ContentLogStatus.PENDING
                : ContentLogStatus.APPROVED;
            AppUser initialLogActor = status == ContentStatus.PENDING ? operator : admin;
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                initialLogActor,
                initialLogStatus,
                null,
                createdAt
            ));
            return new Fixture(admin.getUserId(), operator.getUserId(), content.getContentId());
        });
    }

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void assertPublishedStateIsRecordedOnce(Long contentId) {
        assertThat(countContentLogs(contentId, ContentLogStatus.PUBLISHED)).isEqualTo(1);
        assertThat(countPublicationAudits(contentId, AuditEventResult.SUCCESS)).isEqualTo(1);
        assertThat(countTargetAudits(contentId, AuditEventResult.FAILURE)).isZero();
    }

    private void assertPublishedStateIsNotRecorded(Long contentId) {
        assertThat(countContentLogs(contentId, ContentLogStatus.PUBLISHED)).isZero();
        assertThat(countPublicationAudits(contentId, AuditEventResult.SUCCESS)).isZero();
    }

    private void assertFailureAuditIsRecorded(Long contentId) {
        AuditEvent failureAudit = auditEventRepository.findAll().stream()
            .filter(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filter(auditEvent -> contentId.equals(auditEvent.getTargetId()))
            .filter(auditEvent -> auditEvent.getResult() == AuditEventResult.FAILURE)
            .findFirst()
            .orElseThrow();
        assertThat(countTargetAudits(contentId, AuditEventResult.FAILURE)).isEqualTo(1);
        assertThat(failureAudit.getReasonCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.code());
        assertThat(failureAudit.getActorKind()).isEqualTo("SYSTEM");
        assertThat(failureAudit.getActorRole()).isNull();
        assertThat(auditEventActorLinkRepository.existsById(failureAudit.getAuditEventId())).isFalse();
    }

    private long countContentLogs(Long contentId, ContentLogStatus status) {
        return contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(contentId).stream()
            .filter(contentLog -> contentLog.getStatus() == status)
            .count();
    }

    private long countSuccessAudits(Long contentId) {
        return countTargetAudits(contentId, AuditEventResult.SUCCESS);
    }

    private long countPublicationAudits(Long contentId, AuditEventResult result) {
        return auditEventRepository.findAll().stream()
            .filter(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filter(auditEvent -> contentId.equals(auditEvent.getTargetId()))
            .filter(auditEvent -> ContentStatus.APPROVED.name().equals(auditEvent.getPreviousState()))
            .filter(auditEvent -> ContentStatus.PUBLISHED.name().equals(auditEvent.getNextState()))
            .filter(auditEvent -> auditEvent.getResult() == result)
            .count();
    }

    private long countTargetAudits(Long contentId, AuditEventResult result) {
        return auditEventRepository.findAll().stream()
            .filter(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filter(auditEvent -> contentId.equals(auditEvent.getTargetId()))
            .filter(auditEvent -> auditEvent.getResult() == result)
            .count();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent publication did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent publication was interrupted", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingPublicationServicesConfig {

        @Bean
        @Primary
        MutableTestClock mutableTestClock() {
            return new MutableTestClock();
        }

        @Bean
        @Primary
        FailingContentService failingContentService(ContentRepository contentRepository) {
            return new FailingContentService(contentRepository);
        }

        @Bean
        @Primary
        FailingContentLogService failingContentLogService(ContentLogRepository contentLogRepository) {
            return new FailingContentLogService(contentLogRepository);
        }

        @Bean
        @Primary
        FailingRecordAuditEventUseCase failingRecordAuditEventUseCase(
            AuditEventService auditEventService,
            AuditEventActorLinkService auditEventActorLinkService
        ) {
            return new FailingRecordAuditEventUseCase(auditEventService, auditEventActorLinkService);
        }
    }

    static class FailingContentService extends ContentService {

        private final AtomicBoolean failNextPublish = new AtomicBoolean();
        private final AtomicReference<PublicationTargetLookupGate> publicationTargetLookupGate = new AtomicReference<>();

        FailingContentService(ContentRepository contentRepository) {
            super(contentRepository);
        }

        @Override
        public Content publish(Content content) {
            Content publishedContent = super.publish(content);
            if (failNextPublish.compareAndSet(true, false)) {
                throw new IllegalStateException(FailurePoint.CONTENT_STATE.failureMessage());
            }
            return publishedContent;
        }

        @Override
        public Optional<Content> findApprovedPublicationTargetForUpdate(Long contentId) {
            PublicationTargetLookupGate gate = publicationTargetLookupGate.get();
            if (gate != null) {
                gate.awaitRelease();
            }
            return super.findApprovedPublicationTargetForUpdate(contentId);
        }

        void blockPublicationTargetLookups(int expectedLookupCount) {
            if (!publicationTargetLookupGate.compareAndSet(
                null,
                new PublicationTargetLookupGate(expectedLookupCount)
            )) {
                throw new IllegalStateException("publication target lookup gate is already configured");
            }
        }

        void awaitPublicationTargetLookups() {
            PublicationTargetLookupGate gate = publicationTargetLookupGate.get();
            if (gate == null) {
                throw new IllegalStateException("publication target lookup gate must be configured");
            }
            gate.awaitReached();
        }

        void releasePublicationTargetLookups() {
            PublicationTargetLookupGate gate = publicationTargetLookupGate.get();
            if (gate == null) {
                throw new IllegalStateException("publication target lookup gate must be configured");
            }
            gate.release();
        }

        void failNextPublish() {
            failNextPublish.set(true);
        }

        void resetFailureInjection() {
            failNextPublish.set(false);
            publicationTargetLookupGate.set(null);
        }
    }

    static class PublicationTargetLookupGate {

        private final CountDownLatch reached;
        private final CountDownLatch release = new CountDownLatch(1);

        PublicationTargetLookupGate(int expectedLookupCount) {
            reached = new CountDownLatch(expectedLookupCount);
        }

        void awaitReached() {
            await(reached);
        }

        void awaitRelease() {
            reached.countDown();
            await(release);
        }

        void release() {
            release.countDown();
        }
    }

    static class MutableTestClock extends Clock {

        private Clock delegate = Clock.systemUTC();

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }

        void setInstant(Instant instant) {
            delegate = Clock.fixed(instant, ZoneOffset.UTC);
        }

        void reset() {
            delegate = Clock.systemUTC();
        }
    }

    static class FailingContentLogService extends ContentLogService {

        private final AtomicBoolean failNextPublishedLog = new AtomicBoolean();

        FailingContentLogService(ContentLogRepository contentLogRepository) {
            super(contentLogRepository);
        }

        @Override
        public ContentLog recordPublished(Content content, Instant publishedAt) {
            ContentLog contentLog = super.recordPublished(content, publishedAt);
            if (failNextPublishedLog.compareAndSet(true, false)) {
                throw new IllegalStateException(FailurePoint.CONTENT_LOG.failureMessage());
            }
            return contentLog;
        }

        void failNextPublishedLog() {
            failNextPublishedLog.set(true);
        }

        void resetFailureInjection() {
            failNextPublishedLog.set(false);
        }
    }

    static class FailingRecordAuditEventUseCase extends RecordAuditEventUseCase {

        private final AtomicBoolean failNextRecord = new AtomicBoolean();

        FailingRecordAuditEventUseCase(
            AuditEventService auditEventService,
            AuditEventActorLinkService auditEventActorLinkService
        ) {
            super(auditEventService, auditEventActorLinkService);
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public AuditEvent record(AuditEventCommand command) {
            AuditEvent auditEvent = super.record(command);
            if (failNextRecord.compareAndSet(true, false)) {
                throw new IllegalStateException(FailurePoint.SUCCESS_AUDIT.failureMessage());
            }
            return auditEvent;
        }

        void failNextRecord() {
            failNextRecord.set(true);
        }

        void resetFailureInjection() {
            failNextRecord.set(false);
        }
    }

    private enum FailurePoint {
        CONTENT_STATE("content state failure"),
        CONTENT_LOG("content log failure"),
        SUCCESS_AUDIT("success audit failure");

        private final String failureMessage;

        FailurePoint(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        private String failureMessage() {
            return failureMessage;
        }
    }

    private record Fixture(Long adminId, Long operatorId, Long contentId) {
    }

}
