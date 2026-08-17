package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
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
class RegionAdminMissionControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-30T14:59:59Z");
    private static final Instant MISSION_PUBLISHED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant MISSION_ENDED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final MissionRepository missionRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    RegionAdminMissionControllerIntegrationTest(
        MockMvc mockMvc,
        MissionRepository missionRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.missionRepository = missionRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void getMissions_returnsOnlyAuthorizedRegionMissionsWithStatusFilterAndPagination() throws Exception {
        Fixture fixture = createFixture("L");
        Fixture otherFixture = createFixture("O");
        Mission draftMission = saveVisitCountMission(fixture);
        Mission pendingReviewMission = saveVisitCountMission(fixture);
        Mission publishedMission = saveVisitCountMission(fixture);
        Mission endedMission = saveVisitCountMission(fixture);
        saveVisitCountMission(otherFixture);
        updateMissionStatus(pendingReviewMission, MissionStatus.PENDING_REVIEW);
        updateMissionStatus(publishedMission, MissionStatus.PUBLISHED);
        updateMissionStatus(endedMission, MissionStatus.ENDED);
        entityManager.clear();

        getMissions(fixture.admin(), null, "0", "2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].missionId").value(endedMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[1].missionId").value(publishedMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.totalElements").value(4))
            .andExpect(jsonPath("$.data.totalPages").value(2));
        getMissions(fixture.admin(), MissionStatus.PENDING_REVIEW.name(), "0", "20")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].missionId").value(pendingReviewMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].status").value("PENDING_REVIEW"));
        getMissions(otherFixture.admin(), null, "0", "20")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1));
        getMissions(saveUser("visitor", AppUserStatus.ACTIVE), null, "0", "20")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/region-admin/missions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(draftMission.getMissionId()).isLessThan(pendingReviewMission.getMissionId());
    }

    @Test
    void getMissions_withNoMission_returnsEmptyPage() throws Exception {
        Fixture fixture = createFixture("E");

        getMissions(fixture.admin(), null, "0", "20")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void getDetail_withContentSetMission_returnsContractFieldsWithoutOperatorTimestamps() throws Exception {
        Fixture fixture = createFixture("C");
        Content firstTargetContent = saveContent(fixture.region(), fixture.admin(), "first-target");
        Content secondTargetContent = saveContent(fixture.region(), fixture.admin(), "second-target");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(
            saveContent(fixture.region(), fixture.admin(), "reward"),
            fixture.region()
        );
        Mission mission = new Mission(
            fixture.region(),
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        );
        mission.addTargetContent(secondTargetContent);
        mission.addTargetContent(firstTargetContent);
        mission = missionRepository.saveAndFlush(mission);
        entityManager.clear();

        getDetail(fixture.admin(), mission.getMissionId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("지역 미션 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.regionId").value(fixture.region().getRegionId().toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value(firstTargetContent.getContentId().toString()))
            .andExpect(jsonPath("$.data.targetContents[0].title").value(firstTargetContent.getTitle()))
            .andExpect(jsonPath("$.data.targetContents[1].contentId").value(secondTargetContent.getContentId().toString()))
            .andExpect(jsonPath("$.data.rewardCouponPolicyId").value(rewardCouponPolicy.getCouponPolicyId().toString()))
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.publishedAt").doesNotExist())
            .andExpect(jsonPath("$.data.endedAt").doesNotExist());
    }

    @Test
    void getDetail_withVisitCountMission_returnsRequiredVisitCountAndEmptyTargetContents() throws Exception {
        Fixture fixture = createFixture("V");
        Mission mission = saveVisitCountMission(fixture);

        getDetail(fixture.admin(), mission.getMissionId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conditionType").value("VISIT_COUNT"))
            .andExpect(jsonPath("$.data.requiredVisitCount").value(3))
            .andExpect(jsonPath("$.data.targetContents").isEmpty())
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"));
    }

    @Test
    void getDetail_returnsAllMissionStatuses() throws Exception {
        Fixture fixture = createFixture("S");
        for (MissionStatus missionStatus : MissionStatus.values()) {
            Mission mission = saveVisitCountMission(fixture);
            updateMissionStatus(mission, missionStatus);
            entityManager.clear();

            getDetail(fixture.admin(), mission.getMissionId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(missionStatus.name()));
        }
    }

    @Test
    void getDetail_withOtherRegionAdmin_returnsForbidden() throws Exception {
        Fixture fixture = createFixture("A");
        Fixture otherFixture = createFixture("B");
        Mission mission = saveVisitCountMission(fixture);

        getDetail(otherFixture.admin(), mission.getMissionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getDetail_withoutRegionAdminRole_returnsForbidden() throws Exception {
        Fixture fixture = createFixture("N");
        AppUser user = saveUser("visitor", AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(fixture);

        getDetail(user, mission.getMissionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getDetail_withMissingMission_returnsNotFound() throws Exception {
        Fixture fixture = createFixture("M");

        getDetail(fixture.admin(), "999999999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getDetail_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/missions/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getDetail_withInvalidMissionId_returnsContractError() throws Exception {
        Fixture fixture = createFixture("I");

        getDetail(fixture.admin(), "01")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        getDetail(fixture.admin(), "not-a-number")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private ResultActions getDetail(AppUser user, String missionId) throws Exception {
        return mockMvc.perform(get("/api/v1/region-admin/missions/{missionId}", missionId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private ResultActions getMissions(
        AppUser user,
        String status,
        String page,
        String size
    ) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = get("/api/v1/region-admin/missions")
            .param("page", page)
            .param("size", size)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId()));
        if (status != null) {
            requestBuilder.param("status", status);
        }
        return mockMvc.perform(requestBuilder);
    }

    private Fixture createFixture(String prefix) {
        Region region = saveRegion(prefix);
        AppUser admin = saveUser("admin", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        return new Fixture(region, admin);
    }

    private Mission saveVisitCountMission(Fixture fixture) {
        Content rewardContent = saveContent(fixture.region(), fixture.admin(), "reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, fixture.region());
        return missionRepository.saveAndFlush(new Mission(
            fixture.region(),
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));
    }

    private void updateMissionStatus(
        Mission mission,
        MissionStatus status
    ) {
        Instant publishedAt = null;
        Instant endedAt = null;
        if (status == MissionStatus.PUBLISHED || status == MissionStatus.ENDED) {
            publishedAt = MISSION_PUBLISHED_AT;
        }
        if (status == MissionStatus.ENDED) {
            endedAt = MISSION_ENDED_AT;
        }
        entityManager.createNativeQuery("""
            UPDATE mission
            SET status = :status,
                published_at = :publishedAt,
                ended_at = :endedAt
            WHERE mission_id = :missionId
            """)
            .setParameter("status", status.name())
            .setParameter("publishedAt", publishedAt)
            .setParameter("endedAt", endedAt)
            .setParameter("missionId", mission.getMissionId())
            .executeUpdate();
    }

    private Region saveRegion(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(prefix + suffix, "Test region", true));
    }

    private AppUser saveUser(
        String prefix,
        AppUserStatus status
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "-" + suffix + "@example.com",
            "hashed-password",
            "Test user",
            "010-1234-5678",
            status
        ));
    }

    private Content saveContent(
        Region region,
        AppUser owner,
        String suffix
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            suffix + " content",
            "Mission test content description",
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

    private CouponPolicy saveMissionRewardCouponPolicy(
        Content content,
        Region region
    ) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "Mission reward coupon",
            "Mission completion reward coupon",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            CONTENT_PUBLISHED_AT,
            COUPON_ISSUE_ENDS_AT,
            100L
        ));
    }

    private record Fixture(
        Region region,
        AppUser admin
    ) {
    }
}
