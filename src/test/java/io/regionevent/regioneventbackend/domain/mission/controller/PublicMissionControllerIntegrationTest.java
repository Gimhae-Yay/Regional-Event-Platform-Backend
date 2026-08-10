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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class PublicMissionControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant FUTURE_ENDS_AT = Instant.parse("2030-09-30T14:59:59Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionParticipationRepository missionParticipationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void getPublicMission_publishedMission_returnsDetailAndOwnParticipationOnly() throws Exception {
        Fixture fixture = createFixture(true);
        Mission mission = saveContentSetMission(fixture, FUTURE_ENDS_AT);
        publishMission(mission);
        missionParticipationRepository.saveAndFlush(new MissionParticipation(mission, fixture.visitor(), NOW.minusSeconds(60)));

        mockMvc.perform(get("/api/v1/missions/{missionId}", mission.getMissionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.targetContents.length()").value(1))
            .andExpect(jsonPath("$.data.endsAt").value("2030-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.participation").isEmpty());
        mockMvc.perform(get("/api/v1/missions/{missionId}", mission.getMissionId())
                .header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.visitor().getUserId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participation.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.participation.progressCount").value(0))
            .andExpect(jsonPath("$.data.participation.requiredCount").value(1));
        mockMvc.perform(get("/api/v1/missions/{missionId}", mission.getMissionId())
                .header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.operator().getUserId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participation").isEmpty());
    }

    @Test
    void getPublicMission_privateOrNotPublicOrEnded_returnsNotFound() throws Exception {
        Fixture privateFixture = createFixture(false);
        Mission privateMission = saveVisitCountMission(privateFixture, FUTURE_ENDS_AT);
        publishMission(privateMission);
        Fixture publicFixture = createFixture(true);
        Mission draftMission = saveVisitCountMission(publicFixture, FUTURE_ENDS_AT);
        Mission pendingReviewMission = saveVisitCountMission(publicFixture, FUTURE_ENDS_AT);
        requestReview(pendingReviewMission);
        Mission endedMission = saveVisitCountMission(publicFixture, FUTURE_ENDS_AT);
        publishMission(endedMission);
        endMission(endedMission);
        Mission endingNowMission = saveVisitCountMission(publicFixture, NOW);
        publishMission(endingNowMission);

        assertNotFound(privateMission);
        assertNotFound(draftMission);
        assertNotFound(pendingReviewMission);
        assertNotFound(endedMission);
        assertNotFound(endingNowMission);
        mockMvc.perform(get("/api/v1/missions/999999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void assertNotFound(Mission mission) throws Exception {
        mockMvc.perform(get("/api/v1/missions/{missionId}", mission.getMissionId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private Fixture createFixture(boolean isPublic) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "Test region", isPublic));
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        AppUser visitor = saveUser("visitor-" + suffix + "@example.com");
        return new Fixture(region, operator, visitor);
    }

    private Mission saveContentSetMission(Fixture fixture, Instant endsAt) {
        Content rewardContent = saveContent(fixture, "reward");
        Mission mission = new Mission(
            fixture.region(), MissionConditionType.CONTENT_SET, null, saveRewardPolicy(fixture, rewardContent), endsAt
        );
        mission.addTargetContent(saveContent(fixture, "target"));
        return missionRepository.saveAndFlush(mission);
    }

    private Mission saveVisitCountMission(Fixture fixture, Instant endsAt) {
        return missionRepository.saveAndFlush(new Mission(
            fixture.region(), MissionConditionType.VISIT_COUNT, 3, saveRewardPolicy(fixture, saveContent(fixture, "reward")), endsAt
        ));
    }

    private Content saveContent(Fixture fixture, String suffix) {
        return contentRepository.saveAndFlush(new Content(
            fixture.region(), fixture.operator(), ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
            suffix + " content", "description", "address", "hours", "010-0000-0000", "notice", "age", "dress", "cancel", NOW
        ));
    }

    private CouponPolicy saveRewardPolicy(Fixture fixture, Content content) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content, fixture.region(), "reward", "reward description", CouponIssuanceType.MISSION_REWARD,
            3_000, 10_000, 30, NOW.minusSeconds(60), FUTURE_ENDS_AT, 100L
        ));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier, "hashed-password", "Visitor", "010-0000-0000", AppUserStatus.ACTIVE
        ));
    }

    private void publishMission(Mission mission) {
        jdbcTemplate.update(
            "UPDATE mission SET status = 'PUBLISHED', published_at = ?, ended_at = NULL WHERE mission_id = ?",
            Timestamp.from(NOW.minusSeconds(60)), mission.getMissionId()
        );
    }

    private void requestReview(Mission mission) {
        jdbcTemplate.update(
            "UPDATE mission SET status = 'PENDING_REVIEW' WHERE mission_id = ?",
            mission.getMissionId()
        );
    }

    private void endMission(Mission mission) {
        jdbcTemplate.update(
            "UPDATE mission SET status = 'ENDED', ended_at = ? WHERE mission_id = ?",
            Timestamp.from(NOW), mission.getMissionId()
        );
    }

    private record Fixture(Region region, AppUser operator, AppUser visitor) {
    }

    @TestConfiguration
    static class FixedClockTestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
