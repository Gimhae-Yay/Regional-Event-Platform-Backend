package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LatestContentRevisionDetailControllerSpringIntegrationTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-18T01:00:00Z");
    private static final Instant IMAGE_ASSIGNED_AT = Instant.parse("2026-08-18T01:01:00Z");
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-18T04:00:00Z");

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @MockitoBean
    private ImageStorageGateway imageStorageGateway;

    @Autowired
    LatestContentRevisionDetailControllerSpringIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void 최신_수정본_조회_성공_전후에_콘텐츠_수정본_이미지와_감사_기록이_변하지_않는다() throws Exception {
        Fixture fixture = saveFixture("success");
        when(imageStorageGateway.createPresignedGetUrl(fixture.objectKey()))
            .thenReturn(new PresignedViewUrl("https://example.invalid/image", IMAGE_EXPIRES_AT));
        DatabaseSnapshot before = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/operator/contents/{contentId}/revisions/latest",
                fixture.contentId()
            )
                .header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.operator())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revisionId().toString()))
            .andExpect(jsonPath("$.data.title").value("후보 제목"))
            .andExpect(jsonPath("$.data.representativeImageUrl")
                .value("https://example.invalid/image"));

        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    @Test
    void 최신_수정본_조회_소유권_실패_전후에_콘텐츠_수정본_이미지와_감사_기록이_변하지_않는다() throws Exception {
        Fixture fixture = saveFixture("forbidden");
        AppUser otherOperator = saveUser("other-operator@example.com");
        assignOperator(otherOperator, fixture.region());
        DatabaseSnapshot before = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/operator/contents/{contentId}/revisions/latest",
                fixture.contentId()
            )
                .header(HttpHeaders.AUTHORIZATION, bearerToken(otherOperator)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(snapshot(fixture)).isEqualTo(before);
        verifyNoInteractions(imageStorageGateway);
    }

    private Fixture saveFixture(String suffix) {
        Region region = regionRepository.saveAndFlush(new Region(
            "LATEST-" + suffix.toUpperCase(),
            "최신 수정본 지역",
            true
        ));
        AppUser operator = saveUser("latest-" + suffix + "@example.com");
        assignOperator(operator, region);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "원본 제목",
            "원본 설명",
            "원본 장소",
            "원본 운영 시간",
            "055-111-1111",
            "원본 유의사항",
            "원본 연령 조건",
            "원본 준비물",
            "원본 취소 규정",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        String objectKey = "contents/latest-" + suffix + ".webp";
        ImageObject candidateImage = ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            100L,
            "sha256:" + suffix,
            IMAGE_ASSIGNED_AT.plusSeconds(60)
        );
        candidateImage.markLinked(IMAGE_ASSIGNED_AT);
        imageObjectRepository.saveAndFlush(candidateImage);
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
            "055-000-0000",
            "후보 유의사항",
            "후보 연령 조건",
            "후보 준비물",
            "후보 취소 규정",
            20_000,
            null,
            SUBMITTED_AT,
            null,
            null,
            null,
            null,
            null,
            null
        );
        revision.assignCandidateImage(candidateImage, IMAGE_ASSIGNED_AT);
        contentRevisionRepository.saveAndFlush(revision);
        return new Fixture(
            operator,
            region,
            content.getContentId(),
            revision.getContentRevisionId(),
            candidateImage.getImageObjectId(),
            objectKey
        );
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void assignOperator(AppUser user, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.OPERATOR, region));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(
            jwtAccessTokenService,
            user.getUserId()
        );
    }

    private DatabaseSnapshot snapshot(Fixture fixture) {
        entityManager.flush();
        entityManager.clear();
        return new DatabaseSnapshot(
            contentRepository.count(),
            contentRevisionRepository.count(),
            imageObjectRepository.count(),
            auditEventRepository.count(),
            contentState(fixture.contentId()),
            revisionState(fixture.revisionId()),
            imageState(fixture.imageObjectId())
        );
    }

    private ContentState contentState(Long contentId) {
        return jdbcTemplate.queryForObject(
            "SELECT status, version_no, deleted_at, updated_at FROM content WHERE content_id = ?",
            (resultSet, rowNum) -> new ContentState(
                resultSet.getString("status"),
                resultSet.getInt("version_no"),
                toInstant(resultSet.getTimestamp("deleted_at")),
                resultSet.getTimestamp("updated_at").toInstant()
            ),
            contentId
        );
    }

    private RevisionState revisionState(Long revisionId) {
        return jdbcTemplate.queryForObject(
            """
                SELECT status, candidate_image_object_id, candidate_image_assigned_at,
                       reviewed_at, review_reason
                FROM content_revision
                WHERE content_revision_id = ?
                """,
            (resultSet, rowNum) -> new RevisionState(
                resultSet.getString("status"),
                resultSet.getObject("candidate_image_object_id", Long.class),
                toInstant(resultSet.getTimestamp("candidate_image_assigned_at")),
                toInstant(resultSet.getTimestamp("reviewed_at")),
                resultSet.getString("review_reason")
            ),
            revisionId
        );
    }

    private ImageState imageState(Long imageObjectId) {
        return jdbcTemplate.queryForObject(
            """
                SELECT lifecycle_status, linked_at, delete_attempt_count, last_delete_attempted_at
                FROM image_object
                WHERE image_object_id = ?
                """,
            (resultSet, rowNum) -> new ImageState(
                resultSet.getString("lifecycle_status"),
                resultSet.getTimestamp("linked_at").toInstant(),
                resultSet.getInt("delete_attempt_count"),
                toInstant(resultSet.getTimestamp("last_delete_attempted_at"))
            ),
            imageObjectId
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Fixture(
        AppUser operator,
        Region region,
        Long contentId,
        Long revisionId,
        Long imageObjectId,
        String objectKey
    ) {
    }

    private record DatabaseSnapshot(
        long contentCount,
        long revisionCount,
        long imageCount,
        long auditCount,
        ContentState content,
        RevisionState revision,
        ImageState image
    ) {
    }

    private record ContentState(
        String status,
        int versionNo,
        Instant deletedAt,
        Instant updatedAt
    ) {
    }

    private record RevisionState(
        String status,
        Long candidateImageObjectId,
        Instant candidateImageAssignedAt,
        Instant reviewedAt,
        String reviewReason
    ) {
    }

    private record ImageState(
        String lifecycleStatus,
        Instant linkedAt,
        int deleteAttemptCount,
        Instant lastDeleteAttemptedAt
    ) {
    }
}
