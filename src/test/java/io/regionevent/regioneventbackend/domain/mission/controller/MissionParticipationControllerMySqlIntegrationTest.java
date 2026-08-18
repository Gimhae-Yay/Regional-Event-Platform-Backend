package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import io.regionevent.regioneventbackend.domain.mission.repository.MissionProgressRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationDuplicateReadService;
import io.regionevent.regioneventbackend.domain.mission.service.MissionService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MissionParticipationControllerMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant FUTURE_ENDS_AT = Instant.parse("2037-01-01T00:00:00Z");
    private static final String PATH = "/api/v1/missions/{missionId}/participations";
    private static final int BOUNDARY_WINDOW_START_MICROSECOND = 50_000;
    private static final int BOUNDARY_WINDOW_END_MICROSECOND = 150_000;
    private static final int BOUNDARY_WINDOW_MAX_ATTEMPTS = 750;
    private static final long BOUNDARY_WINDOW_RETRY_MILLIS = 2L;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionService missionService;
    private final MissionParticipationRepository missionParticipationRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionRewardClaimRepository missionRewardClaimRepository;
    private final MissionParticipationDuplicateReadService duplicateReadService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private AppUserService appUserService;

    @Autowired
    MissionParticipationControllerMySqlIntegrationTest(
        MockMvc mockMvc,
        ObjectMapper objectMapper,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionService missionService,
        MissionParticipationRepository missionParticipationRepository,
        MissionProgressRepository missionProgressRepository,
        MissionRewardClaimRepository missionRewardClaimRepository,
        MissionParticipationDuplicateReadService duplicateReadService,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.missionService = missionService;
        this.missionParticipationRepository = missionParticipationRepository;
        this.missionProgressRepository = missionProgressRepository;
        this.missionRewardClaimRepository = missionRewardClaimRepository;
        this.duplicateReadService = duplicateReadService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void create_신규와반복요청은최초참여결과를반환하고진행도와보상을만들지않는다() throws Exception {
        Fixture fixture = createFixture(true, true);
        Instant before = missionService.findCurrentDatabaseTime().minusSeconds(1);

        MvcResult firstResponse = performCreate(fixture.visitor(), fixture.mission())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andReturn();
        String participationId = responseData(firstResponse).get("participationId").asString();
        markMissionEnded(fixture.mission());
        MvcResult repeatedResponse = performCreate(fixture.visitor(), fixture.mission())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andReturn();

        Instant after = missionService.findCurrentDatabaseTime().plusSeconds(1);
        MissionParticipation participation = missionParticipationRepository.findAll().getFirst();
        assertThat(responseData(repeatedResponse).get("participationId").asString()).isEqualTo(participationId);
        assertThat(participation.getMissionParticipationId().toString()).isEqualTo(participationId);
        assertThat(participation.getJoinedAt()).isBetween(before, after);
        assertThat(missionParticipationRepository.count()).isOne();
        assertThat(missionProgressRepository.count()).isZero();
        assertThat(missionRewardClaimRepository.count()).isZero();
    }

    @Test
    @Timeout(15)
    void create_동시요청은동일참여식별자와단일행로수렴한다() throws Exception {
        Fixture fixture = createFixture(true, true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = submitCreate(executorService, ready, start, fixture);
            Future<MvcResult> second = submitCreate(executorService, ready, start, fixture);
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> responses = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
            assertThat(responses)
                .extracting(response -> response.getResponse().getStatus())
                .containsOnly(201);
            String participationId = responseData(responses.getFirst()).get("participationId").asString();
            assertThat(responses)
                .extracting(response -> responseData(response).get("participationId").asString())
                .containsOnly(participationId);
        } finally {
            start.countDown();
        }

        assertThat(missionParticipationRepository.count()).isOne();
    }

    @Test
    @Timeout(15)
    void create_사용자행잠금선행이면참여커밋후탈퇴가완료된다() throws Exception {
        Fixture fixture = createFixture(true, true);
        CountDownLatch userLocked = new CountDownLatch(1);
        CountDownLatch releaseCreate = new CountDownLatch(1);

        doAnswer(invocation -> {
            Object lockedUser = invocation.callRealMethod();
            userLocked.countDown();
            await(releaseCreate);
            return lockedUser;
        }).when(appUserService).findActiveUserForUpdate(fixture.visitor().getUserId());

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> creation = executorService.submit(
                () -> performCreate(fixture.visitor(), fixture.mission()).andReturn()
            );
            assertThat(userLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<?> withdrawal = executorService.submit(
                () -> withdrawVisitor(fixture.visitor().getUserId())
            );
            try {
                assertThatThrownBy(() -> withdrawal.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            } finally {
                releaseCreate.countDown();
            }

            MvcResult response = creation.get(5, TimeUnit.SECONDS);
            assertThat(response.getResponse().getStatus()).isEqualTo(201);
            assertThat(responseCode(response)).isEqualTo("SUCCESS");
            withdrawal.get(5, TimeUnit.SECONDS);
        } finally {
            releaseCreate.countDown();
        }

        assertThat(appUserRepository.findById(fixture.visitor().getUserId())).isEmpty();
        assertThat(missionParticipationRepository.findAll()).singleElement().satisfies(participation ->
            assertThat(participation.getUser()).isNull()
        );
    }

    @Test
    @Timeout(15)
    void create_탈퇴잠금선행이면탈퇴커밋후새참여없이금지한다() throws Exception {
        Fixture fixture = createFixture(true, true);
        CountDownLatch withdrawalStarted = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);

        doAnswer(invocation -> {
            invocation.callRealMethod();
            withdrawalStarted.countDown();
            await(releaseWithdrawal);
            return null;
        }).when(appUserService).startWithdrawal(any(AppUser.class));

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> withdrawal = executorService.submit(
                () -> withdrawVisitor(fixture.visitor().getUserId())
            );
            assertThat(withdrawalStarted.await(3, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> creation = executorService.submit(
                () -> performCreate(fixture.visitor(), fixture.mission()).andReturn()
            );
            try {
                assertThatThrownBy(() -> creation.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            } finally {
                releaseWithdrawal.countDown();
            }

            withdrawal.get(5, TimeUnit.SECONDS);
            MvcResult response = creation.get(5, TimeUnit.SECONDS);
            assertThat(response.getResponse().getStatus()).isEqualTo(403);
            assertThat(responseCode(response)).isEqualTo("FORBIDDEN");
        } finally {
            releaseWithdrawal.countDown();
        }

        assertThat(appUserRepository.findById(fixture.visitor().getUserId())).isEmpty();
        assertThat(missionParticipationRepository.count()).isZero();
    }

    @Test
    void create_비공개지역은기존참여와중복복구조회에서도찾을수없음으로숨긴다() throws Exception {
        Fixture fixture = createFixture(true, true);
        missionParticipationRepository.saveAndFlush(new MissionParticipation(
            fixture.mission(),
            fixture.visitor(),
            PUBLISHED_AT.plusSeconds(1)
        ));
        fixture.region().changeVisibility(false);
        regionRepository.saveAndFlush(fixture.region());

        performCreate(fixture.visitor(), fixture.mission())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThatThrownBy(() -> duplicateReadService.find(
            fixture.mission().getMissionId(),
            fixture.visitor().getUserId()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
        );
    }

    @Test
    void create_미공개와종료미션은상태충돌을반환한다() throws Exception {
        Fixture draftFixture = createFixture(true, false);

        performCreate(draftFixture.visitor(), draftFixture.mission())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        Fixture endedFixture = createFixture(true, true);
        markMissionEnded(endedFixture.mission());

        performCreate(endedFixture.visitor(), endedFixture.mission())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));
        assertThat(missionParticipationRepository.count()).isZero();
    }

    @Test
    void create_소수초_종료경계를_지난_신규참여는_상태충돌을_반환한다() throws Exception {
        Fixture fixture = createFixture(true, true);
        setMissionEndsAtOneMicrosecondBeforeCurrentTime(fixture.mission());

        performCreate(fixture.visitor(), fixture.mission())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertThat(missionParticipationRepository.count()).isZero();
    }

    @Test
    void create_활성방문자가아니거나대상이없으면계약오류를반환한다() throws Exception {
        Fixture fixture = createFixture(true, true);
        AppUser nonVisitor = saveUser("non-visitor");

        performCreate(nonVisitor, fixture.mission())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(PATH, Long.MAX_VALUE)
                .header(AUTHORIZATION, bearerToken(fixture.visitor())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(missionParticipationRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
        AppUser visitor,
        Mission mission
    ) throws Exception {
        return mockMvc.perform(post(PATH, mission.getMissionId())
            .header(AUTHORIZATION, bearerToken(visitor)));
    }

    private Future<MvcResult> submitCreate(
        ExecutorService executorService,
        CountDownLatch ready,
        CountDownLatch start,
        Fixture fixture
    ) {
        return executorService.submit(() -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post(PATH, fixture.mission().getMissionId())
                    .header(AUTHORIZATION, bearerToken(fixture.visitor())))
                .andReturn();
        });
    }

    private JsonNode responseData(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        } catch (Exception exception) {
            throw new IllegalStateException("failed to parse mission participation response", exception);
        }
    }

    private String responseCode(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asString();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to parse mission participation response", exception);
        }
    }

    private Fixture createFixture(
        boolean isPublic,
        boolean published
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "테스트 지역", isPublic));
        AppUser operator = saveUser("operator-" + suffix);
        AppUser visitor = saveUser("visitor-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        Content rewardContent = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "미션 보상 콘텐츠",
            "미션 참여 API 통합 테스트를 위한 설명입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            PUBLISHED_AT
        ));
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            rewardContent,
            region,
            "미션 완주 쿠폰",
            "미션 완료 보상 쿠폰입니다.",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            PUBLISHED_AT,
            FUTURE_ENDS_AT,
            100L
        ));
        Mission mission = missionRepository.saveAndFlush(new Mission(
            "테스트 미션",
            region,
            MissionConditionType.VISIT_COUNT,
            1,
            rewardCouponPolicy,
            FUTURE_ENDS_AT
        ));
        if (published) {
            publishMission(mission);
        }
        return new Fixture(region, visitor, mission);
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "테스트 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void publishMission(Mission mission) {
        jdbcTemplate.update(
            """
            UPDATE mission
            SET status = 'PUBLISHED',
                published_at = ?,
                ended_at = NULL
            WHERE mission_id = ?
            """,
            Timestamp.from(PUBLISHED_AT),
            mission.getMissionId()
        );
    }

    private void markMissionEnded(Mission mission) {
        jdbcTemplate.update(
            """
            UPDATE mission
            SET status = 'ENDED',
                ended_at = ?
            WHERE mission_id = ?
            """,
            Timestamp.from(PUBLISHED_AT.plusSeconds(1)),
            mission.getMissionId()
        );
    }

    private void setMissionEndsAtOneMicrosecondBeforeCurrentTime(Mission mission) throws InterruptedException {
        for (int attempt = 0; attempt < BOUNDARY_WINDOW_MAX_ATTEMPTS; attempt++) {
            int updatedCount = jdbcTemplate.update(
                """
                UPDATE mission
                SET ends_at = CURRENT_TIMESTAMP(6) - INTERVAL 1 MICROSECOND
                WHERE mission_id = ?
                  AND MICROSECOND(CURRENT_TIMESTAMP(6)) BETWEEN ? AND ?
                """,
                mission.getMissionId(),
                BOUNDARY_WINDOW_START_MICROSECOND,
                BOUNDARY_WINDOW_END_MICROSECOND
            );
            if (updatedCount == 1) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(BOUNDARY_WINDOW_RETRY_MILLIS);
        }
        throw new IllegalStateException("failed to set mission end boundary within the current second");
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private void withdrawVisitor(Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            AppUser user = appUserService.findActiveUserForUpdate(userId).orElseThrow();
            appUserService.startWithdrawal(user);
            jdbcTemplate.update(
                """
                UPDATE user_role_assignment
                SET status = 'REVOKED',
                    revoked_at = CURRENT_TIMESTAMP(6),
                    revoke_reason_code = 'USER_WITHDRAWAL',
                    user_id = NULL
                WHERE user_id = ?
                """,
                userId
            );
            appUserService.delete(user);
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }

    private record Fixture(
        Region region,
        AppUser visitor,
        Mission mission
    ) {
    }
}
