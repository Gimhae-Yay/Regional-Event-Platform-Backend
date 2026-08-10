package io.regionevent.regioneventbackend.domain.stampbook.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

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
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class MyStampbookListProjectionRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant FIRST_PUBLISHED_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant SECOND_PUBLISHED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-04T00:00:00Z");
    private static final Instant FIRST_EARNED_AT = Instant.parse("2026-08-02T01:00:00Z");
    private static final Instant LATEST_EARNED_AT = Instant.parse("2026-08-03T01:00:00Z");

    private final StampbookRepository stampbookRepository;
    private final StampbookProgressRepository stampbookProgressRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final StampEarnRepository stampEarnRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    MyStampbookListProjectionRepositoryTest(
        StampbookRepository stampbookRepository,
        StampbookProgressRepository stampbookProgressRepository,
        StampbookContentRepository stampbookContentRepository,
        StampEarnRepository stampEarnRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.stampbookRepository = stampbookRepository;
        this.stampbookProgressRepository = stampbookProgressRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.stampEarnRepository = stampEarnRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 공개_스탬프북과_본인_종료_이력만_고정_정렬로_조회하고_진행도를_변경하지_않는다() {
        Region region = saveRegion();
        Content targetContent = saveContent(region, "target-content");
        Content secondTargetContent = saveContent(region, "second-target-content");
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy(targetContent, region);
        AppUser viewer = saveUser("viewer@example.com");
        AppUser otherUser = saveUser("other@example.com");

        Stampbook sameTimeFirst = saveStampbook(
            region,
            rewardCouponPolicy,
            targetContent,
            secondTargetContent
        );
        updateStampbookStatus(sameTimeFirst, StampbookStatus.PUBLISHED, FIRST_PUBLISHED_AT, null);

        Stampbook sameTimeSecond = saveStampbook(
            region,
            rewardCouponPolicy,
            targetContent,
            secondTargetContent
        );
        updateStampbookStatus(sameTimeSecond, StampbookStatus.PUBLISHED, FIRST_PUBLISHED_AT, null);
        stampbookProgressRepository.saveAndFlush(new StampbookProgress(sameTimeSecond, otherUser));

        Stampbook ownedEnded = saveStampbook(
            region,
            rewardCouponPolicy,
            targetContent,
            secondTargetContent
        );
        updateStampbookStatus(
            ownedEnded,
            StampbookStatus.ENDED,
            SECOND_PUBLISHED_AT,
            Instant.parse("2026-08-05T00:00:00Z")
        );
        StampbookProgress completedProgress = new StampbookProgress(ownedEnded, viewer);
        completedProgress.complete(COMPLETED_AT);
        stampbookProgressRepository.saveAndFlush(completedProgress);

        Stampbook otherEnded = saveStampbook(
            region,
            rewardCouponPolicy,
            targetContent,
            secondTargetContent
        );
        updateStampbookStatus(
            otherEnded,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:00Z")
        );
        stampbookProgressRepository.saveAndFlush(new StampbookProgress(otherEnded, otherUser));

        saveStampbook(region, rewardCouponPolicy, targetContent, secondTargetContent);
        entityManager.clear();

        List<MyStampbookListProjection> projections = stampbookRepository.findMyStampbookListProjections(
            viewer.getUserId(),
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
        entityManager.clear();

        assertThat(projections)
            .extracting(MyStampbookListProjection::stampbookId)
            .containsExactly(
                sameTimeSecond.getStampbookId(),
                sameTimeFirst.getStampbookId(),
                ownedEnded.getStampbookId()
            );
        assertThat(projections.getFirst())
            .extracting(
                MyStampbookListProjection::stampbookStatus,
                MyStampbookListProjection::progressStatus,
                MyStampbookListProjection::earnedCount,
                MyStampbookListProjection::targetCount,
                MyStampbookListProjection::completedAt,
                MyStampbookListProjection::lastEarnedAt
            )
            .containsExactly(StampbookStatus.PUBLISHED, null, 0L, 2L, null, null);
        assertThat(projections.get(2))
            .extracting(
                MyStampbookListProjection::stampbookStatus,
                MyStampbookListProjection::progressStatus,
                MyStampbookListProjection::completedAt
            )
            .containsExactly(StampbookStatus.ENDED, StampbookProgressStatus.COMPLETED, COMPLETED_AT);
        assertThat(stampbookProgressRepository.findById(completedProgress.getStampbookProgressId()).orElseThrow())
            .extracting(StampbookProgress::getStatus, StampbookProgress::getCompletedAt)
            .containsExactly(StampbookProgressStatus.COMPLETED, COMPLETED_AT);
    }

    @Test
    void 스탬프북_목록_Projection은_적립과_대상_콘텐츠의_교차_조인_없이_진행도를_집계한다() {
        Region region = saveRegion();
        Content firstTarget = saveContent(region, "first-target");
        Content secondTarget = saveContent(region, "second-target");
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy(firstTarget, region);
        AppUser viewer = saveUser("viewer@example.com");
        Stampbook stampbook = saveStampbook(
            region,
            rewardCouponPolicy,
            firstTarget,
            secondTarget
        );
        updateStampbookStatus(stampbook, StampbookStatus.PUBLISHED, FIRST_PUBLISHED_AT, null);
        StampbookProgress progress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, viewer)
        );
        saveStampEarn(region, viewer, progress, firstTarget, FIRST_EARNED_AT, "first");
        saveStampEarn(region, viewer, progress, secondTarget, LATEST_EARNED_AT, "second");
        progress.complete(COMPLETED_AT);
        stampbookProgressRepository.saveAndFlush(progress);
        entityManager.clear();

        MyStampbookListProjection projection = stampbookRepository.findMyStampbookListProjections(
                viewer.getUserId(),
                StampbookStatus.PUBLISHED,
                StampbookStatus.ENDED
            )
            .getFirst();
        entityManager.clear();

        assertThat(projection)
            .extracting(
                MyStampbookListProjection::progressStatus,
                MyStampbookListProjection::earnedCount,
                MyStampbookListProjection::targetCount,
                MyStampbookListProjection::completedAt,
                MyStampbookListProjection::lastEarnedAt
            )
            .containsExactly(
                StampbookProgressStatus.COMPLETED,
                2L,
                2L,
                COMPLETED_AT,
                LATEST_EARNED_AT
            );
        assertThat(stampEarnRepository.countByStampbookProgressStampbookProgressId(
            progress.getStampbookProgressId()
        )).isEqualTo(2L);
    }

    @Test
    void 스탬프북_상세_Projection은_콘텐츠별_적립을_정렬하고_공개_또는_본인_종료_이력만_조회한다() {
        Region region = saveRegion();
        Content firstTarget = saveContent(region, "first-detail-target");
        Content secondTarget = saveContent(region, "second-detail-target");
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy(firstTarget, region);
        AppUser viewer = saveUser("detail-viewer@example.com");
        AppUser otherUser = saveUser("detail-other@example.com");

        Stampbook publishedStampbook = saveStampbook(
            region,
            rewardCouponPolicy,
            firstTarget,
            secondTarget
        );
        updateStampbookStatus(publishedStampbook, StampbookStatus.PUBLISHED, FIRST_PUBLISHED_AT, null);
        StampbookProgress publishedProgress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(publishedStampbook, viewer)
        );
        saveStampEarn(
            region,
            viewer,
            publishedProgress,
            secondTarget,
            FIRST_EARNED_AT,
            "detail-published"
        );

        Stampbook ownedEndedStampbook = saveStampbook(
            region,
            rewardCouponPolicy,
            firstTarget,
            secondTarget
        );
        updateStampbookStatus(
            ownedEndedStampbook,
            StampbookStatus.ENDED,
            SECOND_PUBLISHED_AT,
            Instant.parse("2026-08-05T00:00:00Z")
        );
        StampbookProgress completedProgress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(ownedEndedStampbook, viewer)
        );
        saveStampEarn(
            region,
            viewer,
            completedProgress,
            firstTarget,
            FIRST_EARNED_AT,
            "detail-ended-first"
        );
        saveStampEarn(
            region,
            viewer,
            completedProgress,
            secondTarget,
            LATEST_EARNED_AT,
            "detail-ended-second"
        );
        completedProgress.complete(COMPLETED_AT);
        stampbookProgressRepository.saveAndFlush(completedProgress);

        Stampbook otherEndedStampbook = saveStampbook(
            region,
            rewardCouponPolicy,
            firstTarget,
            secondTarget
        );
        updateStampbookStatus(
            otherEndedStampbook,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:00Z")
        );
        stampbookProgressRepository.saveAndFlush(new StampbookProgress(otherEndedStampbook, otherUser));

        Stampbook draftStampbook = saveStampbook(
            region,
            rewardCouponPolicy,
            firstTarget,
            secondTarget
        );
        long earnedCountBeforeRead = stampEarnRepository.countByStampbookProgressStampbookProgressId(
            publishedProgress.getStampbookProgressId()
        );
        entityManager.clear();

        List<MyStampbookDetailProjection> publishedProjections = stampbookRepository
            .findMyStampbookDetailProjections(
                viewer.getUserId(),
                publishedStampbook.getStampbookId(),
                StampbookStatus.PUBLISHED,
                StampbookStatus.ENDED
            );
        List<MyStampbookDetailProjection> ownedEndedProjections = stampbookRepository
            .findMyStampbookDetailProjections(
                viewer.getUserId(),
                ownedEndedStampbook.getStampbookId(),
                StampbookStatus.PUBLISHED,
                StampbookStatus.ENDED
            );
        List<MyStampbookDetailProjection> otherEndedProjections = stampbookRepository
            .findMyStampbookDetailProjections(
                viewer.getUserId(),
                otherEndedStampbook.getStampbookId(),
                StampbookStatus.PUBLISHED,
                StampbookStatus.ENDED
            );
        List<MyStampbookDetailProjection> draftProjections = stampbookRepository
            .findMyStampbookDetailProjections(
                viewer.getUserId(),
                draftStampbook.getStampbookId(),
                StampbookStatus.PUBLISHED,
                StampbookStatus.ENDED
            );
        entityManager.clear();

        assertThat(publishedProjections)
            .extracting(MyStampbookDetailProjection::contentId)
            .containsExactly(firstTarget.getContentId(), secondTarget.getContentId());
        assertThat(publishedProjections.getFirst())
            .extracting(
                MyStampbookDetailProjection::progressStatus,
                MyStampbookDetailProjection::contentId,
                MyStampbookDetailProjection::earnedAt
            )
            .containsExactly(StampbookProgressStatus.IN_PROGRESS, firstTarget.getContentId(), null);
        assertThat(publishedProjections.get(1))
            .extracting(
                MyStampbookDetailProjection::contentId,
                MyStampbookDetailProjection::earnedAt
            )
            .containsExactly(secondTarget.getContentId(), FIRST_EARNED_AT);
        assertThat(ownedEndedProjections)
            .allSatisfy(projection -> {
                assertThat(projection.stampbookStatus()).isEqualTo(StampbookStatus.ENDED);
                assertThat(projection.progressStatus()).isEqualTo(StampbookProgressStatus.COMPLETED);
                assertThat(projection.completedAt()).isEqualTo(COMPLETED_AT);
            });
        assertThat(otherEndedProjections).isEmpty();
        assertThat(draftProjections).isEmpty();
        assertThat(stampbookProgressRepository.findById(publishedProgress.getStampbookProgressId()).orElseThrow())
            .extracting(StampbookProgress::getStatus, StampbookProgress::getCompletedAt)
            .containsExactly(StampbookProgressStatus.IN_PROGRESS, null);
        assertThat(stampEarnRepository.countByStampbookProgressStampbookProgressId(
            publishedProgress.getStampbookProgressId()
        )).isEqualTo(earnedCountBeforeRead);
    }

    private Stampbook saveStampbook(
        Region region,
        CouponPolicy rewardCouponPolicy,
        Content targetContent,
        Content secondTargetContent
    ) {
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(region, rewardCouponPolicy));
        stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, targetContent));
        stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, secondTargetContent));
        return stampbook;
    }

    private void updateStampbookStatus(
        Stampbook stampbook,
        StampbookStatus status,
        Instant publishedAt,
        Instant endedAt
    ) {
        jdbcTemplate.update(
            """
            UPDATE stampbook
            SET status = ?, published_at = ?, ended_at = ?
            WHERE stampbook_id = ?
            """,
            status.name(),
            publishedAt,
            endedAt,
            stampbook.getStampbookId()
        );
    }

    private CouponPolicy saveRewardCouponPolicy(
        Content content,
        Region region
    ) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "스탬프북 완료 보상",
            "스탬프북 완료 시 발급하는 할인 쿠폰입니다.",
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
    }

    private void saveStampEarn(
        Region region,
        AppUser user,
        StampbookProgress progress,
        Content content,
        Instant earnedAt,
        String reference
    ) {
        ContentSession contentSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        ));
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            Instant.parse("2026-08-02T00:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-20260802-" + reference,
            "qr-reference-" + reference,
            region,
            capacityHold,
            contentSession,
            user,
            ReservationStatus.CONFIRMED,
            Instant.parse("2026-08-02T00:00:00Z"),
            null,
            null,
            null,
            null
        ));
        AppUser checkinOperator = saveUser("checkin-operator-" + reference + "@example.com");
        Visit visit = visitRepository.saveAndFlush(new Visit(
            region,
            reservation,
            user,
            content,
            contentSession,
            checkinOperator,
            CheckinMethod.QR,
            Instant.parse("2026-08-02T01:05:00Z")
        ));
        stampEarnRepository.saveAndFlush(new StampEarn(progress, visit, content, earnedAt));
    }

    private Content saveContent(
        Region region,
        String suffix
    ) {
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험 " + suffix,
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
    }

    private Region saveRegion() {
        return regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }
}
