package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class PublicRegionMissionControllerIntegrationTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant CURRENT_TIME = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant FUTURE_ENDS_AT = Instant.parse("2030-09-30T14:59:59Z");
    private static final Instant LATER_ENDS_AT = Instant.parse("2030-10-31T14:59:59Z");
    private static final Instant PAST_ENDS_AT = Instant.parse("2026-08-02T00:00:00Z");

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicRegionMissionControllerIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository missionParticipationRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.missionParticipationRepository = missionParticipationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void getPublicRegionMissions_anonymous_returnsOnlyPublishedAndNotEndedMissions() throws Exception {
        Fixture fixture = createFixture(true);
        Mission first = saveContentSetMission(fixture, "first", FUTURE_ENDS_AT);
        Mission second = saveVisitCountMission(fixture, "second", LATER_ENDS_AT, 3);
        saveVisitCountMission(fixture, "draft", FUTURE_ENDS_AT, 5);
        Mission ended = saveVisitCountMission(fixture, "ended", PAST_ENDS_AT, 7);
        Mission endingNow = saveVisitCountMission(fixture, "ending-now", CURRENT_TIME, 9);
        publishMission(first, PUBLISHED_AT);
        publishMission(second, PUBLISHED_AT);
        publishMission(ended, PUBLISHED_AT);
        publishMission(endingNow, PUBLISHED_AT);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 미션 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.content[0].missionId").value(first.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].title").value("테스트 미션"))
            .andExpect(jsonPath("$.data.content[0].conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.content[0].requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.content[0].targetContentCount").value(3))
            .andExpect(jsonPath("$.data.content[0].endsAt").value("2030-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.content[0].participationStatus").isEmpty())
            .andExpect(jsonPath("$.data.content[1].missionId").value(second.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[1].title").value("테스트 미션"))
            .andExpect(jsonPath("$.data.content[1].requiredVisitCount").value(3))
            .andExpect(jsonPath("$.data.content[1].targetContentCount").value(0))
            .andExpect(jsonPath("$.data.content[2]").doesNotExist());
    }

    @Test
    void getPublicRegionMissions_authenticated_returnsParticipationStatus() throws Exception {
        Fixture fixture = createFixture(true);
        Mission mission = saveVisitCountMission(fixture, "participating", FUTURE_ENDS_AT, 3);
        publishMission(mission, PUBLISHED_AT);
        missionParticipationRepository.saveAndFlush(new MissionParticipation(
            mission,
            fixture.visitor(),
            PUBLISHED_AT.plusSeconds(60)
        ));
        String accessToken = AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, fixture.visitor().getUserId());

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId())
                .header(AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].participationStatus").value("IN_PROGRESS"));
    }

    @Test
    void getPublicRegionMissions_authenticatedWithOtherUser_doesNotReturnParticipationStatus() throws Exception {
        Fixture fixture = createFixture(true);
        Mission mission = saveVisitCountMission(fixture, "other-user-participating", FUTURE_ENDS_AT, 3);
        publishMission(mission, PUBLISHED_AT);
        missionParticipationRepository.saveAndFlush(new MissionParticipation(
            mission,
            fixture.visitor(),
            PUBLISHED_AT.plusSeconds(60)
        ));
        String accessToken = AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, fixture.operator().getUserId());

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId())
                .header(AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].participationStatus").isEmpty());
    }

    @Test
    void getPublicRegionMissions_sameEndsAt_returnsMissionIdAscending() throws Exception {
        Fixture fixture = createFixture(true);
        Mission first = saveVisitCountMission(fixture, "same-ends-at-first", FUTURE_ENDS_AT, 1);
        Mission second = saveVisitCountMission(fixture, "same-ends-at-second", FUTURE_ENDS_AT, 2);
        publishMission(second, PUBLISHED_AT);
        publishMission(first, PUBLISHED_AT);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].missionId").value(first.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[1].missionId").value(second.getMissionId().toString()));
    }

    @Test
    void getPublicRegionMissions_emptyResult_returnsEmptyPage() throws Exception {
        Fixture fixture = createFixture(true);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void getPublicRegionMissions_returnsRequestedPage() throws Exception {
        Fixture fixture = createFixture(true);
        Mission first = saveVisitCountMission(fixture, "first-page", FUTURE_ENDS_AT, 1);
        Mission second = saveVisitCountMission(fixture, "second-page", LATER_ENDS_AT, 2);
        publishMission(first, PUBLISHED_AT);
        publishMission(second, PUBLISHED_AT);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId())
                .queryParam("page", "1")
                .queryParam("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].missionId").value(second.getMissionId().toString()))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getPublicRegionMissions_nonPublicRegion_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(false);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getPublicRegionMissions_invalidBearerToken_returnsUnauthenticated() throws Exception {
        Fixture fixture = createFixture(true);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId())
                .header(AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getPublicRegionMissions_blankAuthorizationHeader_returnsUnauthenticated() throws Exception {
        Fixture fixture = createFixture(true);

        mockMvc.perform(get("/api/v1/regions/{regionId}/missions", fixture.region().getRegionId())
                .header(AUTHORIZATION, "   "))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private Fixture createFixture(boolean isPublic) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "테스트 지역", isPublic));
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        AppUser visitor = saveUser("visitor-" + suffix + "@example.com");
        return new Fixture(region, operator, visitor, suffix);
    }

    private Mission saveContentSetMission(
        Fixture fixture,
        String suffix,
        Instant endsAt
    ) {
        Content rewardContent = saveContent(fixture, suffix + "-reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(fixture, rewardContent, suffix);
        Mission mission = new Mission(
            "테스트 미션",
            fixture.region(),
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            endsAt
        );
        mission.addTargetContent(saveContent(fixture, suffix + "-first-target"));
        mission.addTargetContent(saveContent(fixture, suffix + "-second-target"));
        mission.addTargetContent(saveContent(fixture, suffix + "-third-target"));
        return missionRepository.saveAndFlush(mission);
    }

    private Mission saveVisitCountMission(
        Fixture fixture,
        String suffix,
        Instant endsAt,
        int requiredVisitCount
    ) {
        Content rewardContent = saveContent(fixture, suffix + "-reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(fixture, rewardContent, suffix);
        return missionRepository.saveAndFlush(new Mission(
            "테스트 미션",
            fixture.region(),
            MissionConditionType.VISIT_COUNT,
            requiredVisitCount,
            rewardCouponPolicy,
            endsAt
        ));
    }

    private Content saveContent(
        Fixture fixture,
        String suffix
    ) {
        return contentRepository.saveAndFlush(new Content(
            fixture.region(),
            fixture.operator(),
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            suffix + " 콘텐츠",
            "미션 목록 테스트 콘텐츠입니다.",
            "경상남도 김해시",
            "10:00~18:00",
            "055-1234-5678",
            "안전 수칙을 지켜주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            PUBLISHED_AT
        ));
    }

    private CouponPolicy saveMissionRewardCouponPolicy(
        Fixture fixture,
        Content content,
        String suffix
    ) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            fixture.region(),
            suffix + " 미션 보상 쿠폰",
            "미션 완료 보상 쿠폰입니다.",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            PUBLISHED_AT,
            LATER_ENDS_AT,
            100L
        ));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "방문자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void publishMission(
        Mission mission,
        Instant publishedAt
    ) {
        jdbcTemplate.update(
            """
            UPDATE mission
            SET status = 'PUBLISHED',
                published_at = ?,
                ended_at = NULL
            WHERE mission_id = ?
            """,
            Timestamp.from(publishedAt),
            mission.getMissionId()
        );
    }

    private record Fixture(
        Region region,
        AppUser operator,
        AppUser visitor,
        String suffix
    ) {
    }

    @TestConfiguration
    static class FixedClockTestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);
        }
    }
}
