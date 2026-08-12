package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionProgressRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Import(RecordMissionProgressUseCaseMySqlTest.FailureTestConfiguration.class)
class RecordMissionProgressUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2037-12-31T00:00:00Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2037-09-30T14:59:59Z");
    private static final Instant ENDED_MISSION_ENDS_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-08-11T00:00:00Z");

    private final RecordMissionProgressUseCase recordMissionProgressUseCase;
    private final EndMissionsUseCase endMissionsUseCase;
    private final MissionParticipationRepository missionParticipationRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionRepository missionRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final VisitRepository visitRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private MissionService missionService;

    @Autowired
    RecordMissionProgressUseCaseMySqlTest(
        RecordMissionProgressUseCase recordMissionProgressUseCase,
        EndMissionsUseCase endMissionsUseCase,
        MissionParticipationRepository missionParticipationRepository,
        MissionProgressRepository missionProgressRepository,
        MissionRepository missionRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AppUserRepository appUserRepository,
        VisitRepository visitRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.recordMissionProgressUseCase = recordMissionProgressUseCase;
        this.endMissionsUseCase = endMissionsUseCase;
        this.missionParticipationRepository = missionParticipationRepository;
        this.missionProgressRepository = missionProgressRepository;
        this.missionRepository = missionRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.visitRepository = visitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @AfterEach
    void clearFailure() {
        ControlledMissionParticipationService.clearFailure();
    }

    @Test
    @Timeout(20)
    void 동일방문을동시에처리하면참여별근거한건과완료로수렴한다() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.VISIT_COUNT, 1, 1, 1);

        runConcurrently(fixture.visitIds().getFirst(), fixture.visitIds().getFirst());

        Long participationId = fixture.participationIds().getFirst();
        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(
            participationId
        )).isOne();
        assertThat(findParticipation(participationId).getStatus())
            .isEqualTo(MissionParticipationStatus.COMPLETED);
        assertThat(findParticipation(participationId).getCompletedAt()).isNotNull();
    }

    @Test
    @Timeout(20)
    void 같은콘텐츠의서로다른방문을동시에처리하면콘텐츠근거한건으로수렴한다() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.CONTENT_SET, null, 1, 2);

        runConcurrently(fixture.visitIds().get(0), fixture.visitIds().get(1));

        Long participationId = fixture.participationIds().getFirst();
        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(
            participationId
        )).isOne();
        assertThat(findParticipation(participationId).getStatus())
            .isEqualTo(MissionParticipationStatus.COMPLETED);
    }

    @Test
    void 한참여실패는다른참여커밋을막지않고실패참여는재호출할수있다() {
        Fixture fixture = createFixture(MissionConditionType.VISIT_COUNT, 1, 2, 1);
        Long failedParticipationId = fixture.participationIds().getFirst();
        Long committedParticipationId = fixture.participationIds().get(1);
        ControlledMissionParticipationService.failOn(failedParticipationId);

        recordMissionProgressUseCase.record(fixture.visitIds().getFirst(), UUID.randomUUID());

        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(
            failedParticipationId
        )).isZero();
        assertThat(findParticipation(failedParticipationId).getStatus())
            .isEqualTo(MissionParticipationStatus.IN_PROGRESS);
        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(
            committedParticipationId
        )).isOne();
        assertThat(findParticipation(committedParticipationId).getStatus())
            .isEqualTo(MissionParticipationStatus.COMPLETED);

        ControlledMissionParticipationService.clearFailure();
        recordMissionProgressUseCase.record(fixture.visitIds().getFirst(), UUID.randomUUID());

        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(
            failedParticipationId
        )).isOne();
        assertThat(findParticipation(failedParticipationId).getStatus())
            .isEqualTo(MissionParticipationStatus.COMPLETED);
    }

    @Test
    @Timeout(20)
    void 자동종료가먼저잠그면진행도반영은근거와완료를추가하지않는다() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            1,
            1,
            1,
            ENDED_MISSION_ENDS_AT
        );
        Long missionId = fixture.missionIds().getFirst();
        Long participationId = fixture.participationIds().getFirst();
        CountDownLatch endMissionLocked = new CountDownLatch(1);
        CountDownLatch progressReachedMissionLock = new CountDownLatch(1);
        CountDownLatch releaseEnd = new CountDownLatch(1);
        MissionService target = AopTestUtils.getTargetObject(missionService);
        doAnswer(invocation -> {
            Mission mission = (Mission) invocation.callRealMethod();
            endMissionLocked.countDown();
            if (!releaseEnd.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("자동 종료 잠금 해제를 기다리는 시간이 초과되었습니다.");
            }
            return mission;
        }).when(target).findForUpdate(missionId);
        doAnswer(invocation -> {
            progressReachedMissionLock.countDown();
            return invocation.callRealMethod();
        }).when(target).findMissionForParticipationUpdate(missionId);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<EndMissionSystemResult> endResult = executor.submit(() -> endMissionsUseCase.endBySystem(
                missionId,
                UUID.randomUUID()
            ));
            assertThat(endMissionLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> progressResult = executor.submit(() -> recordMissionProgressUseCase.record(
                fixture.visitIds().getFirst(),
                UUID.randomUUID()
            ));
            assertThat(progressReachedMissionLock.await(5, TimeUnit.SECONDS)).isTrue();
            releaseEnd.countDown();

            assertThat(endResult.get(5, TimeUnit.SECONDS).status()).isEqualTo(EndMissionSystemResult.Status.ENDED);
            progressResult.get(5, TimeUnit.SECONDS);
        } finally {
            releaseEnd.countDown();
        }

        assertThat(missionRepository.findById(missionId).orElseThrow().getStatus()).isEqualTo(MissionStatus.ENDED);
        assertThat(findParticipation(participationId).getStatus()).isEqualTo(MissionParticipationStatus.ENDED_INCOMPLETE);
        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(participationId)).isZero();
    }

    private void runConcurrently(
        Long firstVisitId,
        Long secondVisitId
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> recordAfterStart(firstVisitId, ready, start));
            Future<?> second = executor.submit(() -> recordAfterStart(secondVisitId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
    }

    private void recordAfterStart(
        Long visitId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent mission progress start timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent mission progress was interrupted", exception);
        }
        recordMissionProgressUseCase.record(visitId, UUID.randomUUID());
    }

    private MissionParticipation findParticipation(Long participationId) {
        return missionParticipationRepository.findById(participationId).orElseThrow();
    }

    private Fixture createFixture(
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        int missionCount,
        int visitCount
    ) {
        return createFixture(conditionType, requiredVisitCount, missionCount, visitCount, MISSION_ENDS_AT);
    }

    private Fixture createFixture(
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        int missionCount,
        int visitCount,
        Instant missionEndsAt
    ) {
        return transactionTemplate.execute(status -> {
            Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
            AppUser operator = saveUser("operator@example.com", "콘텐츠 운영자");
            AppUser visitor = saveUser("visitor@example.com", "방문자");
            Content content = contentRepository.saveAndFlush(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "김해 가야 문화 체험",
                "김해 가야 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                ISSUE_STARTS_AT
            ));
            CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                content,
                region,
                "미션 완료 보상",
                "미션 완료 시 지급하는 쿠폰입니다.",
                CouponIssuanceType.MISSION_REWARD,
                3_000,
                10_000,
                30,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                100L
            ));

            List<Long> missionIds = new ArrayList<>();
            List<Long> participationIds = new ArrayList<>();
            for (int missionIndex = 0; missionIndex < missionCount; missionIndex++) {
                Mission mission = new Mission(
                    region,
                    conditionType,
                    requiredVisitCount,
                    rewardCouponPolicy,
                    missionEndsAt.plusSeconds(missionIndex)
                );
                if (conditionType == MissionConditionType.CONTENT_SET) {
                    mission.addTargetContent(content);
                }
                mission.submitForReview();
                mission.approve(ISSUE_STARTS_AT);
                missionRepository.saveAndFlush(mission);
                missionIds.add(mission.getMissionId());
                MissionParticipation participation = missionParticipationRepository.saveAndFlush(
                    new MissionParticipation(mission, visitor, JOINED_AT)
                );
                participationIds.add(participation.getMissionParticipationId());
            }

            List<Long> visitIds = new ArrayList<>();
            for (int visitIndex = 0; visitIndex < visitCount; visitIndex++) {
                visitIds.add(saveVisit(region, content, visitor, operator, visitIndex).getVisitId());
            }
            return new Fixture(List.copyOf(missionIds), List.copyOf(participationIds), List.copyOf(visitIds));
        });
    }

    private Visit saveVisit(
        Region region,
        Content content,
        AppUser visitor,
        AppUser operator,
        int index
    ) {
        AppUser reviewer = saveUser("reviewer-" + index + "@example.com", "회차 검토자");
        ContentSession contentSession = new ContentSession(
            content,
            region,
            CHECKED_AT.plusSeconds(3_600L * index),
            CHECKED_AT.plusSeconds(3_600L * index + 1_800),
            CHECKED_AT.plusSeconds(3_600L * index - 600),
            CHECKED_AT.plusSeconds(3_600L * index + 1_200),
            10
        );
        contentSession.approve(reviewer, ISSUE_STARTS_AT);
        contentSessionRepository.saveAndFlush(contentSession);
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            ISSUE_STARTS_AT,
            ISSUE_STARTS_AT,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-618-" + index,
            "qr-618-" + index,
            region,
            capacityHold,
            contentSession,
            visitor,
            ReservationStatus.CONFIRMED,
            ISSUE_STARTS_AT,
            null,
            null,
            null,
            null
        ));
        return visitRepository.saveAndFlush(new Visit(
            region,
            reservation,
            visitor,
            content,
            contentSession,
            operator,
            CheckinMethod.QR,
            CHECKED_AT.plusSeconds(index)
        ));
    }

    private AppUser saveUser(
        String loginIdentifier,
        String name
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record Fixture(
        List<Long> missionIds,
        List<Long> participationIds,
        List<Long> visitIds
    ) {
    }

    @TestConfiguration
    static class FailureTestConfiguration {

        @Bean
        @Primary
        MissionParticipationService controlledMissionParticipationService(
            MissionParticipationRepository missionParticipationRepository
        ) {
            return new ControlledMissionParticipationService(missionParticipationRepository);
        }
    }

    static class ControlledMissionParticipationService extends MissionParticipationService {

        private static volatile Long failedParticipationId;

        ControlledMissionParticipationService(MissionParticipationRepository missionParticipationRepository) {
            super(missionParticipationRepository);
        }

        static void failOn(Long participationId) {
            failedParticipationId = participationId;
        }

        static void clearFailure() {
            failedParticipationId = null;
        }

        @Override
        public Optional<MissionParticipation> findByIdForProgressUpdate(Long participationId) {
            if (participationId.equals(failedParticipationId)) {
                throw new IllegalStateException("controlled mission participation failure");
            }
            return super.findByIdForProgressUpdate(participationId);
        }
    }
}
