package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
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
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
class EndMissionsUseCaseIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final Instant PUBLISHED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2020-01-02T00:00:00Z");

    private final EndMissionsUseCase endMissionsUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final AuditEventRepository auditEventRepository;

    @MockitoSpyBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    EndMissionsUseCaseIntegrationTest(
        EndMissionsUseCase endMissionsUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository missionParticipationRepository,
        AuditEventRepository auditEventRepository
    ) {
        this.endMissionsUseCase = endMissionsUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.missionParticipationRepository = missionParticipationRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void endBySystem_종료대상미션과진행중참여와성공감사를함께저장한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.randomUUID();

        assertThat(endMissionsUseCase.findAutoEndCandidateIds()).contains(fixture.missionId());

        EndMissionSystemResult result = endMissionsUseCase.endBySystem(fixture.missionId(), requestId);

        assertThat(result.status()).isEqualTo(EndMissionSystemResult.Status.ENDED);
        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ENDED);
        assertThat(mission.getEndedAt()).isNotNull().isAfterOrEqualTo(ENDS_AT);
        assertThat(missionParticipationRepository.findById(fixture.inProgressParticipationId()).orElseThrow()
            .getStatus()).isEqualTo(MissionParticipationStatus.ENDED_INCOMPLETE);
        assertThat(missionParticipationRepository.findById(fixture.completedParticipationId()).orElseThrow()
            .getStatus()).isEqualTo(MissionParticipationStatus.COMPLETED);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getRequestId()).isEqualTo(requestId.toString());
            assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(event.getPreviousState()).isEqualTo(MissionStatus.PUBLISHED.name());
            assertThat(event.getNextState()).isEqualTo(MissionStatus.ENDED.name());
            assertThat(event.getReasonCode()).isEqualTo("MISSION_END_TIME_REACHED");
            assertThat(event.getActorKind()).isEqualTo("SYSTEM");
        });
    }

    @Test
    void endBySystem_성공감사기록에실패하면미션종료와참여정리를롤백하고실패감사를기록한다()
        throws Exception {
        Fixture fixture = createFixture();
        RecordAuditEventUseCase target = AopTestUtils.getTargetObject(recordAuditEventUseCase);
        doThrow(new IllegalStateException("감사 저장 실패"))
            .when(target)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> endMissionsUseCase.endBySystem(fixture.missionId(), UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);

        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
        assertThat(mission.getEndedAt()).isNull();
        assertThat(missionParticipationRepository.findById(fixture.inProgressParticipationId()).orElseThrow()
            .getStatus()).isEqualTo(MissionParticipationStatus.IN_PROGRESS);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(event.getReasonCode()).isEqualTo("MISSION_AUTO_END_FAILED");
            assertThat(event.getPreviousState()).isEqualTo(MissionStatus.PUBLISHED.name());
            assertThat(event.getNextState()).isNull();
            assertThat(event.getActorKind()).isEqualTo("SYSTEM");
        });
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("AUTO-" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        AppUser firstVisitor = appUserRepository.saveAndFlush(new AppUser(
            "first-visitor-" + suffix + "@example.com",
            "hashed-password",
            "진행 사용자",
            "010-1234-5679",
            AppUserStatus.ACTIVE
        ));
        AppUser completedVisitor = appUserRepository.saveAndFlush(new AppUser(
            "completed-visitor-" + suffix + "@example.com",
            "hashed-password",
            "완료 사용자",
            "010-1234-5680",
            AppUserStatus.ACTIVE
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "보상 콘텐츠",
            "미션 보상용 콘텐츠",
            "김해시",
            "10:00-18:00",
            "055-1234-5678",
            "공지",
            "전체",
            "없음",
            "취소 정책",
            PUBLISHED_AT
        ));
        CouponPolicy rewardPolicy = new CouponPolicy(
            content,
            region,
            "미션 보상",
            null,
            CouponIssuanceType.MISSION_REWARD,
            1_000,
            1_000,
            7,
            PUBLISHED_AT,
            Instant.parse("2037-12-31T00:00:00Z"),
            null
        );
        rewardPolicy.publish(PUBLISHED_AT);
        rewardPolicy = couponPolicyRepository.saveAndFlush(rewardPolicy);
        Mission mission = new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            2,
            rewardPolicy,
            ENDS_AT
        );
        mission.submitForReview();
        mission.approve(PUBLISHED_AT);
        mission = missionRepository.saveAndFlush(mission);
        MissionParticipation inProgress = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(mission, firstVisitor, PUBLISHED_AT)
        );
        MissionParticipation completed = new MissionParticipation(mission, completedVisitor, PUBLISHED_AT);
        completed.complete(PUBLISHED_AT.plusSeconds(60));
        completed = missionParticipationRepository.saveAndFlush(completed);
        return new Fixture(
            mission.getMissionId(),
            inProgress.getMissionParticipationId(),
            completed.getMissionParticipationId()
        );
    }

    private record Fixture(
        Long missionId,
        Long inProgressParticipationId,
        Long completedParticipationId
    ) {
    }
}
