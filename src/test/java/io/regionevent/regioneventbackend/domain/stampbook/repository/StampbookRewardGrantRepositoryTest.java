package io.regionevent.regioneventbackend.domain.stampbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class StampbookRewardGrantRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant GRANTED_AT = Instant.parse("2026-08-10T00:01:00Z");

    private final StampbookRewardGrantRepository stampbookRewardGrantRepository;
    private final StampbookProgressRepository stampbookProgressRepository;
    private final StampbookRepository stampbookRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    StampbookRewardGrantRepositoryTest(
        StampbookRewardGrantRepository stampbookRewardGrantRepository,
        StampbookProgressRepository stampbookProgressRepository,
        StampbookRepository stampbookRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.stampbookRewardGrantRepository = stampbookRewardGrantRepository;
        this.stampbookProgressRepository = stampbookProgressRepository;
        this.stampbookRepository = stampbookRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 완료_보상_지급_근거는_식별자와_진행_식별자로_조회한다() {
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy();
        StampbookProgress completedProgress = saveCompletedProgress(rewardCouponPolicy);
        StampbookRewardGrant rewardGrant = stampbookRewardGrantRepository.saveAndFlush(
            new StampbookRewardGrant(completedProgress, rewardCouponPolicy, GRANTED_AT)
        );
        entityManager.clear();

        StampbookRewardGrant foundByGrantId = stampbookRewardGrantRepository
            .findByStampbookRewardGrantId(rewardGrant.getStampbookRewardGrantId())
            .orElseThrow();
        StampbookRewardGrant foundByProgressId = stampbookRewardGrantRepository
            .findByStampbookProgressStampbookProgressId(completedProgress.getStampbookProgressId())
            .orElseThrow();

        assertThat(foundByGrantId.getGrantedAt()).isEqualTo(GRANTED_AT);
        assertThat(foundByProgressId.getStampbookRewardGrantId())
            .isEqualTo(rewardGrant.getStampbookRewardGrantId());
        assertThat(Hibernate.isInitialized(foundByGrantId.getStampbookProgress())).isTrue();
        assertThat(Hibernate.isInitialized(foundByGrantId.getStampbookProgress().getStampbook())).isTrue();
        assertThat(Hibernate.isInitialized(foundByGrantId.getStampbookProgress().getUser())).isTrue();
        assertThat(Hibernate.isInitialized(foundByGrantId.getCouponPolicy())).isTrue();
        assertThat(Hibernate.isInitialized(foundByGrantId.getCouponPolicy().getRegion())).isTrue();
    }

    @Test
    void 완료_보상_지급_근거는_진행별로_하나만_저장된다() {
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy();
        StampbookProgress completedProgress = saveCompletedProgress(rewardCouponPolicy);
        stampbookRewardGrantRepository.saveAndFlush(
            new StampbookRewardGrant(completedProgress, rewardCouponPolicy, GRANTED_AT)
        );

        assertThatThrownBy(() -> stampbookRewardGrantRepository.saveAndFlush(
            new StampbookRewardGrant(completedProgress, rewardCouponPolicy, GRANTED_AT.plusSeconds(1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 완료_보상_지급_근거는_존재하는_진행과_쿠폰_정책만_참조한다() {
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy();
        StampbookProgress completedProgress = saveCompletedProgress(rewardCouponPolicy);

        assertThatThrownBy(() -> insertRewardGrant(
            Long.MAX_VALUE,
            rewardCouponPolicy.getCouponPolicyId()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRewardGrant(
            completedProgress.getStampbookProgressId(),
            Long.MAX_VALUE
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRewardGrant(
        Long stampbookProgressId,
        Long couponPolicyId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO stampbook_reward_grant (
                stampbook_progress_id,
                coupon_policy_id,
                granted_at
            ) VALUES (?, ?, ?)
            """,
            stampbookProgressId,
            couponPolicyId,
            GRANTED_AT
        );
    }

    private StampbookProgress saveCompletedProgress(CouponPolicy rewardCouponPolicy) {
        Stampbook stampbook = stampbookRepository.saveAndFlush(
            new Stampbook(rewardCouponPolicy.getRegion(), rewardCouponPolicy)
        );
        AppUser user = saveUser("visitor@example.com");
        StampbookProgress stampbookProgress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, user)
        );
        stampbookProgress.complete(COMPLETED_AT);
        stampbookProgressRepository.flush();
        return stampbookProgress;
    }

    private CouponPolicy saveRewardCouponPolicy() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            saveUser("operator@example.com"),
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
