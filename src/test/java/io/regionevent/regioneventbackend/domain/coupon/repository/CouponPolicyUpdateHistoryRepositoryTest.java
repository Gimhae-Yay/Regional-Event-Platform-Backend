package io.regionevent.regioneventbackend.domain.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateHistory;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CouponPolicyUpdateHistoryRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T00:00:00Z");

    private final CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;

    @Autowired
    CouponPolicyUpdateHistoryRepositoryTest(
        CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository
    ) {
        this.couponPolicyUpdateHistoryRepository = couponPolicyUpdateHistoryRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
    }

    @Test
    void 쿠폰_정책_수정이력은_처리자와_수정_전후값을_저장한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "operator@example.com",
            "hashed-password",
            "콘텐츠 운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            actor,
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
        CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "재방문 3천 원 할인",
            "기존 설명",
            CouponIssuanceType.VISIT,
            3_000L,
            10_000L,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            1_000L
        ));

        CouponPolicyUpdateHistory history = couponPolicyUpdateHistoryRepository.saveAndFlush(
            new CouponPolicyUpdateHistory(
                couponPolicy,
                actor,
                CouponPolicyUpdateHistory.snapshotOf(couponPolicy),
                new CouponPolicyUpdateHistory.Snapshot(
                    "재방문 5천 원 할인",
                    null,
                    5_000L,
                    15_000L,
                    45,
                    ISSUE_STARTS_AT,
                    ISSUE_ENDS_AT,
                    null
                ),
                "할인 금액 조정",
                UPDATED_AT
            )
        );

        CouponPolicyUpdateHistory foundHistory = couponPolicyUpdateHistoryRepository
            .findById(history.getCouponPolicyUpdateHistoryId())
            .orElseThrow();
        assertThat(foundHistory.getActor().getUserId()).isEqualTo(actor.getUserId());
        assertThat(foundHistory.getPreviousName()).isEqualTo("재방문 3천 원 할인");
        assertThat(foundHistory.getNextName()).isEqualTo("재방문 5천 원 할인");
        assertThat(foundHistory.getPreviousDiscountAmount()).isEqualTo(3_000L);
        assertThat(foundHistory.getNextDiscountAmount()).isEqualTo(5_000L);
        assertThat(foundHistory.getReason()).isEqualTo("할인 금액 조정");
        assertThat(foundHistory.getOccurredAt()).isEqualTo(UPDATED_AT);
    }
}
