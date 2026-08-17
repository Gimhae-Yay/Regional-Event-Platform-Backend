package io.regionevent.regioneventbackend.domain.stampbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
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
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContentId;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class StampbookRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-02T00:00:00Z");

    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    StampbookRepositoryTest(
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        AuditEventRepository auditEventRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.auditEventRepository = auditEventRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 스탬프북과_대상_콘텐츠를_저장하고_복합_식별자로_조회한다() {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region);
        CouponPolicy couponPolicy = saveRewardCouponPolicy(content, region);
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(region, couponPolicy));
        stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, content));
        entityManager.clear();

        StampbookContent foundStampbookContent = stampbookContentRepository.findById(
            new StampbookContentId(stampbook.getStampbookId(), content.getContentId())
        ).orElseThrow();

        assertThat(foundStampbookContent.getId()).isEqualTo(
            new StampbookContentId(stampbook.getStampbookId(), content.getContentId())
        );
        assertThat(Hibernate.isInitialized(foundStampbookContent.getStampbook())).isFalse();
        assertThat(Hibernate.isInitialized(foundStampbookContent.getContent())).isFalse();

        Stampbook foundStampbook = stampbookRepository.findById(stampbook.getStampbookId()).orElseThrow();
        assertThat(foundStampbook.getStatus()).isEqualTo(StampbookStatus.DRAFT);
        assertThat(foundStampbook.getPublishedAt()).isNull();
        assertThat(foundStampbook.getEndedAt()).isNull();
        assertThat(Hibernate.isInitialized(foundStampbook.getRegion())).isFalse();
        assertThat(Hibernate.isInitialized(foundStampbook.getRewardCouponPolicy())).isFalse();
    }

    @Test
    void 스탬프북은_정의된_상태와_상태별_시각만_저장한다() {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region);
        CouponPolicy couponPolicy = saveRewardCouponPolicy(content, region);

        assertThatThrownBy(() -> insertStampbook(
            region.getRegionId(),
            couponPolicy.getCouponPolicyId(),
            "UNKNOWN",
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStampbook(
            region.getRegionId(),
            couponPolicy.getCouponPolicyId(),
            StampbookStatus.PUBLISHED.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStampbook(
            region.getRegionId(),
            couponPolicy.getCouponPolicyId(),
            StampbookStatus.ENDED.name(),
            PUBLISHED_AT,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 스탬프북은_존재하는_보상_쿠폰_정책만_참조한다() {
        Region region = saveRegion("GIMHAE");

        assertThatThrownBy(() -> insertStampbook(
            region.getRegionId(),
            Long.MAX_VALUE,
            StampbookStatus.DRAFT.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 대상_콘텐츠는_스탬프북별_복합_식별자로_중복을_차단한다() {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region);
        CouponPolicy couponPolicy = saveRewardCouponPolicy(content, region);
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(region, couponPolicy));

        insertStampbookContent(stampbook.getStampbookId(), content.getContentId());

        assertThatThrownBy(() -> insertStampbookContent(
            stampbook.getStampbookId(),
            content.getContentId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 담당지역의_심사대기_스탬프북을_가장최근제출시각순으로_조회한다() {
        Region region = saveRegion("GIMHAE");
        Content firstContent = saveContent(region);
        Content secondContent = saveContent(region);
        CouponPolicy firstRewardPolicy = saveRewardCouponPolicy(firstContent, region);
        CouponPolicy secondRewardPolicy = saveRewardCouponPolicy(secondContent, region);
        Stampbook firstStampbook = savePendingStampbook(
            region,
            firstRewardPolicy,
            firstContent,
            secondContent
        );
        Stampbook secondStampbook = savePendingStampbook(
            region,
            secondRewardPolicy,
            secondContent
        );
        recordSubmissionAudit(region, firstStampbook, Instant.parse("2026-08-10T00:00:00Z"));
        recordSubmissionAudit(region, firstStampbook, Instant.parse("2026-08-12T00:00:00Z"));
        recordSubmissionAudit(region, secondStampbook, Instant.parse("2026-08-11T00:00:00Z"));

        Region otherRegion = saveRegion("BUSAN");
        Content otherContent = saveContent(otherRegion);
        CouponPolicy otherRewardPolicy = saveRewardCouponPolicy(otherContent, otherRegion);
        Stampbook otherRegionStampbook = savePendingStampbook(
            otherRegion,
            otherRewardPolicy,
            otherContent
        );
        recordSubmissionAudit(otherRegion, otherRegionStampbook, Instant.parse("2026-08-09T00:00:00Z"));

        List<PendingRegionAdminStampbookProjection> projections = stampbookRepository
            .findPendingRegionAdminStampbookProjections(
                region.getRegionId(),
                StampbookStatus.PENDING_REVIEW,
                AuditEventTargetType.STAMPBOOK,
                AuditEventResult.SUCCESS,
                StampbookStatus.DRAFT.name(),
                StampbookStatus.PENDING_REVIEW.name()
            );

        assertThat(projections).containsExactly(
            new PendingRegionAdminStampbookProjection(
                secondStampbook.getStampbookId(),
                region.getRegionId(),
                StampbookStatus.PENDING_REVIEW,
                1L,
                secondRewardPolicy.getCouponPolicyId(),
                Instant.parse("2026-08-11T00:00:00Z")
            ),
            new PendingRegionAdminStampbookProjection(
                firstStampbook.getStampbookId(),
                region.getRegionId(),
                StampbookStatus.PENDING_REVIEW,
                2L,
                firstRewardPolicy.getCouponPolicyId(),
                Instant.parse("2026-08-12T00:00:00Z")
            )
        );
    }

    @Test
    void 서로_다른_지역의_콘텐츠는_스탬프북에_연결할_수_없다() {
        Region stampbookRegion = saveRegion("GIMHAE");
        Content stampbookContent = saveContent(stampbookRegion);
        CouponPolicy couponPolicy = saveRewardCouponPolicy(stampbookContent, stampbookRegion);
        Stampbook stampbook = stampbookRepository.saveAndFlush(
            new Stampbook(stampbookRegion, couponPolicy)
        );
        Region otherRegion = saveRegion("BUSAN");
        Content otherRegionContent = saveContent(otherRegion);

        assertThatThrownBy(() -> new StampbookContent(stampbook, otherRegionContent))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("content must belong to stampbook region");
    }

    private void insertStampbook(
        Long regionId,
        Long rewardCouponPolicyId,
        String status,
        Instant publishedAt,
        Instant endedAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO stampbook (
                region_id,
                reward_coupon_policy_id,
                status,
                published_at,
                ended_at
            ) VALUES (?, ?, ?, ?, ?)
            """,
            regionId,
            rewardCouponPolicyId,
            status,
            publishedAt,
            endedAt
        );
    }

    private void insertStampbookContent(
        Long stampbookId,
        Long contentId
    ) {
        jdbcTemplate.update(
            "INSERT INTO stampbook_content (stampbook_id, content_id) VALUES (?, ?)",
            stampbookId,
            contentId
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

    private Stampbook savePendingStampbook(
        Region region,
        CouponPolicy rewardCouponPolicy,
        Content... contents
    ) {
        Stampbook stampbook = new Stampbook(region, rewardCouponPolicy);
        stampbook.requestPublication();
        stampbook = stampbookRepository.saveAndFlush(stampbook);
        for (Content content : contents) {
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, content));
        }
        return stampbook;
    }

    private void recordSubmissionAudit(
        Region region,
        Stampbook stampbook,
        Instant occurredAt
    ) {
        auditEventRepository.saveAndFlush(new AuditEvent(
            UUID.randomUUID().toString(),
            region,
            AuditEventTargetType.STAMPBOOK,
            stampbook.getStampbookId(),
            StampbookStatus.DRAFT.name(),
            StampbookStatus.PENDING_REVIEW.name(),
            AuditEventResult.SUCCESS,
            null,
            "스탬프북 공개 심사를 요청합니다.",
            null,
            "USER",
            "OPERATOR",
            occurredAt
        ));
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + "시", true));
    }

    private Content saveContent(Region region) {
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + region.getRegionCode() + "-" + System.nanoTime() + "@example.com",
            "hashed-password",
            "콘텐츠 운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        return contentRepository.saveAndFlush(new Content(
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
    }
}
