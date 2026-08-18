package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
@Sql(statements = """
    CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR "io.regionevent.regioneventbackend.support.jpa.H2MySqlCompatibilityFunctions.unixTimestamp"
    """)
class RejectContentWithdrawalControllerIntegrationTest {

    private static final String NEW_IDEMPOTENCY_KEY =
        "f750266c-dcb6-4ca7-a3a4-5fd1382b5a48";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    RejectContentWithdrawalControllerIntegrationTest(
        MockMvc mockMvc,
        ObjectMapper objectMapper,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository,
        ContentRepository contentRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
    }

    @Test
    void 반려하면_요청과_감사와_actor_link를_같은_사건_시각으로_저장한다() throws Exception {
        Fixture fixture = createFixture();

        reject(fixture.admin(), fixture.withdrawalRequestId(), "  운영 근거 부족  ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.withdrawalRequestId")
                .value(fixture.withdrawalRequestId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectionReason").value("운영 근거 부족"))
            .andExpect(jsonPath("$.data.rejectedAt").isString());

        ContentWithdrawalRequest rejectedRequest = withdrawalRequestRepository.findById(
            fixture.withdrawalRequestId()
        ).orElseThrow();
        assertThat(rejectedRequest.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.REJECTED);
        assertThat(rejectedRequest.getReviewedBy().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(rejectedRequest.getRejectionReason()).isEqualTo("운영 근거 부족");
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType()
                == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.withdrawalRequestId());
                assertThat(event.getPreviousState()).isEqualTo("PENDING");
                assertThat(event.getNextState()).isEqualTo("REJECTED");
                assertThat(event.getReasonCode()).isEqualTo("CONTENT_WITHDRAWAL_REJECTED");
                assertThat(event.getActorRole()).isEqualTo("REGION_ADMIN");
                assertThat(event.getOccurredAt()).isEqualTo(rejectedRequest.getReviewedAt());
                assertThat(auditEventActorLinkRepository.findById(event.getAuditEventId()))
                    .hasValueSatisfying(link -> assertThat(link.getActor().getUserId())
                        .isEqualTo(fixture.admin().getUserId()));
            });
    }

    @Test
    void 같은_사유의_순차_재시도는_최초_응답을_반환하고_감사를_추가하지_않는다() throws Exception {
        Fixture fixture = createFixture();

        MvcResult first = reject(
            fixture.admin(),
            fixture.withdrawalRequestId(),
            "  운영 근거 부족  "
        ).andExpect(status().isOk()).andReturn();
        MvcResult retry = reject(
            fixture.admin(),
            fixture.withdrawalRequestId(),
            "운영 근거 부족"
        ).andExpect(status().isOk()).andReturn();

        JsonNode firstData = objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
        JsonNode retryData = objectMapper.readTree(retry.getResponse().getContentAsString()).path("data");
        assertThat(retryData).isEqualTo(firstData);
        assertThat(withdrawalRequestRepository.count()).isOne();
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType()
                == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .hasSize(1);
    }

    @Test
    void 반려된_뒤에는_새_멱등_키로_철회_요청을_다시_생성할_수_있다() throws Exception {
        Fixture fixture = createFixture();
        reject(fixture.admin(), fixture.withdrawalRequestId(), "운영 근거 부족")
            .andExpect(status().isOk());

        mockMvc.perform(post(
                "/api/v1/operator/contents/{contentId}/withdrawal-requests",
                fixture.contentId()
            )
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", NEW_IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"보완 후 재요청\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.requestReason").value("보완 후 재요청"));

        assertThat(withdrawalRequestRepository.findAll())
            .extracting(ContentWithdrawalRequest::getStatus)
            .containsExactlyInAnyOrder(
                ContentWithdrawalRequestStatus.REJECTED,
                ContentWithdrawalRequestStatus.PENDING
            );
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        AppUser operator = saveUser("operator-" + suffix);
        roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operator,
            UserRole.OPERATOR,
            region
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            now.minusSeconds(86_400)
        ));
        ContentWithdrawalRequest request = withdrawalRequestRepository.saveAndFlush(
            ContentWithdrawalRequest.createPending(
                content,
                operator,
                "a".repeat(64),
                "운영 계획 변경",
                now.minusSeconds(1_800)
            )
        );
        return new Fixture(
            admin,
            operator,
            content.getContentId(),
            request.getContentWithdrawalRequestId()
        );
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private org.springframework.test.web.servlet.ResultActions reject(
        AppUser admin,
        Long withdrawalRequestId,
        String reason
    ) throws Exception {
        return mockMvc.perform(post(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}/reject",
                withdrawalRequestId
            )
            .header("Authorization", bearerToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"reason":"%s"}
                """.formatted(reason)));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private record Fixture(
        AppUser admin,
        AppUser operator,
        Long contentId,
        Long withdrawalRequestId
    ) {
    }
}
