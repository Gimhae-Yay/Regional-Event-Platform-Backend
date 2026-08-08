package io.regionevent.regioneventbackend.domain.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CouponPolicyRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-03T00:00:00Z");

    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CouponPolicyRepositoryTest(
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 쿠폰_정책은_콘텐츠와_같은_지역만_저장한다() {
        Region contentRegion = saveRegion("GIMHAE");
        Region anotherRegion = saveRegion("BUSAN");
        Content content = saveContent(contentRegion);
        CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(newCouponPolicy(content, contentRegion));
        entityManager.clear();

        CouponPolicy foundCouponPolicy = couponPolicyRepository
            .findByCouponPolicyId(couponPolicy.getCouponPolicyId())
            .orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundCouponPolicy.getContent().getContentId()).isEqualTo(content.getContentId());
        assertThat(foundCouponPolicy.getRegion().getRegionId()).isEqualTo(contentRegion.getRegionId());
        assertThat(persistenceUnitUtil.isLoaded(foundCouponPolicy, "content")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(foundCouponPolicy, "region")).isTrue();
        assertThat(couponPolicyRepository.findByCouponPolicyIdForUpdate(couponPolicy.getCouponPolicyId()))
            .contains(foundCouponPolicy);
        assertThatThrownBy(() -> insertCouponPolicy(
            content.getContentId(),
            anotherRegion.getRegionId(),
            CouponIssuanceType.VISIT.name(),
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            null,
            0,
            CouponPolicyStatus.DRAFT.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 쿠폰_정책은_정의된_발급_경로만_저장한다() {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region);

        assertThatThrownBy(() -> insertCouponPolicy(
            content.getContentId(),
            region.getRegionId(),
            "MANUAL",
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            null,
            0,
            CouponPolicyStatus.DRAFT.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 쿠폰_정책은_상태별_시각을_일관되게_저장한다() {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region);
        CouponPolicy draftPolicy = couponPolicyRepository.saveAndFlush(newCouponPolicy(content, region));
        CouponPolicy publishedPolicy = couponPolicyRepository.saveAndFlush(newCouponPolicy(content, region));
        CouponPolicy endedPolicy = couponPolicyRepository.saveAndFlush(newCouponPolicy(content, region));

        publishedPolicy.publish(PUBLISHED_AT);
        couponPolicyRepository.flush();
        endedPolicy.publish(PUBLISHED_AT);
        endedPolicy.end(ENDED_AT);
        couponPolicyRepository.flush();

        assertThat(draftPolicy.getStatus()).isEqualTo(CouponPolicyStatus.DRAFT);
        assertThat(draftPolicy.getPublishedAt()).isNull();
        assertThat(draftPolicy.getEndedAt()).isNull();
        assertThat(publishedPolicy.getStatus()).isEqualTo(CouponPolicyStatus.PUBLISHED);
        assertThat(publishedPolicy.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(publishedPolicy.getEndedAt()).isNull();
        assertThat(endedPolicy.getStatus()).isEqualTo(CouponPolicyStatus.ENDED);
        assertThat(endedPolicy.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(endedPolicy.getEndedAt()).isEqualTo(ENDED_AT);
        assertThatThrownBy(() -> insertCouponPolicy(
            content.getContentId(),
            region.getRegionId(),
            CouponIssuanceType.VISIT.name(),
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            null,
            0,
            CouponPolicyStatus.PUBLISHED.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCouponPolicy(
            content.getContentId(),
            region.getRegionId(),
            CouponIssuanceType.VISIT.name(),
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            null,
            0,
            CouponPolicyStatus.ENDED.name(),
            PUBLISHED_AT,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidCouponPolicyConditions")
    void 쿠폰_정책은_금액_기간_발급_한도_제약을_강제한다(
        long discountAmount,
        long minimumPaymentAmount,
        int validDays,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit,
        long issuedCount
    ) {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region);

        assertThatThrownBy(() -> insertCouponPolicy(
            content.getContentId(),
            region.getRegionId(),
            CouponIssuanceType.VISIT.name(),
            discountAmount,
            minimumPaymentAmount,
            validDays,
            issueStartsAt,
            issueEndsAt,
            totalIssueLimit,
            issuedCount,
            CouponPolicyStatus.DRAFT.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Stream<Arguments> invalidCouponPolicyConditions() {
        return Stream.of(
            Arguments.of(0L, 10_000L, 30, ISSUE_STARTS_AT, ISSUE_ENDS_AT, null, 0L),
            Arguments.of(3_000L, 2_999L, 30, ISSUE_STARTS_AT, ISSUE_ENDS_AT, null, 0L),
            Arguments.of(3_000L, 10_000L, 0, ISSUE_STARTS_AT, ISSUE_ENDS_AT, null, 0L),
            Arguments.of(3_000L, 10_000L, 366, ISSUE_STARTS_AT, ISSUE_ENDS_AT, null, 0L),
            Arguments.of(3_000L, 10_000L, 30, ISSUE_STARTS_AT, ISSUE_STARTS_AT, null, 0L),
            Arguments.of(3_000L, 10_000L, 30, ISSUE_STARTS_AT, ISSUE_ENDS_AT, 0L, 0L),
            Arguments.of(3_000L, 10_000L, 30, ISSUE_STARTS_AT, ISSUE_ENDS_AT, null, -1L),
            Arguments.of(3_000L, 10_000L, 30, ISSUE_STARTS_AT, ISSUE_ENDS_AT, 10L, 11L)
        );
    }

    private CouponPolicy newCouponPolicy(
        Content content,
        Region region
    ) {
        return new CouponPolicy(
            content,
            region,
            "재방문 3천 원 할인",
            "유효 방문 뒤 발급되는 재방문 쿠폰입니다.",
            CouponIssuanceType.VISIT,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        );
    }

    private void insertCouponPolicy(
        Long contentId,
        Long regionId,
        String issuanceType,
        long discountAmount,
        long minimumPaymentAmount,
        int validDays,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit,
        long issuedCount,
        String status,
        Instant publishedAt,
        Instant endedAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO coupon_policy (
                content_id,
                region_id,
                name,
                issuance_type,
                discount_amount,
                minimum_payment_amount,
                valid_days,
                issue_starts_at,
                issue_ends_at,
                total_issue_limit,
                issued_count,
                status,
                published_at,
                ended_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            contentId,
            regionId,
            "쿠폰 정책 제약 검증",
            issuanceType,
            discountAmount,
            minimumPaymentAmount,
            validDays,
            issueStartsAt,
            issueEndsAt,
            totalIssueLimit,
            issuedCount,
            status,
            publishedAt,
            endedAt
        );
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + "시", true));
    }

    private Content saveContent(Region region) {
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + region.getRegionCode() + "@example.com",
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
