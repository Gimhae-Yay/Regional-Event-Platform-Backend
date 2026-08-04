package io.regionevent.regioneventbackend.domain.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
    void 감사_결과는_성공과_실패를_표현한다() {
        assertThat(AuditEventResult.values())
            .containsExactly(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
    }

    @Test
    void 감사_대상_유형은_비개인_도메인으로만_제한한다() {
        assertThat(AuditEventTargetType.values()).containsExactly(
            AuditEventTargetType.REGION,
            AuditEventTargetType.OPERATOR_APPLICATION,
            AuditEventTargetType.CONTENT,
            AuditEventTargetType.CONTENT_SESSION,
            AuditEventTargetType.CAPACITY_HOLD,
            AuditEventTargetType.RESERVATION,
            AuditEventTargetType.VISIT,
            AuditEventTargetType.REVIEW
        );

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
    void QR_예외_감사_이벤트를_단건_projection으로_조회한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AuditEvent auditEvent = auditEventRepository.saveAndFlush(new AuditEvent(
            "00000000-0000-0000-0000-000000000002",
            region,
            AuditEventTargetType.RESERVATION,
            123L,
            null,
            null,
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SIGNATURE_INVALID",
            "USER",
            "OPERATOR",
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
    void QR_예외_범위가_아닌_감사_이벤트는_projection으로_조회하지_않는다() {
        Region region = regionRepository.saveAndFlush(new Region("BUSAN", "부산시", true));
        AuditEvent contentAuditEvent = auditEventRepository.saveAndFlush(new AuditEvent(
            "00000000-0000-0000-0000-000000000003",
            region,
            AuditEventTargetType.CONTENT,
            101L,
            "PENDING",
            "PUBLISHED",
            AuditEventResult.SUCCESS,
            null,
            "SYSTEM",
            null,
            Instant.parse("2026-08-01T01:02:00Z")
        ));
        AuditEvent qrSuccessAuditEvent = auditEventRepository.saveAndFlush(new AuditEvent(
            "00000000-0000-0000-0000-000000000004",
            region,
            AuditEventTargetType.VISIT,
            201L,
            "CONFIRMED",
            "CHECKED_IN",
            AuditEventResult.SUCCESS,
            "QR_CHECK_IN_SUCCESS",
            "USER",
            "OPERATOR",
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
        return new AuditEvent(
            "00000000-0000-0000-0000-000000000001",
            region,
            AuditEventTargetType.CONTENT,
            101L,
            "PENDING",
            "PUBLISHED",
            result,
            null,
            "SYSTEM",
            null,
            Instant.parse("2026-07-29T00:00:00Z")
        );
    }
}
