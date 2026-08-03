package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class ContentSessionCancelAuditAtomicityTest {

    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    ContentSessionCancelAuditAtomicityTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        AuditEventRepository auditEventRepository,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    @Test
    void cancelSession_whenAuditRecordingFails_rollsBackSessionAndHoldChanges() throws Exception {
        Fixture fixture = createFixture();
        CapacityHold activeHold = saveActiveHold(fixture);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        mockMvc.perform(post(
            "/api/v1/operator/sessions/{sessionId}/cancel",
            fixture.contentSession().getSessionId()
        )
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.operator().getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "cancellationReason": "Session cancelled"
                }
                """))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        entityManager.clear();
        ContentSession unchangedSession = contentSessionRepository
            .findById(fixture.contentSession().getSessionId())
            .orElseThrow();
        CapacityHold unchangedHold = capacityHoldRepository.findById(activeHold.getHoldId()).orElseThrow();
        assertThat(unchangedSession.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
        assertThat(unchangedSession.getCancelledAt()).isNull();
        assertThat(unchangedSession.getRemainingCapacity()).isEqualTo(10);
        assertThat(unchangedHold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(unchangedHold.getTerminalAt()).isNull();
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("ATOMIC-" + suffix, "Atomic", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "atomic-operator-" + suffix + "@example.com",
            "hashed-password",
            "Operator",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "Original title",
            "Original description",
            "Original location",
            "Original hours",
            "055-000-0000",
            "Original precautions",
            "Original age",
            "Original materials",
            "Original policy",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        ContentSession contentSession = new ContentSession(
            content,
            region,
            STARTS_AT,
            STARTS_AT.plusSeconds(7_200),
            STARTS_AT.minusSeconds(1_800),
            STARTS_AT.plusSeconds(5_400),
            10
        );
        contentSession.approve(operator, REVIEWED_AT);
        return new Fixture(
            region,
            operator,
            contentSessionRepository.saveAndFlush(contentSession)
        );
    }

    private CapacityHold saveActiveHold(Fixture fixture) {
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.contentSession(),
            fixture.operator(),
            2,
            CapacityHoldStatus.ACTIVE,
            STARTS_AT.minusSeconds(3_600),
            null,
            null,
            null
        ));
    }

    private record Fixture(
        Region region,
        AppUser operator,
        ContentSession contentSession
    ) {
    }
}
