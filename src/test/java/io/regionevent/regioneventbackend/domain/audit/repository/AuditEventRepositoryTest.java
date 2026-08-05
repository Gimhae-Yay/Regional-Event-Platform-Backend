package io.regionevent.regioneventbackend.domain.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
    void 지역이_없어도_감사_이벤트를_저장한다() {
        AuditEvent savedAuditEvent = auditEventRepository.saveAndFlush(
            createAuditEvent(null, AuditEventResult.SUCCESS)
        );

        assertThat(savedAuditEvent.getAuditEventId()).isNotNull();
        assertThat(savedAuditEvent.getRegion()).isNull();
    }

    @Test
    void 지역_연관관계는_지연_로딩한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
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
    void 성공과_실패_결과를_저장한다() {
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
    void 사용자_actor는_감사_이벤트와_분리된_연결_테이블에_저장한다() {
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "actor@example.com",
            "password-hash",
            "감사 처리자",
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
    void findQrExceptionReadProjections_excludes_qr_check_in_success_reason() {
        Region region = regionRepository.saveAndFlush(new Region("QR-EXCEPTION-1", "김해시", true));
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
    void existsQrExceptionCursorBoundary_excludes_qr_check_in_success_reason() {
        Region region = regionRepository.saveAndFlush(new Region("QR-EXCEPTION-2", "김해시", true));
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
}
