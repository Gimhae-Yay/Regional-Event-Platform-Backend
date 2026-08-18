package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentLogRepositoryTest {

    private final ContentLogRepository contentLogRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    ContentLogRepositoryTest(
        ContentLogRepository contentLogRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.contentLogRepository = contentLogRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 콘텐츠_로그의_추가_전용_핵심_값을_저장한다() {
        Content content = saveContent();
        Instant recordedAt = Instant.parse("2026-08-01T00:00:00Z");

        ContentLog contentLog = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.PENDING, null, recordedAt)
        );

        ContentLog foundContentLog = contentLogRepository.findById(contentLog.getId()).orElseThrow();

        assertThat(foundContentLog.getContent().getContentId()).isEqualTo(content.getContentId());
        assertThat(foundContentLog.getActor()).isNull();
        assertThat(foundContentLog.getStatus()).isEqualTo(ContentLogStatus.PENDING);
        assertThat(foundContentLog.getReason()).isNull();
        assertThat(foundContentLog.getDate()).isEqualTo(recordedAt);
    }

    @ParameterizedTest
    @EnumSource(ContentLogStatus.class)
    void 콘텐츠_로그_상태_카탈로그를_문자열로_매핑한다(ContentLogStatus status) {
        Content content = saveContent();
        String reason = switch (status) {
            case REJECTED, SUSPENDED, WITHDRAWN, DELETED -> "상태 변경 사유";
            default -> null;
        };

        ContentLog contentLog = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, status, reason, Instant.parse("2026-08-01T00:00:00Z"))
        );

        assertThat(contentLogRepository.findById(contentLog.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(
        value = ContentLogStatus.class,
        names = {"REJECTED", "SUSPENDED", "WITHDRAWN", "DELETED"}
    )
    void 사유가_필수인_콘텐츠_로그_상태는_빈_사유를_거부한다(ContentLogStatus status) {
        Content content = saveContent();

        assertThatThrownBy(() -> new ContentLog(
            content,
            null,
            status,
            " ",
            Instant.parse("2026-08-01T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 콘텐츠와_처리자를_지연_로딩으로_매핑하고_탈퇴_후_연결을_제거한다() {
        Content content = saveContent();
        AppUser actor = saveUser("actor@example.com");
        ContentLog contentLog = contentLogRepository.saveAndFlush(
            new ContentLog(
                content,
                actor,
                ContentLogStatus.REJECTED,
                "필수 정보가 누락되었습니다.",
                Instant.parse("2026-08-01T00:00:00Z")
            )
        );
        entityManager.clear();

        ContentLog foundContentLog = contentLogRepository.findById(contentLog.getId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundContentLog, "content")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundContentLog, "actor")).isFalse();
        assertThat(foundContentLog.getContent().getContentId()).isEqualTo(content.getContentId());
        assertThat(foundContentLog.getActor().getUserId()).isEqualTo(actor.getUserId());

        foundContentLog.unlinkActor();
        contentLogRepository.flush();
        entityManager.clear();

        ContentLog unlinkedContentLog = contentLogRepository.findById(contentLog.getId()).orElseThrow();

        assertThat(unlinkedContentLog.getActor()).isNull();
        assertThat(unlinkedContentLog.getStatus()).isEqualTo(ContentLogStatus.REJECTED);
        assertThat(unlinkedContentLog.getReason()).isEqualTo("필수 정보가 누락되었습니다.");
    }

    @Test
    void 최신_REJECTED_로그는_처리시각과_식별자_내림차순으로_조회한다() {
        Content content = saveContent();
        AppUser actor = saveUser("latest-rejected-actor@example.com");
        Instant earlier = Instant.parse("2026-08-01T00:00:00Z");
        Instant later = Instant.parse("2026-08-01T01:00:00Z");
        contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.REJECTED, "이전 반려 사유", earlier)
        );
        ContentLog firstAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.REJECTED, "동일 시각 첫 반려 사유", later)
        );
        ContentLog latestAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.REJECTED, "최신 반려 사유", later)
        );
        contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.APPROVED, null, later.plusSeconds(60))
        );
        entityManager.clear();

        ContentLog latestRejectedLog = contentLogRepository
            .findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
                content.getContentId(),
                ContentLogStatus.REJECTED
            )
            .orElseThrow();

        assertThat(latestRejectedLog.getId()).isGreaterThan(firstAtSameTime.getId());
        assertThat(latestRejectedLog.getId()).isEqualTo(latestAtSameTime.getId());
        assertThat(latestRejectedLog.getReason()).isEqualTo("최신 반려 사유");
    }

    @Test
    void 상태별_목록용_최신_APPROVED_로그는_같은_시각이면_식별자가_큰_한건을_조회한다() {
        Content content = saveContent();
        Instant approvedAt = Instant.parse("2026-08-01T01:00:00Z");
        contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.APPROVED, null, approvedAt.minusSeconds(1))
        );
        ContentLog firstAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.APPROVED, null, approvedAt)
        );
        ContentLog latestAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.APPROVED, null, approvedAt)
        );
        contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.PENDING, null, approvedAt.plusSeconds(1))
        );
        entityManager.clear();

        List<ContentLog> approvedLogs = contentLogRepository.findLatestByContentIdsAndStatus(
            List.of(content.getContentId()),
            ContentLogStatus.APPROVED
        );

        assertThat(approvedLogs).extracting(ContentLog::getId).containsExactly(latestAtSameTime.getId());
        assertThat(approvedLogs.getFirst().getId()).isGreaterThan(firstAtSameTime.getId());
    }

    @Test
    void 최신_ENDED_로그는_처리시각과_식별자_내림차순으로_한건_조회한다() {
        Content content = saveContent();
        AppUser actor = saveUser("latest-ended-actor@example.com");
        Instant earlier = Instant.parse("2026-08-01T00:00:00Z");
        Instant later = Instant.parse("2026-08-01T01:00:00Z");
        contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.ENDED, null, earlier)
        );
        ContentLog firstAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.ENDED, null, later)
        );
        ContentLog latestAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.ENDED, null, later)
        );
        entityManager.clear();

        ContentLog latestEndedLog = contentLogRepository.findLatestEndedForUpdate(
                content.getContentId(),
                ContentLogStatus.ENDED,
                Pageable.ofSize(1)
            )
            .getFirst();

        assertThat(latestEndedLog.getId()).isGreaterThan(firstAtSameTime.getId());
        assertThat(latestEndedLog.getId()).isEqualTo(latestAtSameTime.getId());
        assertThat(latestEndedLog.getDate()).isEqualTo(later);
    }

    @Test
    void 소프트_삭제_콘텐츠의_로그와_처리자를_처리시각과_식별자_오름차순으로_조회한다() {
        Content content = saveContent();
        AppUser actor = saveUser("history-actor@example.com");
        Instant earlier = Instant.parse("2026-08-01T00:00:00Z");
        Instant later = Instant.parse("2026-08-01T01:00:00Z");
        ContentLog firstAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.APPROVED, null, later)
        );
        ContentLog secondAtSameTime = contentLogRepository.saveAndFlush(
            new ContentLog(content, null, ContentLogStatus.DELETED, "등록 요청 철회", later)
        );
        ContentLog earliest = contentLogRepository.saveAndFlush(
            new ContentLog(content, actor, ContentLogStatus.PENDING, null, earlier)
        );
        content.softDelete();
        contentRepository.flush();
        entityManager.clear();

        List<ContentLog> histories = contentLogRepository
            .findByContentContentIdOrderByDateAscIdAsc(content.getContentId());

        assertThat(histories).extracting(ContentLog::getId)
            .containsExactly(earliest.getId(), firstAtSameTime.getId(), secondAtSameTime.getId());
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(histories.get(0), "actor")).isTrue();
        assertThat(histories.get(0).getActor().getName()).isEqualTo("홍길동");
    }

    private Content saveContent() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = saveUser("operator@example.com");

        return contentRepository.saveAndFlush(
            new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PENDING,
                "김해 가야 문화 체험",
                "김해 가야 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                Instant.parse("2026-08-01T00:00:00Z")
            )
        );
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(
            new AppUser(
                loginIdentifier,
                "hashed-password",
                "홍길동",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            )
        );
    }
}
