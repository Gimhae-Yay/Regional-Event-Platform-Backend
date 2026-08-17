package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
@Sql(statements = """
    CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR "io.regionevent.regioneventbackend.support.jpa.H2MySqlCompatibilityFunctions.unixTimestamp"
    """)
class RequestContentWithdrawalControllerIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    RequestContentWithdrawalControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
    }

    @Test
    void 요청하면_정규화된_201_응답과_요청_감사를_저장한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        mockMvc.perform(post(path(fixture.content().getContentId()))
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"  운영 계획 변경  "}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체 콘텐츠 철회 요청을 등록했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequestId").isString())
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.requestReason").value("운영 계획 변경"))
            .andExpect(jsonPath("$.data.requestedAt").isString());

        assertThat(withdrawalRequestRepository.findAll()).singleElement().satisfies(request -> {
            assertThat(request.getContent().getContentId()).isEqualTo(fixture.content().getContentId());
            assertThat(request.getRequestedBy().getUserId()).isEqualTo(fixture.operator().getUserId());
            assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
            assertThat(request.getRequestReason()).isEqualTo("운영 계획 변경");
            assertThat(request.getIdempotencyKeyHash()).hasSize(64).doesNotContain(IDEMPOTENCY_KEY);
        });
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getPreviousState()).isNull();
                assertThat(event.getNextState()).isEqualTo("PENDING");
                assertThat(event.getReasonCode()).isEqualTo("CONTENT_WITHDRAWAL_REQUESTED");
                assertThat(event.getActorRole()).isEqualTo("OPERATOR");
                assertThat(auditEventActorLinkRepository.findById(event.getAuditEventId()))
                    .hasValueSatisfying(link -> assertThat(link.getActor().getUserId())
                        .isEqualTo(fixture.operator().getUserId()));
            });
    }

    @Test
    void 인증이나_멱등_키가_없으면_요청을_저장하지_않는다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        mockMvc.perform(post(path(fixture.content().getContentId()))
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"운영 계획 변경\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post(path(fixture.content().getContentId()))
                .header("Authorization", bearerToken(fixture.operator()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"운영 계획 변경\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(withdrawalRequestRepository.count()).isZero();
    }

    @Test
    void 잘못된_JSON은_요청을_저장하지_않는다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        mockMvc.perform(post(path(fixture.content().getContentId()))
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(withdrawalRequestRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "0, INVALID_INPUT",
        "-1, INVALID_INPUT",
        "01, INVALID_INPUT",
        "not-a-number, INVALID_TYPE",
        "9223372036854775808, INVALID_TYPE"
    })
    void 콘텐츠_ID_오류를_계약된_코드로_반환한다(String contentId, String expectedCode) throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        mockMvc.perform(post(path(contentId))
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"운영 계획 변경\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(expectedCode));

        assertThat(withdrawalRequestRepository.count()).isZero();
    }

    private Fixture createFixture(ContentStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operator,
            UserRole.OPERATOR,
            region
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.now().minusSeconds(86_400)
        ));
        return new Fixture(operator, content);
    }

    private String path(Object contentId) {
        return "/api/v1/operator/contents/" + contentId + "/withdrawal-requests";
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(AppUser operator, Content content) {
    }
}
