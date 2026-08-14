package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampEarnRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookProgressRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRewardGrantRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
class RecordStampbookProgressUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2037-12-31T00:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-08-11T00:00:00Z");

    private final RecordStampbookProgressUseCase recordStampbookProgressUseCase;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final StampbookProgressRepository stampbookProgressRepository;
    private final StampEarnRepository stampEarnRepository;
    private final StampbookRewardGrantRepository stampbookRewardGrantRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final VisitRepository visitRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RecordStampbookProgressUseCaseMySqlTest(
        RecordStampbookProgressUseCase recordStampbookProgressUseCase,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        StampbookProgressRepository stampbookProgressRepository,
        StampEarnRepository stampEarnRepository,
        StampbookRewardGrantRepository stampbookRewardGrantRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AppUserRepository appUserRepository,
        VisitRepository visitRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.recordStampbookProgressUseCase = recordStampbookProgressUseCase;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.stampbookProgressRepository = stampbookProgressRepository;
        this.stampEarnRepository = stampEarnRepository;
        this.stampbookRewardGrantRepository = stampbookRewardGrantRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.visitRepository = visitRepository;
        this.jdbcTemplate = jdbcTemplate;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 첫적립으로모든대상콘텐츠를채우면진행완료와단일보상근거를함께생성한다() {
        Fixture fixture = createFixture(StampbookStatus.PUBLISHED, 1, 0);

        recordStampbookProgressUseCase.record(fixture.visitIds().getFirst());

        StampbookProgress progress = findProgress(fixture);
        assertThat(stampEarnRepository.countByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isOne();
        assertThat(progress.getStatus()).isEqualTo(StampbookProgressStatus.COMPLETED);
        assertThat(progress.getCompletedAt()).isNotNull();
        assertThat(stampbookRewardGrantRepository.findByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).hasValueSatisfying(grant -> {
            assertThat(grant.getCouponPolicy().getCouponPolicyId())
                .isEqualTo(fixture.rewardCouponPolicyId());
            assertThat(grant.getGrantedAt()).isEqualTo(progress.getCompletedAt());
        });
    }

    @Test
    void 같은콘텐츠재방문은추가스탬프를생성하지않는다() {
        Fixture fixture = createFixture(StampbookStatus.PUBLISHED, 1, 0, 0);

        recordStampbookProgressUseCase.record(fixture.visitIds().get(0));
        recordStampbookProgressUseCase.record(fixture.visitIds().get(1));

        StampbookProgress progress = findProgress(fixture);
        assertThat(stampEarnRepository.countByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isOne();
        assertThat(stampbookRewardGrantRepository.findByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isPresent();
    }

    @Test
    void 같은방문재전달은기존적립결과로수렴한다() {
        Fixture fixture = createFixture(StampbookStatus.PUBLISHED, 1, 0);
        Long visitId = fixture.visitIds().getFirst();

        recordStampbookProgressUseCase.record(visitId);
        recordStampbookProgressUseCase.record(visitId);

        StampbookProgress progress = findProgress(fixture);
        assertThat(stampEarnRepository.countByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isOne();
        assertThat(stampbookRewardGrantRepository.findByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isPresent();
    }

    @Test
    void 미공개스탬프북은유효방문으로도진행을생성하지않는다() {
        Fixture fixture = createFixture(StampbookStatus.DRAFT, 1, 0);

        recordStampbookProgressUseCase.record(fixture.visitIds().getFirst());

        assertThat(stampbookProgressRepository.findByStampbookStampbookIdAndUserUserId(
            fixture.stampbookId(),
            fixture.userId()
        )).isEmpty();
    }

    @Test
    void 종료된스탬프북은유효방문으로도진행을생성하지않는다() {
        Fixture fixture = createFixture(StampbookStatus.ENDED, 1, 0);

        recordStampbookProgressUseCase.record(fixture.visitIds().getFirst());

        assertThat(stampbookProgressRepository.findByStampbookStampbookIdAndUserUserId(
            fixture.stampbookId(),
            fixture.userId()
        )).isEmpty();
    }

    @Test
    @Timeout(20)
    void 서로다른대상콘텐츠방문을동시에적립하면완료와보상근거한건으로수렴한다() throws Exception {
        Fixture fixture = createFixture(StampbookStatus.PUBLISHED, 2, 0, 1);

        runConcurrently(fixture.visitIds().get(0), fixture.visitIds().get(1));

        StampbookProgress progress = findProgress(fixture);
        assertThat(stampEarnRepository.countByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isEqualTo(2);
        assertThat(progress.getStatus()).isEqualTo(StampbookProgressStatus.COMPLETED);
        assertThat(stampbookRewardGrantRepository.findByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isPresent();
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
                throw new IllegalStateException("concurrent stampbook progress start timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent stampbook progress was interrupted", exception);
        }
        recordStampbookProgressUseCase.record(visitId);
    }

    private StampbookProgress findProgress(Fixture fixture) {
        return stampbookProgressRepository.findByStampbookStampbookIdAndUserUserId(
            fixture.stampbookId(),
            fixture.userId()
        ).orElseThrow();
    }

    private Fixture createFixture(
        StampbookStatus stampbookStatus,
        int targetContentCount,
        int... visitTargetIndexes
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("GIMHAE-" + suffix, "김해시", true));
            AppUser operator = saveUser("operator-" + suffix + "@example.com", "콘텐츠 운영자");
            AppUser visitor = saveUser("visitor-" + suffix + "@example.com", "방문자");
            List<Content> targetContents = new ArrayList<>();
            for (int contentIndex = 0; contentIndex < targetContentCount; contentIndex++) {
                targetContents.add(saveContent(region, operator, suffix, contentIndex));
            }
            CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                targetContents.getFirst(),
                region,
                "스탬프북 완료 보상",
                "스탬프북 완료 시 지급하는 쿠폰입니다.",
                CouponIssuanceType.STAMPBOOK_COMPLETION,
                3_000,
                10_000,
                30,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                100L
            ));
            Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(region, rewardCouponPolicy));
            stampbookContentRepository.saveAllAndFlush(targetContents.stream()
                .map(content -> new StampbookContent(stampbook, content))
                .toList());
            updateStampbookStatus(stampbook.getStampbookId(), stampbookStatus);

            List<Long> visitIds = new ArrayList<>();
            for (int visitIndex = 0; visitIndex < visitTargetIndexes.length; visitIndex++) {
                Content targetContent = targetContents.get(visitTargetIndexes[visitIndex]);
                visitIds.add(saveVisit(
                    region,
                    targetContent,
                    visitor,
                    operator,
                    suffix,
                    visitIndex
                ).getVisitId());
            }
            return new Fixture(
                stampbook.getStampbookId(),
                visitor.getUserId(),
                rewardCouponPolicy.getCouponPolicyId(),
                List.copyOf(visitIds)
            );
        });
    }

    private void updateStampbookStatus(
        Long stampbookId,
        StampbookStatus stampbookStatus
    ) {
        if (stampbookStatus == StampbookStatus.DRAFT) {
            return;
        }
        if (stampbookStatus == StampbookStatus.PUBLISHED) {
            jdbcTemplate.update(
                "UPDATE stampbook SET status = ?, published_at = ? WHERE stampbook_id = ?",
                stampbookStatus.name(),
                Timestamp.from(PUBLISHED_AT),
                stampbookId
            );
            return;
        }
        jdbcTemplate.update(
            "UPDATE stampbook SET status = ?, published_at = ?, ended_at = ? WHERE stampbook_id = ?",
            stampbookStatus.name(),
            Timestamp.from(PUBLISHED_AT),
            Timestamp.from(ENDED_AT),
            stampbookId
        );
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String suffix,
        int contentIndex
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험 " + contentIndex,
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            ISSUE_STARTS_AT.plusSeconds(contentIndex)
        ));
    }

    private Visit saveVisit(
        Region region,
        Content content,
        AppUser visitor,
        AppUser operator,
        String suffix,
        int visitIndex
    ) {
        Instant sessionStartsAt = CHECKED_AT.plusSeconds(3_600L * visitIndex);
        ContentSession contentSession = new ContentSession(
            content,
            region,
            sessionStartsAt,
            sessionStartsAt.plusSeconds(1_800),
            sessionStartsAt.minusSeconds(600),
            sessionStartsAt.plusSeconds(1_200),
            10
        );
        contentSession.approve(operator, ISSUE_STARTS_AT);
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
            "R-764-" + suffix + "-" + visitIndex,
            "qr-764-" + suffix + "-" + visitIndex,
            region,
            capacityHold,
            contentSession,
            visitor,
            ReservationStatus.CHECKED_IN,
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
            CHECKED_AT.plusSeconds(visitIndex)
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
        Long stampbookId,
        Long userId,
        Long rewardCouponPolicyId,
        List<Long> visitIds
    ) {
    }
}
