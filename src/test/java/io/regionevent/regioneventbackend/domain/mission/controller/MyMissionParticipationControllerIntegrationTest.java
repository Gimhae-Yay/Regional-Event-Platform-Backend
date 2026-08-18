package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
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
class MyMissionParticipationControllerIntegrationTest {

    private static final String PATH = "/api/v1/me/mission-participations";
    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant EARLIER_JOINED_AT = Instant.parse("2026-08-07T05:00:00Z");
    private static final Instant TIED_JOINED_AT = Instant.parse("2026-08-08T05:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-09T05:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    MyMissionParticipationControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository missionParticipationRepository,
        JwtAccessTokenService jwtAccessTokenService
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.missionParticipationRepository = missionParticipationRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
    }

    @Test
    void 내_참여_목록은_고정정렬_상태필터_페이지와_명세응답을_반환한다() throws Exception {
        Fixture fixture = createFixture(true);
        Mission completedMission = saveMission(fixture, 3);
        Mission inProgressMission = saveMission(fixture, 2);
        Mission endedMission = saveMission(fixture, 1);
        Mission otherUserMission = saveMission(fixture, 1);
        MissionParticipation completedParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(completedMission, fixture.visitor(), EARLIER_JOINED_AT)
        );
        completedParticipation.complete(COMPLETED_AT);
        MissionParticipation inProgressParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(inProgressMission, fixture.visitor(), TIED_JOINED_AT)
        );
        MissionParticipation endedParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(endedMission, fixture.visitor(), TIED_JOINED_AT)
        );
        endedParticipation.endIncomplete();
        AppUser otherVisitor = saveUser("other-list-visitor@example.com", "다른 방문자");
        missionParticipationRepository.saveAndFlush(new MissionParticipation(
            otherUserMission,
            otherVisitor,
            TIED_JOINED_AT.plusSeconds(60)
        ));
        missionParticipationRepository.flush();

        performGet(fixture.visitor())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 참여 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(3))
            .andExpect(jsonPath("$.data.content[0].participationId").value(
                endedParticipation.getMissionParticipationId().toString()
            ))
            .andExpect(jsonPath("$.data.content[0].missionId").value(endedMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].status").value("ENDED_INCOMPLETE"))
            .andExpect(jsonPath("$.data.content[0].progressCount").value(0))
            .andExpect(jsonPath("$.data.content[0].requiredCount").value(1))
            .andExpect(jsonPath("$.data.content[0].rewardClaimed").value(false))
            .andExpect(jsonPath("$.data.content[0].joinedAt").value("2026-08-08T05:00:00Z"))
            .andExpect(jsonPath("$.data.content[0].completedAt").doesNotExist())
            .andExpect(jsonPath("$.data.content[1].participationId").value(
                inProgressParticipation.getMissionParticipationId().toString()
            ))
            .andExpect(jsonPath("$.data.content[2].participationId").value(
                completedParticipation.getMissionParticipationId().toString()
            ))
            .andExpect(jsonPath("$.data.content[2].completedAt").value("2026-08-09T05:00:00Z"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        performGet(fixture.visitor(), "status", "COMPLETED", "page", "0", "size", "1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].participationId").value(
                completedParticipation.getMissionParticipationId().toString()
            ))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        performGet(fixture.visitor(), "page", "1", "size", "2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].participationId").value(
                completedParticipation.getMissionParticipationId().toString()
            ))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void 참여가_없으면_빈_배열과_0인_페이지_메타데이터를_반환한다() throws Exception {
        Fixture fixture = createFixture(true);

        performGet(fixture.visitor())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void 페이지_경계와_타입이_유효하지_않으면_계약_오류를_반환한다() throws Exception {
        Fixture fixture = createFixture(true);

        performGet(fixture.visitor(), "page", "-1")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performGet(fixture.visitor(), "size", "101")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performGet(fixture.visitor(), "page", "not-a-number")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 미인증과_비활성_방문자는_계약_오류를_반환한다() throws Exception {
        Fixture inactiveFixture = createFixture(false);

        mockMvc.perform(get(PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        performGet(inactiveFixture.visitor())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private ResultActions performGet(AppUser user, String... parameters) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = get(PATH)
            .header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId()));
        for (int index = 0; index < parameters.length; index += 2) {
            request.param(parameters[index], parameters[index + 1]);
        }
        return mockMvc.perform(request);
    }

    private Fixture createFixture(boolean activeVisitor) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix + "@example.com", "미션 운영자");
        AppUser visitor = saveUser("visitor-" + suffix + "@example.com", "방문자");
        UserRoleAssignment visitorRole = new UserRoleAssignment(visitor, UserRole.VISITOR, null);
        if (!activeVisitor) {
            visitorRole.revoke(ISSUE_STARTS_AT, "TEST_INACTIVE_VISITOR");
        }
        userRoleAssignmentRepository.saveAndFlush(visitorRole);
        Content content = contentRepository.saveAndFlush(new Content(
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
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "미션 완료 보상",
            "미션 완료 시 지급하는 쿠폰입니다.",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
        return new Fixture(visitor, region, rewardCouponPolicy);
    }

    private Mission saveMission(Fixture fixture, int requiredVisitCount) {
        return missionRepository.saveAndFlush(new Mission(
            "테스트 미션",
            fixture.region(),
            MissionConditionType.VISIT_COUNT,
            requiredVisitCount,
            fixture.rewardCouponPolicy(),
            MISSION_ENDS_AT
        ));
    }

    private AppUser saveUser(String loginIdentifier, String name) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record Fixture(
        AppUser visitor,
        Region region,
        CouponPolicy rewardCouponPolicy
    ) {
    }
}
