package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class ContentRevisionApprovalAuditAtomicityTest {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    ContentRevisionApprovalAuditAtomicityTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ImageObjectRepository imageObjectRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void approveContentRevision_whenAuditRecordingFails_rollsBackAllChanges() throws Exception {
        Fixture fixture = createFixture();
        int originalVersion = fixture.content().getVersionNo();
        Long originalImageId = fixture.content().getRepresentativeImageObject().getImageObjectId();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        mockMvc.perform(post(
                "/api/v1/region-admin/content-revisions/{revisionId}/approve",
                fixture.revision().getContentRevisionId()
            ).header(
                "Authorization",
                "Bearer " + jwtAccessTokenService.issue(fixture.admin().getUserId())
            ))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        entityManager.clear();
        Content unchangedContent = contentRepository.findById(
            fixture.content().getContentId()
        ).orElseThrow();
        ContentRevision unchangedRevision = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(unchangedContent.getVersionNo()).isEqualTo(originalVersion);
        assertThat(unchangedContent.getTitle()).isEqualTo("원본 제목");
        assertThat(unchangedContent.getRepresentativeImageObject().getImageObjectId())
            .isEqualTo(originalImageId);
        assertThat(unchangedRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(unchangedRevision.getReviewedAt()).isNull();
        assertThat(unchangedRevision.getReviewedBy()).isNull();
        assertThat(unchangedRevision.getReviewReason()).isNull();
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        AppUser operator = saveUser("operator-" + suffix);
        ImageObject originalImage = saveLinkedImage("original-" + suffix, operator, region);
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
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
        );
        content.assignRepresentativeImage(originalImage, SUBMITTED_AT.minusSeconds(60));
        contentRepository.saveAndFlush(content);
        ImageObject candidateImage = saveLinkedImage("candidate-" + suffix, operator, region);
        ContentRevision revision = new ContentRevision(
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
            null,
            SUBMITTED_AT,
            null,
            null,
            null,
            null,
            null,
            null
        );
        revision.assignCandidateImage(candidateImage, SUBMITTED_AT);
        contentRevisionRepository.saveAndFlush(revision);
        return new Fixture(admin, content, revision);
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private ImageObject saveLinkedImage(String suffix, AppUser operator, Region region) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/approval-rollback-" + suffix + ".webp",
            operator,
            region,
            "image/webp",
            1L,
            "checksum-" + suffix,
            SUBMITTED_AT.plusSeconds(3_600)
        );
        imageObject.markLinked(SUBMITTED_AT.minusSeconds(1));
        return imageObjectRepository.saveAndFlush(imageObject);
    }

    private record Fixture(
        AppUser admin,
        Content content,
        ContentRevision revision
    ) {
    }
}
