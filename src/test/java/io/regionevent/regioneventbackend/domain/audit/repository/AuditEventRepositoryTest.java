package io.regionevent.regioneventbackend.domain.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class AuditEventRepositoryTest {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuditEventRepositoryTest(
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void saveAuditEvent_withoutRegion_storesAuditEvent() {
        AuditEvent savedAuditEvent = auditEventRepository.saveAndFlush(
            createAuditEvent(null, AuditEventResult.SUCCESS)
        );

        assertThat(savedAuditEvent.getAuditEventId()).isNotNull();
        assertThat(savedAuditEvent.getRegion()).isNull();
    }

    @Test
    void findById_regionRelation_isLazyLoaded() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "Gimhae", true));
        AuditEvent auditEvent = auditEventRepository.saveAndFlush(
            createAuditEvent(region, AuditEventResult.SUCCESS)
        );
        entityManager.clear();

        AuditEvent foundAuditEvent = auditEventRepository.findById(auditEvent.getAuditEventId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory()
            .getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundAuditEvent, "region")).isFalse();
        assertThat(foundAuditEvent.getRegion().getRegionId()).isEqualTo(region.getRegionId());
    }

    @Test
    void saveAuditEvent_storesSuccessAndFailureResults() {
        AuditEvent successAuditEvent = auditEventRepository.saveAndFlush(
            createAuditEvent(null, AuditEventResult.SUCCESS)
        );
        AuditEvent failureAuditEvent = auditEventRepository.saveAndFlush(
            createAuditEvent(null, AuditEventResult.FAILURE)
        );

        assertThat(successAuditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(failureAuditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
    }

    @Test
    void saveAuditEventActorLink_storesActorInSeparateLinkTable() {
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "actor@example.com",
            "password-hash",
            "Audit Actor",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        AuditEvent auditEvent = auditEventRepository.saveAndFlush(
            createAuditEvent(null, AuditEventResult.SUCCESS)
        );

        AuditEventActorLink savedActorLink = auditEventActorLinkRepository.saveAndFlush(
            new AuditEventActorLink(auditEvent, actor)
        );
        entityManager.clear();

        AuditEventActorLink actorLink = auditEventActorLinkRepository.findById(
            auditEvent.getAuditEventId()
        ).orElseThrow();
        List<String> auditEventColumns = jdbcTemplate.query(
            """
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'AUDIT_EVENT'
                """,
            (resultSet, rowNumber) -> resultSet.getString("COLUMN_NAME")
        );

        assertThat(savedActorLink.getAuditEventId()).isEqualTo(auditEvent.getAuditEventId());
        assertThat(actorLink.getActor().getUserId()).isEqualTo(actor.getUserId());
        assertThat(auditEventColumns).doesNotContain("USER_ID");
    }

    @Test
    void findMissionHistoryAuditProjections_filtersBoundaryOrdersAndLeftJoinsActor() {
        Region region = regionRepository.saveAndFlush(new Region("MISSION-HISTORY", "Gimhae", true));
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "mission-history-actor@example.com",
            "password-hash",
            "Mission history actor",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        Instant databaseNow = jdbcTemplate.queryForObject(
            "SELECT CURRENT_TIMESTAMP",
            OffsetDateTime.class
        ).toInstant();
        Instant cutoff = databaseNow.minusSeconds(90L * 24 * 60 * 60);
        Instant sameOccurredAt = databaseNow.minusSeconds(60);
        AuditEvent boundaryEvent = auditEventRepository.saveAndFlush(createMissionAuditEvent(
            region,
            701L,
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            cutoff
        ));
        AuditEvent sameTimeFirstEvent = auditEventRepository.saveAndFlush(createMissionAuditEvent(
            region,
            701L,
            "DRAFT",
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_UPDATED",
            "USER",
            sameOccurredAt
        ));
        AuditEvent sameTimeSecondEvent = auditEventRepository.saveAndFlush(createMissionAuditEvent(
            region,
            701L,
            "DRAFT",
            "PENDING_REVIEW",
            AuditEventResult.SUCCESS,
            "MISSION_SUBMITTED",
            "USER",
            sameOccurredAt
        ));
        auditEventActorLinkRepository.saveAndFlush(new AuditEventActorLink(boundaryEvent, actor));
        auditEventRepository.saveAndFlush(createMissionAuditEvent(
            region,
            701L,
            "DRAFT",
            "PENDING_REVIEW",
            AuditEventResult.FAILURE,
            "MISSION_SUBMITTED",
            "USER",
            databaseNow.minusSeconds(120)
        ));
        auditEventRepository.saveAndFlush(createMissionAuditEvent(
            region,
            701L,
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            cutoff.minusSeconds(1)
        ));
        auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000099",
            region,
            AuditEventTargetType.CONTENT,
            701L,
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            databaseNow.minusSeconds(30)
        ));
        auditEventRepository.saveAndFlush(createMissionAuditEvent(
            region,
            702L,
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            databaseNow.minusSeconds(30)
        ));
        entityManager.clear();

        List<MissionHistoryAuditProjection> projections = auditEventRepository
            .findMissionHistoryAuditProjections(701L);

        assertThat(projections)
            .extracting(MissionHistoryAuditProjection::auditEventId)
            .containsExactly(
                boundaryEvent.getAuditEventId(),
                sameTimeFirstEvent.getAuditEventId(),
                sameTimeSecondEvent.getAuditEventId()
            );
        assertThat(projections.getFirst().actorUserId()).isEqualTo(actor.getUserId());
        assertThat(projections.get(1).actorUserId()).isNull();
        assertThat(projections.getFirst().previousState()).isNull();
        assertThat(projections.getFirst().occurredAt()).isEqualTo(cutoff);
    }

    @Test
    void findQrExceptionReadProjections_excludesQrCheckInSuccessReason() {
        Region region = regionRepository.saveAndFlush(new Region("QR-EXCEPTION-1", "Gimhae", true));
        auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000101",
            region,
            AuditEventTargetType.RESERVATION,
            null,
            AuditEventResult.SUCCESS,
            "QR_CHECK_IN_SUCCESS",
            Instant.parse("2026-08-01T00:00:02Z")
        ));
        AuditEvent failureAuditEvent = auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000102",
            region,
            AuditEventTargetType.RESERVATION,
            null,
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SIGNATURE_INVALID",
            Instant.parse("2026-08-01T00:00:01Z")
        ));

        List<QrExceptionReadProjection> projections = auditEventRepository.findQrExceptionReadProjections(
            region.getRegionId(),
            Instant.parse("2026-07-01T00:00:00Z"),
            null,
            null,
            "QR_CHECK_IN_",
            "QR_CHECK_IN_SUCCESS",
            "QR_VERIFICATION_FAILED",
            "MANUAL_CHECK_IN_",
            PageRequest.of(0, 20)
        );

        assertThat(projections)
            .extracting(QrExceptionReadProjection::auditEventId)
            .containsExactly(failureAuditEvent.getAuditEventId());
    }

    @Test
    void existsQrExceptionCursorBoundary_excludesQrCheckInSuccessReason() {
        Region region = regionRepository.saveAndFlush(new Region("QR-EXCEPTION-2", "Gimhae", true));
        AuditEvent successAuditEvent = auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000201",
            region,
            AuditEventTargetType.RESERVATION,
            null,
            AuditEventResult.SUCCESS,
            "QR_CHECK_IN_SUCCESS",
            Instant.parse("2026-08-01T00:00:02Z")
        ));
        AuditEvent failureAuditEvent = auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000202",
            region,
            AuditEventTargetType.RESERVATION,
            null,
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SIGNATURE_INVALID",
            Instant.parse("2026-08-01T00:00:01Z")
        ));

        boolean successBoundaryExists = auditEventRepository.existsQrExceptionCursorBoundary(
            region.getRegionId(),
            successAuditEvent.getOccurredAt(),
            successAuditEvent.getAuditEventId(),
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            "QR_CHECK_IN_",
            "QR_CHECK_IN_SUCCESS",
            "QR_VERIFICATION_FAILED",
            "MANUAL_CHECK_IN_"
        );
        boolean failureBoundaryExists = auditEventRepository.existsQrExceptionCursorBoundary(
            region.getRegionId(),
            failureAuditEvent.getOccurredAt(),
            failureAuditEvent.getAuditEventId(),
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            "QR_CHECK_IN_",
            "QR_CHECK_IN_SUCCESS",
            "QR_VERIFICATION_FAILED",
            "MANUAL_CHECK_IN_"
        );

        assertThat(successBoundaryExists).isFalse();
        assertThat(failureBoundaryExists).isTrue();
    }

    @Test
    void findQrExceptionAuditProjectionById_qrExceptionReason_returnsProjection() {
        Region region = regionRepository.saveAndFlush(new Region("QR-EXCEPTION-3", "Gimhae", true));
        AuditEvent auditEvent = auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000301",
            region,
            AuditEventTargetType.RESERVATION,
            123L,
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SIGNATURE_INVALID",
            Instant.parse("2026-08-01T01:02:00Z")
        ));

        QrExceptionAuditProjection projection = auditEventRepository
            .findQrExceptionAuditProjectionById(auditEvent.getAuditEventId())
            .orElseThrow();

        assertThat(projection.exceptionId()).isEqualTo(auditEvent.getAuditEventId());
        assertThat(projection.regionId()).isEqualTo(region.getRegionId());
        assertThat(projection.targetType()).isEqualTo(AuditEventTargetType.RESERVATION);
        assertThat(projection.targetId()).isEqualTo(123L);
        assertThat(projection.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(projection.reasonCode()).isEqualTo("QR_CHECK_IN_SIGNATURE_INVALID");
        assertThat(projection.occurredAt()).isEqualTo(Instant.parse("2026-08-01T01:02:00Z"));
    }

    @Test
    void findQrExceptionAuditProjectionById_nonQrExceptionReason_returnsEmpty() {
        Region region = regionRepository.saveAndFlush(new Region("QR-EXCEPTION-4", "Gimhae", true));
        AuditEvent contentAuditEvent = auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000401",
            region,
            AuditEventTargetType.CONTENT,
            101L,
            AuditEventResult.SUCCESS,
            null,
            Instant.parse("2026-08-01T01:02:00Z")
        ));
        AuditEvent qrSuccessAuditEvent = auditEventRepository.saveAndFlush(createAuditEvent(
            "00000000-0000-0000-0000-000000000402",
            region,
            AuditEventTargetType.VISIT,
            201L,
            AuditEventResult.SUCCESS,
            "QR_CHECK_IN_SUCCESS",
            Instant.parse("2026-08-01T01:03:00Z")
        ));

        assertThat(auditEventRepository.findQrExceptionAuditProjectionById(contentAuditEvent.getAuditEventId()))
            .isEmpty();
        assertThat(auditEventRepository.findQrExceptionAuditProjectionById(qrSuccessAuditEvent.getAuditEventId()))
            .isEmpty();
    }

    private AuditEvent createAuditEvent(
        Region region,
        AuditEventResult result
    ) {
        return createAuditEvent(
            "00000000-0000-0000-0000-000000000001",
            region,
            AuditEventTargetType.CONTENT,
            101L,
            result,
            null,
            Instant.parse("2026-07-29T00:00:00Z")
        );
    }

    private AuditEvent createAuditEvent(
        String requestId,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        AuditEventResult result,
        String reasonCode,
        Instant occurredAt
    ) {
        return new AuditEvent(
            requestId,
            region,
            targetType,
            targetId,
            "PENDING",
            "PUBLISHED",
            result,
            reasonCode,
            "SYSTEM",
            null,
            occurredAt
        );
    }

    private AuditEvent createMissionAuditEvent(
        Region region,
        Long missionId,
        String previousState,
        String nextState,
        AuditEventResult result,
        String reasonCode,
        String actorKind,
        Instant occurredAt
    ) {
        return new AuditEvent(
            "00000000-0000-0000-0000-000000000638",
            region,
            AuditEventTargetType.MISSION,
            missionId,
            previousState,
            nextState,
            result,
            reasonCode,
            actorKind,
            null,
            occurredAt
        );
    }
}
