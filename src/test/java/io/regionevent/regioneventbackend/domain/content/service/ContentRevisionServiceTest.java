package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@DataJpaTest
@Import(ContentRevisionService.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentRevisionServiceTest {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-06T00:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-02T01:00:00Z");

    private final ContentRevisionService contentRevisionService;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;

    @Autowired
    ContentRevisionServiceTest(
        ContentRevisionService contentRevisionService,
        ContentRevisionRepository contentRevisionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository
    ) {
        this.contentRevisionService = contentRevisionService;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
    }

    @Test
    void reject_whenPublishedRevisionIsValid_rejectsRevisionWithoutChangingOriginal() {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        int originalVersion = fixture.content().getVersionNo();

        ContentRevision rejectedRevision = contentRevisionService.reject(
            contentRevisionService.findReviewTargetForUpdate(fixture.revision().getContentRevisionId()),
            fixture.reviewer(),
            REVIEWED_AT,
            "  표시 정보를 보완해 주세요.  "
        );

        assertThat(rejectedRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(rejectedRevision.getReviewReason()).isEqualTo("표시 정보를 보완해 주세요.");
        assertThat(fixture.content().getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(fixture.content().getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(fixture.content().getVersionNo()).isEqualTo(originalVersion);
        assertThat(fixture.content().getTitle()).isEqualTo("원본 제목");
    }

    @Test
    void reject_whenPrePublicationRevisionIsValid_keepsOriginalPendingAndRejectsRevision() {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT);

        ContentRevision rejectedRevision = contentRevisionService.reject(
            contentRevisionService.findReviewTargetForUpdate(fixture.revision().getContentRevisionId()),
            fixture.reviewer(),
            REVIEWED_AT,
            "공개 예정 시각을 보완해 주세요."
        );

        assertThat(rejectedRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(fixture.content().getStatus()).isEqualTo(ContentStatus.PENDING);
        assertThat(fixture.content().getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(fixture.content().getTitle()).isEqualTo("원본 제목");
    }

    @Test
    void reject_whenOriginalStateAndCandidatePublishAtDoNotMatch_throwsContentStateConflict() {
        Fixture publishedWithCandidate = createFixture(ContentStatus.PUBLISHED, CANDIDATE_PUBLISH_AT);

        assertContentStateConflict(() -> contentRevisionService.reject(
            contentRevisionService.findReviewTargetForUpdate(
                publishedWithCandidate.revision().getContentRevisionId()
            ),
            publishedWithCandidate.reviewer(),
            REVIEWED_AT,
            "반려 사유"
        ));
        assertThat(publishedWithCandidate.revision().getStatus())
            .isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
    }

    @Test
    void reject_whenRevisionIsAlreadyTerminal_throwsContentStateConflict() {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        fixture.revision().reject(fixture.reviewer(), REVIEWED_AT, "이미 처리한 사유");
        contentRevisionRepository.flush();

        assertContentStateConflict(() -> contentRevisionService.reject(
            contentRevisionService.findReviewTargetForUpdate(fixture.revision().getContentRevisionId()),
            fixture.reviewer(),
            REVIEWED_AT.plusSeconds(60),
            "다시 처리하는 사유"
        ));
    }

    @Test
    void findReviewTargetForUpdate_whenOriginalIsSoftDeleted_throwsNotFound() {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT);
        fixture.content().softDelete();
        contentRepository.flush();

        assertThatThrownBy(() -> contentRevisionService.findReviewTargetForUpdate(
            fixture.revision().getContentRevisionId()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
        );
    }

    private Fixture createFixture(ContentStatus contentStatus, Instant candidatePublishAt) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        AppUser reviewer = saveUser("reviewer-" + suffix + "@example.com");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "원본 제목",
            "원본 설명",
            "원본 장소",
            "원본 운영 시간",
            "055-1234-5678",
            "원본 주의사항",
            "만 7세 이상",
            "편한 복장",
            "원본 취소 정책",
            ORIGINAL_PUBLISH_AT
        ));
        ContentRevision revision = contentRevisionRepository.saveAndFlush(new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "후보 제목",
            "후보 설명",
            "후보 장소",
            "후보 운영 시간",
            "055-9876-5432",
            "후보 주의사항",
            "만 8세 이상",
            "운동화",
            "후보 취소 정책",
            candidatePublishAt,
            Instant.parse("2026-08-01T00:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        return new Fixture(content, revision, reviewer);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void assertContentStateConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    private record Fixture(
        Content content,
        ContentRevision revision,
        AppUser reviewer
    ) {
    }
}
