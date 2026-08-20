package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegionAdminStampbookDetailControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant FIRST_REQUESTED_AT = Instant.parse("2026-08-09T01:00:00Z");
    private static final Instant LATEST_REQUESTED_AT = Instant.parse("2026-08-09T02:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-11T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    RegionAdminStampbookDetailControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 심사_상세는_대상_콘텐츠와_보상_정책_및_가장_최근_심사_요청을_반환한다() throws Exception {
        Fixture fixture = createFixture("DETAIL");
        Content firstTargetContent = saveContent(fixture.region(), fixture.operator(), "first-target");
        Content secondTargetContent = saveContent(fixture.region(), fixture.operator(), "second-target");
        Stampbook stampbook = savePendingReviewStampbook(
            fixture,
            List.of(secondTargetContent, firstTargetContent)
        );
        recordReviewRequest(stampbook, fixture.region(), FIRST_REQUESTED_AT, "이전 심사 요청 사유");
        recordReviewRequest(stampbook, fixture.region(), LATEST_REQUESTED_AT, "최신 심사 요청 사유");
        entityManager.clear();

        getDetail(fixture.regionAdmin(), stampbook.getStampbookId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 심사 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value(stampbook.getStampbookId().toString()))
            .andExpect(jsonPath("$.data.regionId").value(fixture.region().getRegionId().toString()))
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value(
                firstTargetContent.getContentId().toString()
            ))
            .andExpect(jsonPath("$.data.targetContents[0].regionId").value(
                fixture.region().getRegionId().toString()
            ))
            .andExpect(jsonPath("$.data.targetContents[0].title").value(firstTargetContent.getTitle()))
            .andExpect(jsonPath("$.data.targetContents[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.targetContents[1].contentId").value(
                secondTargetContent.getContentId().toString()
            ))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.couponPolicyId").value(
                stampbook.getRewardCouponPolicy().getCouponPolicyId().toString()
            ))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.regionId").value(
                fixture.region().getRegionId().toString()
            ))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.issuanceType").value("STAMPBOOK_COMPLETION"))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-09T02:00:00Z"))
            .andExpect(jsonPath("$.data.requestReason").value("최신 심사 요청 사유"));
    }

    @Test
    void 다른_지역_관리자는_심사_대기_스탬프북을_찾을_수_없다() throws Exception {
        Fixture fixture = createFixture("OWNER");
        Fixture otherRegionFixture = createFixture("OTHER");
        Stampbook stampbook = savePendingReviewStampbook(fixture, List.of());
        recordReviewRequest(stampbook, fixture.region(), FIRST_REQUESTED_AT, "심사 요청 사유");
        entityManager.clear();

        getDetail(otherRegionFixture.regionAdmin(), stampbook.getStampbookId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 심사_대기가_아닌_스탬프북은_심사_상세_조회에서_거부된다() throws Exception {
        for (StampbookStatus status : List.of(
            StampbookStatus.DRAFT,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )) {
            Fixture fixture = createFixture("STATUS");
            Stampbook stampbook = saveStampbook(fixture, List.of());
            updateStampbookStatus(stampbook, status);
            entityManager.clear();

            getDetail(fixture.regionAdmin(), stampbook.getStampbookId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void 지역_관리자_권한과_식별자_계약을_검증한다() throws Exception {
        Fixture fixture = createFixture("AUTH");
        AppUser visitor = saveUser("visitor");
        Stampbook stampbook = savePendingReviewStampbook(fixture, List.of());
        recordReviewRequest(stampbook, fixture.region(), FIRST_REQUESTED_AT, "심사 요청 사유");

        getDetail(visitor, stampbook.getStampbookId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/region-admin/stampbooks/{stampbookId}", stampbook.getStampbookId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        getDetail(fixture.regionAdmin(), "01")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        getDetail(fixture.regionAdmin(), "not-a-number")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private ResultActions getDetail(
        AppUser user,
        String stampbookId
    ) throws Exception {
        return mockMvc.perform(get("/api/v1/region-admin/stampbooks/{stampbookId}", stampbookId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId())));
    }

    private Fixture createFixture(String prefix) {
        Region region = regionRepository.saveAndFlush(new Region(
            prefix + Long.toUnsignedString(System.nanoTime()),
            "Test region",
            true
        ));
        AppUser regionAdmin = saveUser(prefix + "-admin");
        AppUser operator = saveUser(prefix + "-operator");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            regionAdmin,
            UserRole.REGION_ADMIN,
            region
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operator,
            UserRole.OPERATOR,
            region
        ));
        return new Fixture(region, regionAdmin, operator);
    }

    private AppUser saveUser(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "-" + suffix + "@example.com",
            "hashed-password",
            "Test user",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Stampbook savePendingReviewStampbook(
        Fixture fixture,
        List<Content> targetContents
    ) {
        Stampbook stampbook = saveStampbook(fixture, targetContents);
        stampbook.requestPublication();
        return stampbookRepository.saveAndFlush(stampbook);
    }

    private Stampbook saveStampbook(
        Fixture fixture,
        List<Content> targetContents
    ) {
        Content rewardContent = saveContent(fixture.region(), fixture.operator(), "reward");
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            rewardContent,
            fixture.region(),
            "스탬프북 완료 쿠폰",
            "스탬프북 완료 보상 쿠폰",
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            3_000,
            10_000,
            30,
            CONTENT_PUBLISHED_AT,
            COUPON_ISSUE_ENDS_AT,
            100L
        ));
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(
            fixture.region(),
            rewardCouponPolicy,
            "스탬프북 제목"
        ));
        List<Content> effectiveTargetContents = targetContents.isEmpty()
            ? List.of(saveContent(fixture.region(), fixture.operator(), "target"))
            : targetContents;
        stampbookContentRepository.saveAllAndFlush(effectiveTargetContents.stream()
            .map(targetContent -> new StampbookContent(stampbook, targetContent))
            .toList());
        return stampbook;
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String suffix
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            suffix + " content",
            "Stampbook test content description",
            "Test city",
            "Every day 10:00~18:00",
            "055-1234-5678",
            "Follow safety guide",
            "Age 7+",
            "Comfortable clothes",
            "Cancel before start day",
            CONTENT_PUBLISHED_AT
        ));
    }

    private void recordReviewRequest(
        Stampbook stampbook,
        Region region,
        Instant requestedAt,
        String requestReason
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
            requestReason,
            null,
            "USER",
            UserRole.OPERATOR.name(),
            requestedAt
        ));
    }

    private void updateStampbookStatus(
        Stampbook stampbook,
        StampbookStatus status
    ) {
        Instant publishedAt = null;
        Instant endedAt = null;
        if (status == StampbookStatus.PUBLISHED || status == StampbookStatus.ENDED) {
            publishedAt = PUBLISHED_AT;
        }
        if (status == StampbookStatus.ENDED) {
            endedAt = ENDED_AT;
        }
        entityManager.createNativeQuery("""
            UPDATE stampbook
            SET status = :status,
                published_at = :publishedAt,
                ended_at = :endedAt
            WHERE stampbook_id = :stampbookId
            """)
            .setParameter("status", status.name())
            .setParameter("publishedAt", publishedAt)
            .setParameter("endedAt", endedAt)
            .setParameter("stampbookId", stampbook.getStampbookId())
            .executeUpdate();
    }

    private record Fixture(
        Region region,
        AppUser regionAdmin,
        AppUser operator
    ) {
    }
}
