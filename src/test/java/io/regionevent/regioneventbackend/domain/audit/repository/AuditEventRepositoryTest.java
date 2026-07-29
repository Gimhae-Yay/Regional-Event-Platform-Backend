package io.regionevent.regioneventbackend.domain.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuditEventRepositoryTest(
        AuditEventRepository auditEventRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.auditEventRepository = auditEventRepository;
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
    void 감사_결과는_성공과_실패로만_제한한다() {
        assertThat(AuditEventResult.values())
            .containsExactly(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO audit_event (
                    request_id,
                    target_type,
                    result,
                    actor_kind,
                    occurred_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
            "00000000-0000-0000-0000-000000000003",
            "CONTENT",
            "PENDING",
            "SYSTEM",
            Timestamp.from(Instant.parse("2026-07-29T00:00:00Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 감사_대상_유형은_비개인_도메인으로만_제한한다() {
        assertThat(AuditEventTargetType.values()).containsExactly(
            AuditEventTargetType.REGION,
            AuditEventTargetType.OPERATOR_APPLICATION,
            AuditEventTargetType.CONTENT,
            AuditEventTargetType.CONTENT_SESSION,
            AuditEventTargetType.RESERVATION,
            AuditEventTargetType.VISIT,
            AuditEventTargetType.REVIEW
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO audit_event (
                    request_id,
                    target_type,
                    target_id,
                    result,
                    actor_kind,
                    occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            "00000000-0000-0000-0000-000000000004",
            "APP_USER",
            1L,
            "SUCCESS",
            "SYSTEM",
            Timestamp.from(Instant.parse("2026-07-29T00:00:00Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);
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

        entityManager.persist(new AuditEventActorLink(auditEvent, actor));
        entityManager.flush();
        entityManager.clear();

        AuditEventActorLink actorLink = entityManager.find(
            AuditEventActorLink.class,
            auditEvent.getAuditEventId()
        );
        List<String> auditEventColumns = jdbcTemplate.query(
            """
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'AUDIT_EVENT'
                """,
            (resultSet, rowNumber) -> resultSet.getString("COLUMN_NAME")
        );

        assertThat(actorLink.getActor().getUserId()).isEqualTo(actor.getUserId());
        assertThat(auditEventColumns).doesNotContain("USER_ID");
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
