package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class MyContentRepositoryTest {

    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    MyContentRepositoryTest(
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void findMyContents_소유자와_지역과_비삭제_조건을_적용하고_생성시각과_식별자로_정렬한다() {
        Region assignedRegion = saveRegion("MY-CONTENTS");
        Region otherRegion = saveRegion("OTHER-CONTENTS");
        AppUser owner = saveUser("owner@example.com");
        AppUser otherOperator = saveUser("other-operator@example.com");

        Content older = saveContent(assignedRegion, owner, ContentStatus.PENDING, "이전 콘텐츠");
        Content firstTie = saveContent(assignedRegion, owner, ContentStatus.APPROVED, "동률 첫 번째 콘텐츠");
        Content secondTie = saveContent(assignedRegion, owner, ContentStatus.PUBLISHED, "동률 두 번째 콘텐츠");
        Content later = saveContent(assignedRegion, owner, ContentStatus.REJECTED, "최근 콘텐츠");
        Content otherOwner = saveContent(assignedRegion, otherOperator, ContentStatus.PENDING, "다른 운영자 콘텐츠");
        Content otherRegionContent = saveContent(otherRegion, owner, ContentStatus.PENDING, "다른 지역 콘텐츠");
        Content deleted = saveContent(assignedRegion, owner, ContentStatus.PENDING, "삭제 콘텐츠");
        deleted.softDelete(Instant.parse("2026-08-05T00:00:00Z"));
        contentRepository.saveAndFlush(deleted);

        setCreatedAt(older, Instant.parse("2026-08-01T00:00:00Z"));
        setCreatedAt(firstTie, Instant.parse("2026-08-03T00:00:00Z"));
        setCreatedAt(secondTie, Instant.parse("2026-08-03T00:00:00Z"));
        setCreatedAt(later, Instant.parse("2026-08-04T00:00:00Z"));
        setCreatedAt(otherOwner, Instant.parse("2026-08-06T00:00:00Z"));
        setCreatedAt(otherRegionContent, Instant.parse("2026-08-07T00:00:00Z"));
        setCreatedAt(deleted, Instant.parse("2026-08-08T00:00:00Z"));
        entityManager.clear();

        List<MyContentProjection> results = contentRepository.findMyContents(
            owner.getUserId(),
            assignedRegion.getRegionId()
        );

        assertThat(results)
            .extracting(MyContentProjection::contentId)
            .containsExactly(
                later.getContentId(),
                secondTie.getContentId(),
                firstTie.getContentId(),
                older.getContentId()
            );
        assertThat(results)
            .extracting(MyContentProjection::title)
            .containsExactly("최근 콘텐츠", "동률 두 번째 콘텐츠", "동률 첫 번째 콘텐츠", "이전 콘텐츠");
        assertThat(results)
            .extracting(MyContentProjection::status)
            .containsExactly(
                ContentStatus.REJECTED,
                ContentStatus.PUBLISHED,
                ContentStatus.APPROVED,
                ContentStatus.PENDING
            );
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode, true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "운영자",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        ContentStatus status,
        String title
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            title,
            "설명입니다.",
            "장소",
            "운영 시간",
            "055-000-0000",
            "유의사항",
            "연령 조건",
            "준비물",
            "취소 규정",
            Instant.parse("2026-08-15T00:00:00Z")
        ));
    }

    private void setCreatedAt(Content content, Instant createdAt) {
        jdbcTemplate.update(
            "UPDATE content SET created_at = ? WHERE content_id = ?",
            Timestamp.from(createdAt),
            content.getContentId()
        );
    }
}
