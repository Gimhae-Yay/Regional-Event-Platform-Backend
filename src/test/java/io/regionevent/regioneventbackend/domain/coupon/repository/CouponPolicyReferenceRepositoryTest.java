package io.regionevent.regioneventbackend.domain.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
class CouponPolicyReferenceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final StampbookRepository stampbookRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CouponPolicyReferenceRepositoryTest(
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        StampbookRepository stampbookRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.stampbookRepository = stampbookRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 공개_미션과_스탬프북의_보상_쿠폰_정책_참조를_각각_확인한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "지역 체험",
            "지역 체험 콘텐츠입니다.",
            "김해시 문화회관",
            "10:00~18:00",
            "055-1234-5678",
            "안내 사항",
            "전체",
            "없음",
            "당일 취소 가능",
            NOW
        ));
        CouponPolicy missionPolicy = couponPolicyRepository.saveAndFlush(
            createPolicy(content, region, CouponIssuanceType.MISSION_REWARD)
        );
        CouponPolicy stampbookPolicy = couponPolicyRepository.saveAndFlush(
            createPolicy(content, region, CouponIssuanceType.STAMPBOOK_COMPLETION)
        );
        Mission mission = missionRepository.saveAndFlush(new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            1,
            missionPolicy,
            NOW.plusSeconds(3_600)
        ));
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(region, stampbookPolicy, "스탬프북 제목"));
        jdbcTemplate.update(
            "UPDATE mission SET status = 'PUBLISHED', published_at = ? WHERE mission_id = ?",
            NOW,
            mission.getMissionId()
        );
        jdbcTemplate.update(
            "UPDATE stampbook SET status = 'PUBLISHED', published_at = ? WHERE stampbook_id = ?",
            NOW,
            stampbook.getStampbookId()
        );

        assertThat(missionRepository.existsByRewardCouponPolicyCouponPolicyIdAndStatus(
            missionPolicy.getCouponPolicyId(),
            MissionStatus.PUBLISHED
        )).isTrue();
        assertThat(stampbookRepository.existsByRewardCouponPolicyCouponPolicyIdAndStatus(
            stampbookPolicy.getCouponPolicyId(),
            StampbookStatus.PUBLISHED
        )).isTrue();
        assertThat(missionRepository.existsByRewardCouponPolicyCouponPolicyIdAndStatus(
            stampbookPolicy.getCouponPolicyId(),
            MissionStatus.PUBLISHED
        )).isFalse();
        assertThat(stampbookRepository.existsByRewardCouponPolicyCouponPolicyIdAndStatus(
            missionPolicy.getCouponPolicyId(),
            StampbookStatus.PUBLISHED
        )).isFalse();
    }

    private CouponPolicy createPolicy(
        Content content,
        Region region,
        CouponIssuanceType issuanceType
    ) {
        return new CouponPolicy(
            content,
            region,
            "보상 쿠폰",
            null,
            issuanceType,
            1_000L,
            1_000L,
            7,
            NOW.minusSeconds(3_600),
            NOW.plusSeconds(3_600),
            null
        );
    }
}
