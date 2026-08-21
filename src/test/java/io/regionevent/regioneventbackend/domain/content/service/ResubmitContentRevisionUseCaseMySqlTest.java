package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionRequest;
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

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResubmitContentRevisionUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ResubmitContentRevisionUseCase resubmitContentRevisionUseCase;
    private final CreateContentRevisionUseCase createContentRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentLogRepository contentLogRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ResubmitContentRevisionUseCaseMySqlTest(
        ResubmitContentRevisionUseCase resubmitContentRevisionUseCase,
        CreateContentRevisionUseCase createContentRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentLogRepository contentLogRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.resubmitContentRevisionUseCase = resubmitContentRevisionUseCase;
        this.createContentRevisionUseCase = createContentRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentLogRepository = contentLogRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 재제출_성공_뒤_반복하면_활성_수정본을_추가하지_않고_상태_충돌을_반환한다() {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);

        ResubmitContentRevisionResult result = resubmitContentRevisionUseCase.resubmit(
            fixture.operatorId(),
            fixture.sourceRevisionId()
        );

        assertThat(result.status()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertContentStateConflict(() -> resubmitContentRevisionUseCase.resubmit(
            fixture.operatorId(),
            fixture.sourceRevisionId()
        ));
        assertSingleActiveRevision(fixture.contentId(), 2);
    }

    @Test
    @Timeout(15)
    void 같은_반려_수정본을_동시에_재제출하면_하나만_성공한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);

        List<ActionAttempt> attempts = race(
            () -> resubmitContentRevisionUseCase.resubmit(
                fixture.operatorId(),
                fixture.sourceRevisionId()
            ),
            () -> resubmitContentRevisionUseCase.resubmit(
                fixture.operatorId(),
                fixture.sourceRevisionId()
            )
        );

        assertOneSuccessAndOneConflict(attempts);
        assertSingleActiveRevision(fixture.contentId(), 2);
    }

    @Test
    @Timeout(15)
    void 재제출과_일반_수정본_생성이_경합하면_하나만_성공한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);

        List<ActionAttempt> attempts = race(
            () -> resubmitContentRevisionUseCase.resubmit(
                fixture.operatorId(),
                fixture.sourceRevisionId()
            ),
            () -> createContentRevisionUseCase.createRevision(
                fixture.operatorId(),
                fixture.contentId(),
                createRevisionRequest(),
                UUID.randomUUID().toString()
            )
        );

        assertOneSuccessAndOneConflict(attempts);
        assertSingleActiveRevision(fixture.contentId(), 2);
    }

    @Test
    void 공개전_반려_수정본_재제출은_상태_로그와_감사_이벤트를_중복_기록하지_않는다() {
        Fixture fixture = createFixture(
            ContentStatus.PENDING,
            Instant.parse("2026-08-25T01:00:00Z"),
            true
        );
        long contentLogCount = contentLogRepository.count();
        long auditEventCount = auditEventRepository.count();

        resubmitContentRevisionUseCase.resubmit(fixture.operatorId(), fixture.sourceRevisionId());

        assertThat(contentLogRepository.count()).isEqualTo(contentLogCount);
        assertThat(auditEventRepository.count()).isEqualTo(auditEventCount);
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING));
    }

    @Test
    void 후보_이미지_정합성이_깨지면_새_수정본을_저장하지_않는다() {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        transactionTemplate.executeWithoutResult(status -> {
            ContentRevision sourceRevision = contentRevisionRepository.findById(
                fixture.sourceRevisionId()
            ).orElseThrow();
            sourceRevision.getCandidateImageObject().markDeletePending();
            imageObjectRepository.flush();
        });

        assertThatThrownBy(() -> resubmitContentRevisionUseCase.resubmit(
            fixture.operatorId(),
            fixture.sourceRevisionId()
        )).isInstanceOf(IllegalStateException.class);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(contentRevisionRepository.findMaxRevisionNoByContentId(fixture.contentId()))
                .isEqualTo(1);
            assertThat(contentRevisionRepository.findById(fixture.sourceRevisionId()))
                .hasValueSatisfying(sourceRevision -> {
                    assertThat(sourceRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
                    assertThat(sourceRevision.getReviewReason()).isEqualTo("후보를 보완해 주세요.");
                });
        });
    }

    private List<ActionAttempt> race(ConcurrentAction first, ConcurrentAction second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ActionAttempt> firstAttempt = executorService.submit(
                () -> executeAfterStart(first, ready, start)
            );
            Future<ActionAttempt> secondAttempt = executorService.submit(
                () -> executeAfterStart(second, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                firstAttempt.get(10, TimeUnit.SECONDS),
                secondAttempt.get(10, TimeUnit.SECONDS)
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

    private Fixture createFixture(
        ContentStatus contentStatus,
        Instant candidatePublishAt,
        boolean savePrePublicationHistory
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.parse("2026-08-21T01:00:00Z");
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser operator = saveUser("operator-" + suffix + "@example.com");
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                contentStatus,
                "원본 제목",
                "원본 설명",
                "원본 장소",
                "매일 10:00~18:00",
                "055-123-4567",
                "원본 주의사항",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소",
                10_000,
                now.minusSeconds(86_400)
            ));
            ImageObject representativeImage = saveLinkedImage(region, operator, "original-" + suffix, now);
            content.assignRepresentativeImage(representativeImage, now);
            contentRepository.flush();
            ImageObject candidateImage = saveLinkedImage(region, operator, "candidate-" + suffix, now);
            ContentRevision sourceRevision = new ContentRevision(
                content,
                1,
                content.getVersionNo(),
                operator,
                ContentRevisionStatus.EDIT_REJECTED,
                "후보 제목",
                "후보 설명",
                "후보 장소",
                "후보 운영 시간",
                "055-9876-5432",
                "후보 주의사항",
                "만 8세 이상",
                "운동화",
                "후보 취소 정책",
                20_000,
                candidatePublishAt,
                now.minusSeconds(7_200),
                now.minusSeconds(3_600),
                operator,
                "후보를 보완해 주세요.",
                null,
                null,
                null
            );
            sourceRevision.assignCandidateImage(candidateImage, now.minusSeconds(7_200));
            contentRevisionRepository.saveAndFlush(sourceRevision);
            if (savePrePublicationHistory) {
                contentLogRepository.save(new ContentLog(
                    content,
                    operator,
                    ContentLogStatus.APPROVED,
                    null,
                    now.minusSeconds(10_800)
                ));
                contentLogRepository.saveAndFlush(new ContentLog(
                    content,
                    operator,
                    ContentLogStatus.PENDING,
                    null,
                    now.minusSeconds(7_200)
                ));
            }
            return new Fixture(operator.getUserId(), content.getContentId(), sourceRevision.getContentRevisionId());
        });
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.save(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private ImageObject saveLinkedImage(
        Region region,
        AppUser operator,
        String suffix,
        Instant linkedAt
    ) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/resubmit-" + suffix + ".webp",
            operator,
            region,
            "image/webp",
            1L,
            "checksum-" + suffix,
            linkedAt.plusSeconds(3_600)
        );
        imageObject.markLinked(linkedAt);
        return imageObjectRepository.save(imageObject);
    }

    private static CreateContentRevisionRequest createRevisionRequest() {
        return new CreateContentRevisionRequest(
            "일반 생성 후보 제목",
            "일반 생성 후보 설명",
            "일반 생성 후보 장소",
            "일반 생성 후보 운영 시간",
            "055-1111-2222",
            "일반 생성 후보 주의사항",
            "만 9세 이상",
            "모자",
            "일반 생성 후보 취소 정책",
            30_000L,
            null,
            null
        );
    }

    private void assertSingleActiveRevision(Long contentId, int expectedRevisionCount) {
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(contentRevisionRepository.findAll())
                .filteredOn(revision -> contentId.equals(revision.getContent().getContentId()))
                .hasSize(expectedRevisionCount)
                .filteredOn(revision -> revision.getStatus() == ContentRevisionStatus.EDIT_REQUESTED)
                .hasSize(1);
        });
    }

    private static void assertOneSuccessAndOneConflict(List<ActionAttempt> attempts) {
        assertThat(attempts).filteredOn(ActionAttempt::isSuccessful).hasSize(1);
        assertThat(attempts)
            .filteredOn(attempt -> attempt.errorCode() == ErrorCode.CONTENT_STATE_CONFLICT)
            .hasSize(1);
    }

    private static void assertContentStateConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent resubmission did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent resubmission was interrupted", exception);
        }
    }

    @FunctionalInterface
    private interface ConcurrentAction {

        void execute();
    }

    private record Fixture(
        Long operatorId,
        Long contentId,
        Long sourceRevisionId
    ) {
    }

    private record ActionAttempt(ErrorCode errorCode) {

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
