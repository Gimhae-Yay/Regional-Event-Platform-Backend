package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
class OperatorStampbookDetailControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant STAMPBOOK_PUBLISHED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant STAMPBOOK_ENDED_AT = Instant.parse("2026-08-11T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    OperatorStampbookDetailControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
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
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 담당_운영자는_모든_수명주기_정보와_정렬된_대상_콘텐츠를_조회한다() throws Exception {
        Fixture fixture = createFixture("DETAIL");
        Content firstTargetContent = saveContent(fixture.region(), fixture.operator(), "first-target");
        Content secondTargetContent = saveContent(fixture.region(), fixture.operator(), "second-target");
        Stampbook stampbook = saveStampbook(fixture, List.of(secondTargetContent, firstTargetContent));
        stampbook.requestPublication();
        stampbook.approve(STAMPBOOK_PUBLISHED_AT);
        stampbook.end(STAMPBOOK_ENDED_AT);
        stampbookRepository.saveAndFlush(stampbook);
        entityManager.clear();

        getDetail(fixture.operator(), stampbook.getStampbookId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stampbookId").value(stampbook.getStampbookId().toString()))
            .andExpect(jsonPath("$.data.title").value("스탬프북 제목"))
            .andExpect(jsonPath("$.data.regionId").value(fixture.region().getRegionId().toString()))
            .andExpect(jsonPath("$.data.status").value("ENDED"))
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
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.data.endedAt").value("2026-08-11T00:00:00Z"));
    }

    @Test
    void 담당_지역_또는_모든_대상_콘텐츠의_소유_범위를_벗어나면_거부한다() throws Exception {
        Fixture fixture = createFixture("OWNER");
        Fixture otherRegionFixture = createFixture("OTHER");
        Stampbook stampbook = saveStampbook(fixture, List.of());
        AppUser otherOperator = saveOperator(fixture.region(), "other-operator");
        Content otherOperatorContent = saveContent(fixture.region(), otherOperator, "other-owner-target");
        Stampbook otherOwnerStampbook = saveStampbook(fixture, List.of(otherOperatorContent));
        entityManager.clear();

        getDetail(otherRegionFixture.operator(), stampbook.getStampbookId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        getDetail(fixture.operator(), otherOwnerStampbook.getStampbookId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 존재하지_않는_스탬프북은_찾을_수_없다() throws Exception {
        Fixture fixture = createFixture("NOTFOUND");

        getDetail(fixture.operator(), "999999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions getDetail(
        AppUser user,
        String stampbookId
    ) throws Exception {
        return mockMvc.perform(get("/api/v1/operator/stampbooks/{stampbookId}", stampbookId)
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(
                    jwtAccessTokenService,
                    user.getUserId()
                )
            ));
    }

    private Fixture createFixture(String prefix) {
        Region region = regionRepository.saveAndFlush(new Region(
            prefix + Long.toUnsignedString(System.nanoTime()),
            "Test region",
            true
        ));
        return new Fixture(region, saveOperator(region, prefix + "-operator"));
    }

    private AppUser saveOperator(
        Region region,
        String prefix
    ) {
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            prefix + Long.toUnsignedString(System.nanoTime()) + "@example.com",
            "hashed-password",
            "Test user",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operator,
            UserRole.OPERATOR,
            region
        ));
        return operator;
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

    private record Fixture(
        Region region,
        AppUser operator
    ) {
    }
}
