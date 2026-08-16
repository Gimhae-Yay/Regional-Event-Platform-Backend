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
import java.util.stream.Stream;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
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

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentRevisionTerminationConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final int SESSION_CAPACITY = 10;

    private final EndContentReservationsUseCase endContentReservationsUseCase;
    private final SuspendContentUseCase suspendContentUseCase;
    private final ApproveContentRevisionUseCase approveContentRevisionUseCase;
    private final RejectContentRevisionUseCase rejectContentRevisionUseCase;
    private final WithdrawContentRevisionUseCase withdrawContentRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ContentRevisionTerminationConcurrencyMySqlTest(
        EndContentReservationsUseCase endContentReservationsUseCase,
        SuspendContentUseCase suspendContentUseCase,
        ApproveContentRevisionUseCase approveContentRevisionUseCase,
        RejectContentRevisionUseCase rejectContentRevisionUseCase,
        WithdrawContentRevisionUseCase withdrawContentRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentSessionRepository contentSessionRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.endContentReservationsUseCase = endContentReservationsUseCase;
        this.suspendContentUseCase = suspendContentUseCase;
        this.approveContentRevisionUseCase = approveContentRevisionUseCase;
        this.rejectContentRevisionUseCase = rejectContentRevisionUseCase;
        this.withdrawContentRevisionUseCase = withdrawContentRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @ParameterizedTest(name = "[{index}] {0}과 {1} 경합")
    @MethodSource("terminationAndReviewCommands")
    @Timeout(15)
    void 콘텐츠_종결과_수정본_터미널_전이가_경합해도_수정본_감사는_한번만_기록된다(
        TerminationCommand terminationCommand,
        RevisionCommand revisionCommand
    ) throws Exception {
        Fixture fixture = createFixture();

        RaceResult raceResult = race(
            () -> terminate(fixture, terminationCommand),
            () -> review(fixture, revisionCommand)
        );

        assertThat(raceResult.terminationAttempt().isSuccessful()).isTrue();
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content ->
                assertThat(content.getStatus()).isEqualTo(terminationCommand.contentStatus())
            );

        ContentRevision revision = contentRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        assertRevisionTransition(raceResult.revisionAttempt(), revision, terminationCommand, revisionCommand);
        assertSingleRevisionAudit(fixture, revision, terminationCommand);
    }

    private static Stream<Arguments> terminationAndReviewCommands() {
        return Stream.of(
            Arguments.of(TerminationCommand.END, RevisionCommand.APPROVE),
            Arguments.of(TerminationCommand.END, RevisionCommand.REJECT),
            Arguments.of(TerminationCommand.END, RevisionCommand.WITHDRAW),
            Arguments.of(TerminationCommand.SUSPEND, RevisionCommand.APPROVE),
            Arguments.of(TerminationCommand.SUSPEND, RevisionCommand.REJECT),
            Arguments.of(TerminationCommand.SUSPEND, RevisionCommand.WITHDRAW)
        );
    }

    private RaceResult race(
        ConcurrentAction terminationAction,
        ConcurrentAction revisionAction
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ActionAttempt> termination = executorService.submit(
                () -> executeAfterStart(terminationAction, ready, start)
            );
            Future<ActionAttempt> revision = executorService.submit(
                () -> executeAfterStart(revisionAction, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return new RaceResult(
                termination.get(10, TimeUnit.SECONDS),
                revision.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private ActionAttempt executeAfterStart(
        ConcurrentAction action,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);

        try {
            action.execute();
            return ActionAttempt.success();
        } catch (BusinessException exception) {
            return ActionAttempt.failure(exception.getErrorCode());
        }
    }

    private void terminate(Fixture fixture, TerminationCommand command) {
        if (command == TerminationCommand.END) {
            endContentReservationsUseCase.end(
                fixture.adminId(),
                fixture.contentId(),
                UUID.randomUUID()
            );
            return;
        }
        suspendContentUseCase.suspend(
            fixture.adminId(),
            fixture.contentId(),
            "운영 중단 경합 검증",
            UUID.randomUUID()
        );
    }

    private void review(Fixture fixture, RevisionCommand command) {
        switch (command) {
            case APPROVE -> approveContentRevisionUseCase.approve(
                fixture.adminId(),
                fixture.revisionId(),
                UUID.randomUUID()
            );
            case REJECT -> rejectContentRevisionUseCase.reject(
                fixture.adminId(),
                fixture.revisionId(),
                "수정본 반려 경합 검증",
                UUID.randomUUID()
            );
            case WITHDRAW -> withdrawContentRevisionUseCase.withdraw(
                fixture.operatorId(),
                fixture.revisionId(),
                "수정본 철회 경합 검증",
                UUID.randomUUID()
            );
        }
    }

    private void assertRevisionTransition(
        ActionAttempt revisionAttempt,
        ContentRevision revision,
        TerminationCommand terminationCommand,
        RevisionCommand revisionCommand
    ) {
        if (revisionAttempt.isSuccessful()) {
            assertThat(revision.getStatus()).isEqualTo(revisionCommand.terminalStatus());
            return;
        }

        assertThat(revisionAttempt.errorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT);
        assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_INVALIDATED);
        assertThat(revision.getInvalidationReason()).isEqualTo(terminationCommand.invalidationReason());
    }

    private void assertSingleRevisionAudit(
        Fixture fixture,
        ContentRevision revision,
        TerminationCommand terminationCommand
    ) {
        List<AuditEvent> auditEvents = auditEventRepository.findAll().stream()
            .filter(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .toList();
        assertThat(auditEvents)
            .filteredOn(auditEvent -> ContentRevisionStatus.EDIT_REQUESTED.name()
                .equals(auditEvent.getPreviousState()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getNextState()).isEqualTo(revision.getStatus().name());
                if (revision.getStatus() == ContentRevisionStatus.EDIT_INVALIDATED) {
                    assertThat(auditEvent.getReasonCode()).isEqualTo(terminationCommand.invalidationReason().name());
                } else {
                    assertThat(auditEvent.getReasonCode()).isNull();
                }
            });
        assertThat(auditEvents).hasSize(2);
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "원본 콘텐츠 제목",
                "원본 콘텐츠 설명",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-123-4567",
                "안전 수칙",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소",
                now.minusSeconds(86_400)
            ));
            saveCompletedSession(content, region, admin, now);
            ImageObject candidateImage = saveLinkedCandidateImage(region, operator, suffix, now);
            ContentRevision revision = new ContentRevision(
                content,
                1,
                content.getVersionNo(),
                operator,
                ContentRevisionStatus.EDIT_REQUESTED,
                "수정 콘텐츠 제목",
                "수정 콘텐츠 설명",
                "수정 장소",
                "수정 운영 시간",
                "055-9876-5432",
                "수정 안전 수칙",
                "만 8세 이상",
                "운동화",
                "수정 취소 정책",
                0,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                null
            );
            revision.assignCandidateImage(candidateImage, now);
            contentRevisionRepository.saveAndFlush(revision);

            return new Fixture(
                admin.getUserId(),
                operator.getUserId(),
                content.getContentId(),
                revision.getContentRevisionId()
            );
        });
    }

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void saveCompletedSession(
        Content content,
        Region region,
        AppUser admin,
        Instant now
    ) {
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(10_800),
            now.minusSeconds(7_200),
            now.minusSeconds(10_800),
            now.minusSeconds(7_300),
            SESSION_CAPACITY
        );
        session.approve(admin, now.minusSeconds(14_400));
        session.complete(now.minusSeconds(7_200));
        contentSessionRepository.save(session);
    }

    private ImageObject saveLinkedCandidateImage(
        Region region,
        AppUser operator,
        String suffix,
        Instant now
    ) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/revision-termination-" + suffix + ".webp",
            operator,
            region,
            "image/webp",
            1L,
            "checksum-" + suffix,
            now.plusSeconds(3_600)
        );
        imageObject.markLinked(now);
        return imageObjectRepository.save(imageObject);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent transitions did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent transitions were interrupted", exception);
        }
    }

    private enum TerminationCommand {
        END(ContentStatus.ENDED, ContentRevisionInvalidationReason.CONTENT_ENDED),
        SUSPEND(ContentStatus.SUSPENDED, ContentRevisionInvalidationReason.CONTENT_SUSPENDED);

        private final ContentStatus contentStatus;
        private final ContentRevisionInvalidationReason invalidationReason;

        TerminationCommand(
            ContentStatus contentStatus,
            ContentRevisionInvalidationReason invalidationReason
        ) {
            this.contentStatus = contentStatus;
            this.invalidationReason = invalidationReason;
        }

        private ContentStatus contentStatus() {
            return contentStatus;
        }

        private ContentRevisionInvalidationReason invalidationReason() {
            return invalidationReason;
        }
    }

    private enum RevisionCommand {
        APPROVE(ContentRevisionStatus.EDIT_APPROVED),
        REJECT(ContentRevisionStatus.EDIT_REJECTED),
        WITHDRAW(ContentRevisionStatus.EDIT_WITHDRAWN);

        private final ContentRevisionStatus terminalStatus;

        RevisionCommand(ContentRevisionStatus terminalStatus) {
            this.terminalStatus = terminalStatus;
        }

        private ContentRevisionStatus terminalStatus() {
            return terminalStatus;
        }
    }

    @FunctionalInterface
    private interface ConcurrentAction {

        void execute();
    }

    private record Fixture(
        Long adminId,
        Long operatorId,
        Long contentId,
        Long revisionId
    ) {
    }

    private record RaceResult(
        ActionAttempt terminationAttempt,
        ActionAttempt revisionAttempt
    ) {
    }

    private record ActionAttempt(
        ErrorCode errorCode
    ) {

        private static ActionAttempt success() {
            return new ActionAttempt(null);
        }

        private static ActionAttempt failure(ErrorCode errorCode) {
            return new ActionAttempt(errorCode);
        }

        private boolean isSuccessful() {
            return errorCode == null;
        }
    }
}
