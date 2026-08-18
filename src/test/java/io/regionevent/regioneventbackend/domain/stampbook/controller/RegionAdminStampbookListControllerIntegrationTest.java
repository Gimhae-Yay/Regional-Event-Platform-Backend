package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegionAdminStampbookListControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");

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
    RegionAdminStampbookListControllerIntegrationTest(
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
    void 담당지역의_심사대기_스탬프북만_제출시각순으로_반환한다() throws Exception {
        Fixture fixture = createRegionAdminFixture("GIMHAE");
        Fixture otherFixture = createRegionAdminFixture("BUSAN");
        Stampbook laterRequestedStampbook = savePendingStampbook(
            fixture,
            "later",
            2,
            Instant.parse("2026-08-14T02:20:00Z")
        );
        Stampbook earlierRequestedStampbook = savePendingStampbook(
            fixture,
            "earlier",
            1,
            Instant.parse("2026-08-14T02:10:00Z")
        );
        saveDraftStampbook(fixture, "draft");
        savePendingStampbook(otherFixture, "other", 1, Instant.parse("2026-08-14T02:00:00Z"));
        entityManager.clear();

        getPendingStampbooks(fixture.admin())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.stampbooks.length()").value(2))
            .andExpect(jsonPath("$.data.stampbooks[0].stampbookId")
                .value(earlierRequestedStampbook.getStampbookId().toString()))
            .andExpect(jsonPath("$.data.stampbooks[0].regionId")
                .value(fixture.region().getRegionId().toString()))
            .andExpect(jsonPath("$.data.stampbooks[0].status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.stampbooks[0].targetCount").value(1))
            .andExpect(jsonPath("$.data.stampbooks[0].requestedAt")
                .value("2026-08-14T02:10:00Z"))
            .andExpect(jsonPath("$.data.stampbooks[1].stampbookId")
                .value(laterRequestedStampbook.getStampbookId().toString()))
            .andExpect(jsonPath("$.data.stampbooks[1].targetCount").value(2))
            .andExpect(jsonPath("$.data.stampbooks[1].requestedAt")
                .value("2026-08-14T02:20:00Z"));
    }

    @Test
    void 미인증과_비지역관리자는_심사대기목록을_조회할수없다() throws Exception {
        Fixture fixture = createRegionAdminFixture("GIMHAE");
        AppUser visitor = saveUser("visitor");
        savePendingStampbook(fixture, "pending", 1, Instant.parse("2026-08-14T02:20:00Z"));

        mockMvc.perform(get("/api/v1/region-admin/stampbooks").param("status", "PENDING_REVIEW"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        getPendingStampbooks(visitor)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private ResultActions getPendingStampbooks(AppUser user) throws Exception {
        return mockMvc.perform(get("/api/v1/region-admin/stampbooks")
            .param("status", "PENDING_REVIEW")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createRegionAdminFixture(String regionCode) {
        Region region = regionRepository.saveAndFlush(new Region(
            regionCode + "-" + System.nanoTime(),
            regionCode + " 지역",
            true
        ));
        AppUser admin = saveUser("region-admin");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        return new Fixture(region, admin);
    }

    private Stampbook savePendingStampbook(
        Fixture fixture,
        String prefix,
        int targetCount,
        Instant requestedAt
    ) {
        Stampbook stampbook = saveDraftStampbook(fixture, prefix, targetCount);
        stampbook.requestPublication();
        stampbook = stampbookRepository.saveAndFlush(stampbook);
        auditEventRepository.saveAndFlush(new AuditEvent(
            UUID.randomUUID().toString(),
            fixture.region(),
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
            requestedAt
        ));
        return stampbook;
    }

    private Stampbook saveDraftStampbook(
        Fixture fixture,
        String prefix
    ) {
        return saveDraftStampbook(fixture, prefix, 1);
    }

    private Stampbook saveDraftStampbook(
        Fixture fixture,
        String prefix,
        int targetCount
    ) {
        Content rewardContent = saveContent(fixture.region(), fixture.admin(), prefix + "-reward");
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            rewardContent,
            fixture.region(),
            prefix + " 스탬프북 완료 쿠폰",
            null,
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
        for (int index = 0; index < targetCount; index++) {
            Content targetContent = index == 0
                ? rewardContent
                : saveContent(fixture.region(), fixture.admin(), prefix + "-target-" + index);
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, targetContent));
        }
        return stampbook;
    }

    private Content saveContent(
        Region region,
        AppUser owner,
        String prefix
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            prefix + " 콘텐츠",
            "스탬프북 심사 대기 목록 통합 테스트 콘텐츠입니다.",
            "테스트 장소",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            CONTENT_PUBLISHED_AT
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

    private record Fixture(
        Region region,
        AppUser admin
    ) {
    }
}
