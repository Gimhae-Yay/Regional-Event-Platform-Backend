package io.regionevent.regioneventbackend.domain.stampbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookReadService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class OperatorStampbookListProjectionRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");

    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OperatorStampbookListProjectionRepositoryTest(
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 운영자_지역의_모든_대상콘텐츠를_본인이_소유한_스탬프북만_식별자_내림차순으로_조회한다() {
        Region region = saveRegion("GIMHAE");
        Region otherRegion = saveRegion("BUSAN");
        AppUser operator = saveUser("operator");
        AppUser otherOperator = saveUser("other-operator");
        AppUser noTargetOperator = saveUser("no-target-operator");
        Content firstOwnedContent = saveContent(region, operator, "first-owned");
        Content secondOwnedContent = saveContent(region, operator, "second-owned");
        Content otherOwnedContent = saveContent(region, otherOperator, "other-owned");
        Content otherRegionContent = saveContent(otherRegion, operator, "other-region");

        Stampbook earlierOwnedStampbook = saveStampbook(
            region,
            saveRewardCouponPolicy(firstOwnedContent, region, "earlier"),
            "이전 내 스탬프북",
            firstOwnedContent
        );
        Stampbook laterOwnedStampbook = saveStampbook(
            region,
            saveRewardCouponPolicy(secondOwnedContent, region, "later"),
            "최근 내 스탬프북",
            firstOwnedContent,
            secondOwnedContent
        );
        saveStampbook(
            region,
            saveRewardCouponPolicy(otherOwnedContent, region, "other"),
            "다른 운영자 스탬프북",
            otherOwnedContent
        );
        saveStampbook(
            region,
            saveRewardCouponPolicy(firstOwnedContent, region, "mixed"),
            "공동 소유 스탬프북",
            firstOwnedContent,
            otherOwnedContent
        );
        saveStampbook(
            otherRegion,
            saveRewardCouponPolicy(otherRegionContent, otherRegion, "other-region"),
            "다른 지역 스탬프북",
            otherRegionContent
        );
        entityManager.clear();

        List<OperatorStampbookListProjection> projections = stampbookRepository
            .findOperatorStampbookListProjections(operator.getUserId(), region.getRegionId());

        assertThat(projections)
            .extracting(OperatorStampbookListProjection::stampbookId)
            .containsExactly(laterOwnedStampbook.getStampbookId(), earlierOwnedStampbook.getStampbookId());
        assertThat(projections)
            .extracting(OperatorStampbookListProjection::targetCount)
            .containsExactly(2L, 1L);
        assertThat(projections)
            .allSatisfy(projection -> {
                assertThat(projection.regionId()).isEqualTo(region.getRegionId());
                assertThat(projection.rewardCouponPolicyRegionId()).isEqualTo(region.getRegionId());
            });
        assertThat(stampbookRepository.findOperatorStampbookListProjections(
            noTargetOperator.getUserId(),
            region.getRegionId()
        )).isEmpty();
    }

    @Test
    void 대상콘텐츠가_없거나_다른지역에_연결된_스탬프북은_정합성오류로_전달한다() {
        Region region = saveRegion("GIMHAE");
        Region otherRegion = saveRegion("BUSAN");
        AppUser operator = saveUser("operator");
        Content ownedContent = saveContent(region, operator, "owned");
        Content otherRegionOwnedContent = saveContent(otherRegion, operator, "other-region-owned");
        CouponPolicy rewardCouponPolicy = saveRewardCouponPolicy(ownedContent, region, "reward");
        Stampbook stampbookWithoutTarget = saveStampbook(
            region,
            rewardCouponPolicy,
            "대상 없는 스탬프북"
        );
        Stampbook stampbookWithOtherRegionTarget = stampbookRepository.saveAndFlush(new Stampbook(
            region,
            rewardCouponPolicy,
            "다른 지역 대상 스탬프북"
        ));
        jdbcTemplate.update(
            "INSERT INTO stampbook_content (stampbook_id, content_id) VALUES (?, ?)",
            stampbookWithOtherRegionTarget.getStampbookId(),
            otherRegionOwnedContent.getContentId()
        );
        entityManager.clear();

        List<OperatorStampbookListProjection> projections = stampbookRepository
            .findOperatorStampbookListProjections(operator.getUserId(), region.getRegionId());

        assertThat(projections)
            .extracting(OperatorStampbookListProjection::stampbookId)
            .containsExactly(
                stampbookWithOtherRegionTarget.getStampbookId(),
                stampbookWithoutTarget.getStampbookId()
            );
        assertThat(projections.getFirst())
            .extracting(
                OperatorStampbookListProjection::minimumTargetContentRegionId,
                OperatorStampbookListProjection::maximumTargetContentRegionId
            )
            .containsExactly(otherRegion.getRegionId(), otherRegion.getRegionId());
        assertThat(projections.get(1))
            .extracting(OperatorStampbookListProjection::targetCount)
            .isEqualTo(0L);
        assertThatThrownBy(() -> new StampbookReadService(stampbookRepository)
            .findOperatorStampbooks(operator.getUserId(), region.getRegionId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("operator stampbook read data is inconsistent");
    }

    private Stampbook saveStampbook(
        Region region,
        CouponPolicy rewardCouponPolicy,
        String title,
        Content... targetContents
    ) {
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(
            region,
            rewardCouponPolicy,
            title
        ));
        for (Content targetContent : targetContents) {
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, targetContent));
        }
        return stampbook;
    }

    private CouponPolicy saveRewardCouponPolicy(
        Content content,
        Region region,
        String prefix
    ) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            prefix + " 스탬프북 완료 쿠폰",
            null,
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String prefix
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            prefix + " 콘텐츠",
            "운영자 스탬프북 목록 조회 테스트 콘텐츠입니다.",
            "테스트 장소",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            ISSUE_STARTS_AT
        ));
    }

    private Region saveRegion(String code) {
        return regionRepository.saveAndFlush(new Region(
            code + "-" + System.nanoTime(),
            code + " 지역",
            true
        ));
    }

    private AppUser saveUser(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "-" + suffix + "@example.com",
            "hashed-password",
            "테스트 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }
}
