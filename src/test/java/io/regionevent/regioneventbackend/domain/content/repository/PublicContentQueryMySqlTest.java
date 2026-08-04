package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.support.mysql.MySqlTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PublicContentQueryMySqlTest extends MySqlTestSupport {

    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicContentQueryMySqlTest(
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeTransaction
    void removeContentSoftDeleteStatusConstraint() {
        jdbcTemplate.execute("ALTER TABLE content DROP CHECK ck_content_soft_delete_status");
    }

    @AfterTransaction
    void restoreContentSoftDeleteStatusConstraint() {
        jdbcTemplate.execute("""
            ALTER TABLE content
            ADD CONSTRAINT ck_content_soft_delete_status
            CHECK (
                CASE
                    WHEN deleted_at IS NULL THEN TRUE
                    WHEN status = 'PENDING' THEN TRUE
                    WHEN status = 'APPROVED' THEN TRUE
                    ELSE FALSE
                END = TRUE
            )
            """);
    }

    @Test
    void 소프트_삭제된_PUBLISHED_콘텐츠는_공개_콘텐츠로_판정하지_않는다() {
        Content content = savePublishedContent();
        jdbcTemplate.update(
            "UPDATE content SET deleted_at = CURRENT_TIMESTAMP WHERE content_id = ?",
            content.getContentId()
        );

        boolean exists = contentRepository.existsByContentIdAndStatusAndDeletedAtIsNull(
            content.getContentId(),
            ContentStatus.PUBLISHED
        );

        assertThat(exists).isFalse();
    }

    @Test
    void 예약_가능_여부는_MySQL_현재시각과_잔여_정원으로_계산한다() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        Content reservable = savePublishedContent(region, operator, "예약 가능 콘텐츠");
        Content unavailable = savePublishedContent(region, operator, "예약 불가 콘텐츠");
        saveScheduledSession(reservable, region, operator, Instant.now().plusSeconds(3_600), 1);
        ContentSession unavailableSession = saveScheduledSession(
            unavailable,
            region,
            operator,
            Instant.now().plusSeconds(3_600),
            1
        );
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = 0 WHERE session_id = ?",
            unavailableSession.getSessionId()
        );

        List<PublicContentProjection> results = contentRepository.findPublicContents(
            region.getRegionId(),
            ContentType.EVENT_EXPERIENCE,
            null,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );

        assertThat(results)
            .extracting(PublicContentProjection::contentId)
            .containsExactly(unavailable.getContentId(), reservable.getContentId());
        assertThat(results)
            .extracting(PublicContentProjection::reservationAvailable)
            .containsExactly(false, true);
    }

    private Content savePublishedContent() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        return savePublishedContent(region, operator, "김해 가야 문화 체험");
    }

    private Content savePublishedContent(Region region, AppUser operator, String title) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            title,
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
    }

    private ContentSession saveScheduledSession(
        Content content,
        Region region,
        AppUser reviewer,
        Instant startsAt,
        int capacity
    ) {
        ContentSession contentSession = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(3_600),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(1_800),
            capacity
        );
        contentSession.approve(reviewer, Instant.now());
        return contentSessionRepository.saveAndFlush(contentSession);
    }
}
