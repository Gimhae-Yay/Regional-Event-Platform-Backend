package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
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
@Transactional
class UpdateContentRevisionControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISH_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant CANDIDATE_IMAGE_ASSIGNED_AT = Instant.parse("2026-08-04T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-10T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentRevisionRepository contentRevisionRepository;

    @Autowired
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private FakeImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @Test
    void updateContentRevision_whenRejectedRevisionRequestsValidFields_updatesOnlyRevision() throws Exception {
        Region region = saveRegion("UPDATE-REVISION");
        AppUser operator = saveUser("update-revision-operator@example.com");
        AppUser reviewer = saveUser("update-revision-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
        ContentSession contentSession = saveSession(content, region);
        ImageObject candidateImageObject = saveImageObject("content/revision-existing-image.webp");
        ContentRevision contentRevision = saveRejectedRevision(content, operator, reviewer, null, candidateImageObject);
        int originalContentVersion = content.getVersionNo();
        int originalSessionCapacity = contentSession.getCapacity();

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(validRequestWithoutPublishAt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 편집에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(contentRevision.getContentRevisionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(content.getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

        ContentRevision updatedRevision = contentRevisionRepository.findById(
            contentRevision.getContentRevisionId()
        ).orElseThrow();
        assertThat(updatedRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(updatedRevision.getTitle()).isEqualTo("보완된 김해 가야문화 체험");
        assertThat(updatedRevision.getDescription()).isEqualTo("보완된 행사 소개입니다.");
        assertThat(updatedRevision.getLocationText()).isEqualTo("김해시 가야의길 190");
        assertThat(updatedRevision.getOperatingHoursText()).isEqualTo("매주 토요일 10:00~16:00");
        assertThat(updatedRevision.getContactText()).isEqualTo("055-000-0000");
        assertThat(updatedRevision.getPrecautions()).isEqualTo("편한 복장으로 참여해 주세요.");
        assertThat(updatedRevision.getAgeRequirement()).isEqualTo("초등학생 이상");
        assertThat(updatedRevision.getMaterials()).isEqualTo("필기도구");
        assertThat(updatedRevision.getCancellationPolicyText()).isEqualTo("회차 시작 전까지 예약 취소가 가능합니다.");
        assertThat(updatedRevision.getPublishAt()).isNull();
        assertThat(updatedRevision.getCandidateImageObject().getImageObjectId())
            .isEqualTo(candidateImageObject.getImageObjectId());
        assertThat(updatedRevision.getCandidateImageAssignedAt()).isEqualTo(CANDIDATE_IMAGE_ASSIGNED_AT);

        Content unchangedContent = contentRepository.findById(content.getContentId()).orElseThrow();
        assertThat(unchangedContent.getTitle()).isEqualTo("김해 가야 문화 체험");
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(unchangedContent.getVersionNo()).isEqualTo(originalContentVersion);
        assertThat(contentSessionRepository.findById(contentSession.getSessionId()))
            .get()
            .satisfies(foundSession -> assertThat(foundSession.getCapacity()).isEqualTo(originalSessionCapacity));
    }

    @Test
    void updateContentRevision_whenCandidatePublishAtExists_requiresPublishAtAndUpdatesIt() throws Exception {
        Region region = saveRegion("UPDATE-PUBLISH-AT");
        AppUser operator = saveUser("update-publish-at-operator@example.com");
        AppUser reviewer = saveUser("update-publish-at-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator, ContentStatus.APPROVED);
        ContentRevision contentRevision = saveRejectedRevision(content, operator, reviewer, CANDIDATE_PUBLISH_AT, null);

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithPublishAt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

        assertThat(contentRevisionRepository.findById(contentRevision.getContentRevisionId()))
            .get()
            .extracting(ContentRevision::getPublishAt)
            .isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
    }

    @Test
    void updateContentRevision_whenUserIsNotOwnerOrRegionDiffers_returnsForbidden() throws Exception {
        Region contentRegion = saveRegion("UPDATE-CONTENT-REGION");
        Region otherRegion = saveRegion("UPDATE-OTHER-REGION");
        AppUser owner = saveUser("update-region-owner@example.com");
        AppUser reviewer = saveUser("update-region-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(owner, UserRole.OPERATOR, otherRegion));
        Content content = saveContent(contentRegion, owner, ContentStatus.PUBLISHED);
        ContentRevision contentRevision = saveRejectedRevision(content, owner, reviewer, null, null);

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithoutPublishAt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void updateContentRevision_whenRevisionIsNotRejected_returnsContentStateConflict() throws Exception {
        Region region = saveRegion("UPDATE-STATE-CONFLICT");
        AppUser operator = saveUser("update-state-conflict-operator@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
        ContentRevision contentRevision = saveRevision(
            content,
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            null,
            null,
            null
        );

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithoutPublishAt()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));
    }

    @Test
    void updateContentRevision_whenPublishAtConditionIsInvalid_returnsInvalidInput() throws Exception {
        Region region = saveRegion("UPDATE-PUBLISH-INVALID");
        AppUser operator = saveUser("update-publish-invalid-operator@example.com");
        AppUser reviewer = saveUser("update-publish-invalid-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
        ContentRevision contentRevision = saveRejectedRevision(content, operator, reviewer, null, null);

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithPublishAt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void updateContentRevision_whenRepresentativeImageObjectIdIsValid_replacesCandidateImage() throws Exception {
        Region region = saveRegion("UPDATE-IMAGE-VALID");
        AppUser operator = saveUser("update-image-valid-operator@example.com");
        AppUser reviewer = saveUser("update-image-valid-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
        ImageObject originalCandidateImageObject = saveImageObject("content/revision-original-candidate.webp");
        ImageObject replacementCandidateImageObject = saveUploadCandidateImageObject(
            operator,
            region,
            "content/revision-replacement-candidate.webp"
        );
        imageStorageGateway.addMetadata(
            replacementCandidateImageObject.getObjectKey(),
            replacementCandidateImageObject.getByteSize(),
            replacementCandidateImageObject.getChecksum()
        );
        ContentRevision contentRevision = saveRejectedRevision(
            content,
            operator,
            reviewer,
            null,
            originalCandidateImageObject
        );
        String replacementImageObjectId = replacementCandidateImageObject.getImageObjectId().toString();

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithRepresentativeImage(replacementImageObjectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

        ContentRevision updatedRevision = contentRevisionRepository.findById(
            contentRevision.getContentRevisionId()
        ).orElseThrow();
        assertThat(updatedRevision.getCandidateImageObject().getImageObjectId())
            .isEqualTo(replacementCandidateImageObject.getImageObjectId());
        assertThat(updatedRevision.getCandidateImageAssignedAt()).isNotNull();
        assertThat(imageObjectRepository.findById(replacementCandidateImageObject.getImageObjectId()))
            .get()
            .satisfies(imageObject -> {
                assertThat(imageObject.getCreatedByUser()).isNull();
                assertThat(imageObject.getLinkedAt()).isEqualTo(updatedRevision.getCandidateImageAssignedAt());
            });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateContentRevision_whenPreviousCandidateImageIsUnreferenced_deletesPreviousImageAfterCommit()
        throws Exception {

        try {
            Region region = saveRegion("UPDATE-IMAGE-DELETE");
            AppUser operator = saveUser("update-image-delete-operator@example.com");
            AppUser reviewer = saveUser("update-image-delete-reviewer@example.com");
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
            ImageObject previousCandidateImageObject = saveLinkedCandidateImageObject(
                operator,
                region,
                "content/revision-previous-delete.webp"
            );
            Long previousImageObjectId = previousCandidateImageObject.getImageObjectId();
            String previousObjectKey = previousCandidateImageObject.getObjectKey();
            ImageObject replacementCandidateImageObject = saveUploadCandidateImageObject(
                operator,
                region,
                "content/revision-replacement-delete.webp"
            );
            imageStorageGateway.addMetadata(
                replacementCandidateImageObject.getObjectKey(),
                replacementCandidateImageObject.getByteSize(),
                replacementCandidateImageObject.getChecksum()
            );
            ContentRevision contentRevision = saveRejectedRevision(
                content,
                operator,
                reviewer,
                null,
                previousCandidateImageObject
            );

            Long revisionId = contentRevision.getContentRevisionId();
            mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", revisionId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestWithRepresentativeImage(
                        replacementCandidateImageObject.getImageObjectId().toString()
                    )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

            assertThat(imageObjectRepository.existsById(previousImageObjectId)).isFalse();
            assertThat(imageStorageGateway.deletedObjectKeys()).containsExactly(previousObjectKey);
        } finally {
            deletePersistedTestData();
        }
    }

    @Test
    void updateContentRevision_whenPreviousCandidateImageIsStillReferenced_keepsPreviousImageActive()
        throws Exception {

        Region region = saveRegion("UPDATE-IMAGE-SHARED");
        AppUser operator = saveUser("update-image-shared-operator@example.com");
        AppUser reviewer = saveUser("update-image-shared-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject sharedCandidateImageObject = saveLinkedCandidateImageObject(
            operator,
            region,
            "content/revision-shared-candidate.webp"
        );
        Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
        ContentRevision contentRevision = saveRejectedRevision(
            content,
            operator,
            reviewer,
            null,
            sharedCandidateImageObject
        );
        Content otherContent = saveContent(region, operator, ContentStatus.PUBLISHED);
        ContentRevision otherContentRevision = saveRejectedRevision(
            otherContent,
            operator,
            reviewer,
            null,
            sharedCandidateImageObject
        );
        ImageObject replacementCandidateImageObject = saveUploadCandidateImageObject(
            operator,
            region,
            "content/revision-replacement-shared.webp"
        );
        imageStorageGateway.addMetadata(
            replacementCandidateImageObject.getObjectKey(),
            replacementCandidateImageObject.getByteSize(),
            replacementCandidateImageObject.getChecksum()
        );

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithRepresentativeImage(
                    replacementCandidateImageObject.getImageObjectId().toString()
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

        assertThat(contentRevisionRepository.findById(otherContentRevision.getContentRevisionId()))
            .get()
            .satisfies(revision -> assertThat(revision.getCandidateImageObject().getImageObjectId())
                .isEqualTo(sharedCandidateImageObject.getImageObjectId()));
        assertThat(imageObjectRepository.findById(sharedCandidateImageObject.getImageObjectId()))
            .get()
            .satisfies(imageObject -> assertThat(imageObject.getLifecycleStatus())
                .isEqualTo(ImageLifecycleStatus.ACTIVE));
    }

    @Test
    void updateContentRevision_whenRepresentativeImageObjectIdIsInvalid_returnsContractError() throws Exception {
        Region region = saveRegion("UPDATE-IMAGE-INVALID");
        AppUser operator = saveUser("update-image-invalid-operator@example.com");
        AppUser reviewer = saveUser("update-image-invalid-reviewer@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator, ContentStatus.PUBLISHED);
        ContentRevision contentRevision = saveRejectedRevision(content, operator, reviewer, null, null);

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithRepresentativeImage("0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithNumericRepresentativeImage()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", contentRevision.getContentRevisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestWithNullRepresentativeImage()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode, true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "Operator",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        ContentStatus status
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            CONTENT_PUBLISH_AT
        ));
    }

    private ContentSession saveSession(
        Content content,
        Region region
    ) {
        return contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-16T01:00:00Z"),
            Instant.parse("2026-08-16T03:00:00Z"),
            Instant.parse("2026-08-16T00:30:00Z"),
            Instant.parse("2026-08-16T01:30:00Z"),
            20
        ));
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

    private ImageObject saveLinkedCandidateImageObject(
        AppUser operator,
        Region region,
        String objectKey
    ) {
        ImageObject imageObject = imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            1L,
            "sha256:" + objectKey,
            Instant.parse("2027-01-01T00:00:00Z")
        ));
        imageObject.markLinked(CANDIDATE_IMAGE_ASSIGNED_AT);
        imageObjectRepository.flush();
        return imageObject;
    }

    private ImageObject saveUploadCandidateImageObject(
        AppUser operator,
        Region region,
        String objectKey
    ) {
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            1L,
            "sha256:" + objectKey,
            Instant.parse("2027-01-01T00:00:00Z")
        ));
    }

    private ContentRevision saveRejectedRevision(
        Content content,
        AppUser editor,
        AppUser reviewer,
        Instant publishAt,
        ImageObject candidateImageObject
    ) {
        ContentRevision contentRevision = saveRevision(
            content,
            editor,
            ContentRevisionStatus.EDIT_REJECTED,
            publishAt,
            REVIEWED_AT,
            reviewer
        );
        if (candidateImageObject != null) {
            contentRevision.assignCandidateImage(candidateImageObject, CANDIDATE_IMAGE_ASSIGNED_AT);
            contentRevisionRepository.saveAndFlush(contentRevision);
        }
        return contentRevision;
    }

    private ContentRevision saveRevision(
        Content content,
        AppUser editor,
        ContentRevisionStatus status,
        Instant publishAt,
        Instant reviewedAt,
        AppUser reviewedBy
    ) {
        return contentRevisionRepository.saveAndFlush(new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            editor,
            status,
            "반려된 김해 가야 문화 체험 수정본",
            "반려된 수정본 설명입니다.",
            "김해문화의전당 대공연장",
            "매일 11:00~19:00",
            "055-987-6543",
            "현장 안내를 따라주세요.",
            "만 8세 이상",
            "운동화",
            "시작 이틀 전까지 취소할 수 있습니다.",
            publishAt,
            SUBMITTED_AT,
            reviewedAt,
            reviewedBy,
            reviewedAt == null ? null : "보완이 필요합니다.",
            null,
            null,
            null
        ));
    }

    private String validRequestWithoutPublishAt() {
        return """
            {
              "title": "보완된 김해 가야문화 체험",
              "description": "보완된 행사 소개입니다.",
              "locationText": "김해시 가야의길 190",
              "operatingHoursText": "매주 토요일 10:00~16:00",
              "contactText": "055-000-0000",
              "precautions": "편한 복장으로 참여해 주세요.",
              "ageRequirement": "초등학생 이상",
              "materials": "필기도구",
              "cancellationPolicyText": "회차 시작 전까지 예약 취소가 가능합니다."
            }
            """;
    }

    private String validRequestWithPublishAt() {
        return """
            {
              "title": "보완된 김해 가야문화 체험",
              "description": "보완된 행사 소개입니다.",
              "locationText": "김해시 가야의길 190",
              "operatingHoursText": "매주 토요일 10:00~16:00",
              "contactText": "055-000-0000",
              "precautions": "편한 복장으로 참여해 주세요.",
              "ageRequirement": "초등학생 이상",
              "materials": "필기도구",
              "cancellationPolicyText": "회차 시작 전까지 예약 취소가 가능합니다.",
              "publishAt": "2026-08-20T09:00:00+09:00"
            }
            """;
    }

    private String validRequestWithRepresentativeImage(String representativeImageObjectId) {
        return appendFieldToRequest("\"representativeImageObjectId\": \"%s\"".formatted(representativeImageObjectId));
    }

    private String validRequestWithNumericRepresentativeImage() {
        return appendFieldToRequest("\"representativeImageObjectId\": 1");
    }

    private String validRequestWithNullRepresentativeImage() {
        return appendFieldToRequest("\"representativeImageObjectId\": null");
    }

    private void deletePersistedTestData() {
        contentSessionRepository.deleteAllInBatch();
        contentRevisionRepository.deleteAllInBatch();
        contentRepository.deleteAllInBatch();
        imageObjectRepository.deleteAllInBatch();
        userRoleAssignmentRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
        imageStorageGateway.reset();
    }

    private String appendFieldToRequest(String field) {
        String request = validRequestWithoutPublishAt();
        int closingBraceIndex = request.lastIndexOf('}');
        return request.substring(0, closingBraceIndex)
            + ",\n              "
            + field
            + "\n"
            + request.substring(closingBraceIndex);
    }

    @TestConfiguration
    static class TestImageStorageConfig {

        @Bean
        @Primary
        FakeImageStorageGateway fakeImageStorageGateway() {
            return new FakeImageStorageGateway();
        }
    }

    static class FakeImageStorageGateway implements ImageStorageGateway {

        private final Map<String, StoredObjectMetadata> metadataByObjectKey = new HashMap<>();
        private final List<String> deletedObjectKeys = new ArrayList<>();

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            return metadataByObjectKey.get(objectKey);
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void delete(String objectKey) {
            deletedObjectKeys.add(objectKey);
        }

        void addMetadata(String objectKey, long byteSize, String checksum) {
            metadataByObjectKey.put(objectKey, new StoredObjectMetadata(byteSize, checksum));
        }

        void reset() {
            metadataByObjectKey.clear();
            deletedObjectKeys.clear();
        }

        List<String> deletedObjectKeys() {
            return deletedObjectKeys;
        }
    }
}
