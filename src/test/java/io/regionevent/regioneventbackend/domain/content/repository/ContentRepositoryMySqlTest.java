package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentRepositoryMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ContentRepository contentRepository;
    private final ContentService contentService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ContentRepositoryMySqlTest(
        ContentRepository contentRepository,
        ContentService contentService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.contentRepository = contentRepository;
        this.contentService = contentService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 자동_공개_후보는_MySQL_현재_시각을_기준으로_승인된_콘텐츠만_조회한다() {
        Fixture fixture = createFixture(contentService.findCurrentDatabaseTime());

        assertThat(contentRepository.findApprovedPublicationCandidateIds())
            .containsExactly(fixture.pastApprovedContentId());
    }

    @Test
    void 자동_공개_잠금_대상은_MySQL_현재_시각과_상태를_다시_확인한다() {
        Fixture fixture = createFixture(contentService.findCurrentDatabaseTime());

        assertThat(hasApprovedPublicationTarget(fixture.pastApprovedContentId())).isTrue();
        assertThat(hasApprovedPublicationTarget(fixture.futureApprovedContentId())).isFalse();
        assertThat(hasApprovedPublicationTarget(fixture.pendingContentId())).isFalse();
        assertThat(hasApprovedPublicationTarget(fixture.deletedApprovedContentId())).isFalse();
    }

    private Fixture createFixture(Instant databaseNow) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("R" + suffix, "테스트 지역", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            Content pastApprovedContent = contentRepository.save(newContent(
                region,
                operator,
                ContentStatus.APPROVED,
                databaseNow.minusSeconds(60)
            ));
            Content futureApprovedContent = contentRepository.save(newContent(
                region,
                operator,
                ContentStatus.APPROVED,
                databaseNow.plusSeconds(60)
            ));
            Content pendingContent = contentRepository.save(newContent(
                region,
                operator,
                ContentStatus.PENDING,
                databaseNow.minusSeconds(60)
            ));
            Content deletedApprovedContent = contentRepository.save(newContent(
                region,
                operator,
                ContentStatus.APPROVED,
                databaseNow.minusSeconds(60)
            ));
            deletedApprovedContent.softDelete();
            contentRepository.flush();
            return new Fixture(
                pastApprovedContent.getContentId(),
                futureApprovedContent.getContentId(),
                pendingContent.getContentId(),
                deletedApprovedContent.getContentId()
            );
        });
    }

    private boolean hasApprovedPublicationTarget(Long contentId) {
        return transactionTemplate.execute(status ->
            contentRepository.findApprovedPublicationTargetForUpdate(contentId).isPresent()
        );
    }

    private Content newContent(
        Region region,
        AppUser operator,
        ContentStatus status,
        Instant publishAt
    ) {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            publishAt
        );
    }

    private record Fixture(
        Long pastApprovedContentId,
        Long futureApprovedContentId,
        Long pendingContentId,
        Long deletedApprovedContentId
    ) {
    }
}
