package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentRevisionRepositoryTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-01T02:00:00Z");

    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final EntityManager entityManager;

    @Autowired
    ContentRevisionRepositoryTest(
        ContentRevisionRepository contentRevisionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ImageObjectRepository imageObjectRepository,
        EntityManager entityManager
    ) {
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 콘텐츠_수정_후보_필드와_수정_요청_상태를_저장한다() {
        Content content = saveContent();
        AppUser editor = saveUser("editor@example.com");

        ContentRevision contentRevision = contentRevisionRepository.saveAndFlush(
            newRevision(content, 1, editor, ContentRevisionStatus.EDIT_REQUESTED, null, null, null, null, null, null)
        );

        ContentRevision foundContentRevision = contentRevisionRepository.findById(
            contentRevision.getContentRevisionId()
        ).orElseThrow();

        assertThat(foundContentRevision.getRevisionNo()).isEqualTo(1);
        assertThat(foundContentRevision.getBaseContentVersion()).isZero();
        assertThat(foundContentRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(foundContentRevision.getTitle()).isEqualTo("김해 가야 문화 체험 수정본");
        assertThat(foundContentRevision.getDescription()).isEqualTo("김해 가야 문화를 체험하는 행사 수정 설명입니다.");
        assertThat(foundContentRevision.getLocationText()).isEqualTo("김해문화의전당 대공연장");
        assertThat(foundContentRevision.getOperatingHoursText()).isEqualTo("매일 11:00~19:00");
        assertThat(foundContentRevision.getContactText()).isEqualTo("055-987-6543");
        assertThat(foundContentRevision.getPrecautions()).isEqualTo("현장 안내를 따라주세요.");
        assertThat(foundContentRevision.getAgeRequirement()).isEqualTo("만 8세 이상");
        assertThat(foundContentRevision.getMaterials()).isEqualTo("운동화");
        assertThat(foundContentRevision.getCancellationPolicyText()).isEqualTo("시작 이틀 전까지 취소할 수 있습니다.");
        assertThat(foundContentRevision.getCandidateImageObject()).isNull();
        assertThat(foundContentRevision.getCandidateImageAssignedAt()).isNull();
        assertThat(foundContentRevision.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(foundContentRevision.getReviewedAt()).isNull();
        assertThat(foundContentRevision.getReviewedBy()).isNull();
        assertThat(foundContentRevision.getReviewReason()).isNull();
        assertThat(foundContentRevision.getWithdrawnAt()).isNull();
        assertThat(foundContentRevision.getWithdrawnBy()).isNull();
        assertThat(foundContentRevision.getWithdrawalReason()).isNull();
        assertThat(foundContentRevision.getCreatedAt()).isNotNull();
    }

    @Test
    void 콘텐츠별_수정_번호는_유일하다() {
        Content content = saveContent();
        AppUser editor = saveUser("editor@example.com");
        AppUser reviewer = saveUser("reviewer@example.com");

        contentRevisionRepository.saveAndFlush(
            newRevision(
                content,
                1,
                editor,
                ContentRevisionStatus.EDIT_APPROVED,
                REVIEWED_AT,
                reviewer,
                "승인합니다.",
                null,
                null,
                null
            )
        );

        assertThatThrownBy(() -> contentRevisionRepository.saveAndFlush(
            newRevision(
                content,
                1,
                editor,
                ContentRevisionStatus.EDIT_REJECTED,
                REVIEWED_AT,
                reviewer,
                "중복된 수정본입니다.",
                null,
                null,
                null
            )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 콘텐츠별_수정_요청은_한_건만_저장한다() {
        Content content = saveContent();
        AppUser editor = saveUser("editor@example.com");

        contentRevisionRepository.saveAndFlush(
            newRevision(content, 1, editor, ContentRevisionStatus.EDIT_REQUESTED, null, null, null, null, null, null)
        );

        assertThatThrownBy(() -> contentRevisionRepository.saveAndFlush(
            newRevision(content, 2, editor, ContentRevisionStatus.EDIT_REQUESTED, null, null, null, null, null, null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 심사와_철회_상태는_처리_정보가_필수다() {
        Content content = saveContent();
        AppUser editor = saveUser("editor@example.com");

        assertThatThrownBy(() -> newRevision(
            content,
            1,
            editor,
            ContentRevisionStatus.EDIT_APPROVED,
            null,
            null,
            null,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> newRevision(
            content,
            2,
            editor,
            ContentRevisionStatus.EDIT_WITHDRAWN,
            null,
            null,
            null,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 콘텐츠와_편집자_심사자_철회_처리자를_지연_로딩으로_매핑한다() {
        Content content = saveContent();
        AppUser editor = saveUser("editor@example.com");
        AppUser reviewer = saveUser("reviewer@example.com");
        AppUser withdrawer = saveUser("withdrawer@example.com");
        ContentRevision contentRevision = contentRevisionRepository.saveAndFlush(
            newRevision(
                content,
                1,
                editor,
                ContentRevisionStatus.EDIT_WITHDRAWN,
                null,
                null,
                null,
                WITHDRAWN_AT,
                withdrawer,
                "내용을 다시 보완하겠습니다."
            )
        );
        ContentRevision reviewedContentRevision = contentRevisionRepository.saveAndFlush(
            newRevision(
                content,
                2,
                editor,
                ContentRevisionStatus.EDIT_APPROVED,
                REVIEWED_AT,
                reviewer,
                "승인합니다.",
                null,
                null,
                null
            )
        );
        entityManager.clear();

        ContentRevision foundContentRevision = contentRevisionRepository.findById(
            contentRevision.getContentRevisionId()
        ).orElseThrow();
        ContentRevision foundReviewedContentRevision = contentRevisionRepository.findById(
            reviewedContentRevision.getContentRevisionId()
        ).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundContentRevision, "content")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundContentRevision, "editor")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundContentRevision, "withdrawnBy")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundReviewedContentRevision, "reviewedBy")).isFalse();
        assertThat(foundContentRevision.getContent().getContentId()).isEqualTo(content.getContentId());
        assertThat(foundContentRevision.getEditor().getUserId()).isEqualTo(editor.getUserId());
        assertThat(foundContentRevision.getWithdrawnBy().getUserId()).isEqualTo(withdrawer.getUserId());
        assertThat(foundReviewedContentRevision.getReviewedBy().getUserId()).isEqualTo(reviewer.getUserId());
    }

    @Test
    void 후보_대표_이미지는_콘텐츠와_여러_수정본에서_공유_참조할_수_있다() {
        Content content = saveContent();
        AppUser editor = saveUser("editor@example.com");
        AppUser reviewer = saveUser("reviewer@example.com");
        ImageObject imageObject = saveImageObject("content/revision-candidate.webp");
        Instant contentAssignedAt = Instant.parse("2026-08-01T03:00:00Z");
        Instant firstRevisionAssignedAt = Instant.parse("2026-08-01T04:00:00Z");
        Instant secondRevisionAssignedAt = Instant.parse("2026-08-01T05:00:00Z");

        content.assignRepresentativeImage(imageObject, contentAssignedAt);
        ContentRevision firstRevision = contentRevisionRepository.saveAndFlush(
            newRevision(
                content,
                1,
                editor,
                ContentRevisionStatus.EDIT_APPROVED,
                REVIEWED_AT,
                reviewer,
                "승인합니다.",
                null,
                null,
                null
            )
        );
        ContentRevision secondRevision = contentRevisionRepository.saveAndFlush(
            newRevision(
                content,
                2,
                editor,
                ContentRevisionStatus.EDIT_REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );
        firstRevision.assignCandidateImage(imageObject, firstRevisionAssignedAt);
        secondRevision.assignCandidateImage(imageObject, secondRevisionAssignedAt);
        contentRevisionRepository.flush();
        entityManager.clear();

        Content foundContent = contentRepository.findById(content.getContentId()).orElseThrow();
        ContentRevision foundFirstRevision = contentRevisionRepository.findById(
            firstRevision.getContentRevisionId()
        ).orElseThrow();
        ContentRevision foundSecondRevision = contentRevisionRepository.findById(
            secondRevision.getContentRevisionId()
        ).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundFirstRevision, "candidateImageObject")).isFalse();
        assertThat(foundContent.getRepresentativeImageObject().getImageObjectId()).isEqualTo(imageObject.getImageObjectId());
        assertThat(foundFirstRevision.getCandidateImageObject().getImageObjectId()).isEqualTo(imageObject.getImageObjectId());
        assertThat(foundSecondRevision.getCandidateImageObject().getImageObjectId()).isEqualTo(imageObject.getImageObjectId());
        assertThat(foundFirstRevision.getCandidateImageAssignedAt()).isEqualTo(firstRevisionAssignedAt);
        assertThat(foundSecondRevision.getCandidateImageAssignedAt()).isEqualTo(secondRevisionAssignedAt);
    }

    private ContentRevision newRevision(
        Content content,
        int revisionNo,
        AppUser editor,
        ContentRevisionStatus status,
        Instant reviewedAt,
        AppUser reviewedBy,
        String reviewReason,
        Instant withdrawnAt,
        AppUser withdrawnBy,
        String withdrawalReason
    ) {
        return new ContentRevision(
            content,
            revisionNo,
            content.getVersionNo(),
            editor,
            status,
            "김해 가야 문화 체험 수정본",
            "김해 가야 문화를 체험하는 행사 수정 설명입니다.",
            "김해문화의전당 대공연장",
            "매일 11:00~19:00",
            "055-987-6543",
            "현장 안내를 따라주세요.",
            "만 8세 이상",
            "운동화",
            "시작 이틀 전까지 취소할 수 있습니다.",
            SUBMITTED_AT,
            reviewedAt,
            reviewedBy,
            reviewReason,
            withdrawnAt,
            withdrawnBy,
            withdrawalReason
        );
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
                "055-123-4567",
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
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            )
        );
    }

    private ImageObject saveImageObject(String objectKey) {
        return imageObjectRepository.saveAndFlush(new ImageObject(
            objectKey,
            "image/webp",
            1L,
            "sha256:" + objectKey,
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        ));
    }
}
