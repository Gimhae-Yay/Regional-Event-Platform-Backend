package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
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
@Import(ContentRevisionReviewControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class ContentRevisionReviewControllerIntegrationTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant IMAGE_ASSIGNED_AT = Instant.parse("2026-07-31T23:00:00Z");
    private static final String IMAGE_OBJECT_KEY = "content/revisions/private-candidate.webp";
    private static final String CHECKSUM = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

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
    private ContentLogRepository contentLogRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FakeImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @Test
    void 공개_콘텐츠_수정본_상세와_현재_회차를_조회하고_데이터와_감사를_변경하지_않는다()
        throws Exception {

        Region region = saveRegion("PUBLISHED-DETAIL");
        AppUser regionAdmin = saveRegionAdmin("published-admin@example.com", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveReviewFixture(region, ContentStatus.PUBLISHED, null, true);
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("심사 대기 콘텐츠 수정본 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revisionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.reviewType").value("PUBLISHED_REVISION"))
            .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.title").value("김해 가야문화 체험 일정 변경"))
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/1"))
            .andExpect(jsonPath("$.data.representativeImageUrlExpiresAt").value("2026-08-01T00:05:01Z"))
            .andExpect(jsonPath("$.data.candidatePublishAt").doesNotExist())
            .andExpect(jsonPath("$.data.sessions[0].sessionId").value(fixture.sessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.sessions[0].startsAt").value("2026-08-21T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].endsAt").value("2026-08-21T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].checkinOpenAt").value("2026-08-21T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].checkinCloseAt").value("2026-08-21T11:30:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].capacity").value(20))
            .andExpect(jsonPath("$.data.sessions[0].remainingCapacity").value(20))
            .andExpect(jsonPath("$.data.submittedAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.editorUserId").doesNotExist())
            .andExpect(jsonPath("$.data.operatorId").doesNotExist())
            .andExpect(content().string(not(containsString(fixture.objectKey()))))
            .andExpect(content().string(not(containsString("published-operator@example.com"))));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(fixture.objectKey());
        assertDatabaseUnchanged(before, fixture);
    }

    @Test
    void 공개_전_수정본은_APPROVED에서_PENDING으로_전이한_이력과_후보_공개시각을_반환한다()
        throws Exception {

        Region region = saveRegion("PRE-PUBLIC-DETAIL");
        AppUser regionAdmin = saveRegionAdmin("pre-public-admin@example.com", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveReviewFixture(
            region,
            ContentStatus.PENDING,
            CANDIDATE_PUBLISH_AT,
            true
        );
        savePrePublicationHistory(fixture.contentId());

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewType").value("PRE_PUBLIC_REVISION"))
            .andExpect(jsonPath("$.data.contentStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.candidatePublishAt").value("2026-08-20T09:00:00+09:00"));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(fixture.objectKey());
    }

    @Test
    void 단기_URL은_응답마다_새로_발급하고_영속화하지_않는다() throws Exception {
        Region region = saveRegion("FRESH-URL");
        AppUser regionAdmin = saveRegionAdmin("fresh-url-admin@example.com", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveReviewFixture(region, ContentStatus.PUBLISHED, null, true);

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/1"));
        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/2"));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(
            fixture.objectKey(),
            fixture.objectKey()
        );
        assertThat(imageObjectRepository.findById(fixture.imageObjectId()))
            .get()
            .extracting(ImageObject::getObjectKey)
            .isEqualTo(fixture.objectKey());
    }

    @Test
    void 인증이_없거나_활성_지역_관리자가_아니면_조회하지_않는다() throws Exception {
        Region region = saveRegion("AUTHORIZATION");
        ReviewFixture fixture = saveReviewFixture(region, ContentStatus.PUBLISHED, null, true);
        AppUser visitor = saveUser("review-visitor@example.com", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        AppUser inactiveAdmin = saveRegionAdmin(
            "inactive-review-admin@example.com",
            region,
            AppUserStatus.WITHDRAWING
        );

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(inactiveAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 다른_담당_지역의_수정본은_URL을_발급하지_않고_FORBIDDEN으로_거부한다() throws Exception {
        Region assignedRegion = saveRegion("ASSIGNED-REGION");
        Region otherRegion = saveRegion("OTHER-REGION");
        AppUser regionAdmin = saveRegionAdmin(
            "other-region-admin@example.com",
            assignedRegion,
            AppUserStatus.ACTIVE
        );
        ReviewFixture fixture = saveReviewFixture(otherRegion, ContentStatus.PUBLISHED, null, true);

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 잘못된_식별자_형식과_범위는_계약된_오류로_거부한다() throws Exception {
        Region region = saveRegion("INVALID-ID");
        AppUser regionAdmin = saveRegionAdmin("invalid-id-admin@example.com", region, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/0")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/region-admin/content-revisions/not-a-number")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/region-admin/content-revisions/9223372036854775808")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 미존재_종결_수정본과_소프트_삭제_원본의_수정본은_NOT_FOUND로_비노출한다()
        throws Exception {

        Region region = saveRegion("NOT-FOUND");
        AppUser regionAdmin = saveRegionAdmin("not-found-admin@example.com", region, AppUserStatus.ACTIVE);
        ReviewFixture terminalFixture = saveReviewFixture(
            region,
            ContentStatus.PUBLISHED,
            null,
            true,
            ContentRevisionStatus.EDIT_APPROVED
        );
        ReviewFixture deletedFixture = saveReviewFixture(region, ContentStatus.PENDING, null, true);
        Content deletedContent = contentRepository.findById(deletedFixture.contentId()).orElseThrow();
        deletedContent.softDelete();
        contentRepository.saveAndFlush(deletedContent);

        for (Long revisionId : List.of(999_999L, terminalFixture.revisionId(), deletedFixture.revisionId())) {
            mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", revisionId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 원본_상태와_후보_공개시각_조합이_잘못되면_INTERNAL_SERVER_ERROR를_반환한다()
        throws Exception {

        Region region = saveRegion("INVALID-STATE");
        AppUser regionAdmin = saveRegionAdmin("invalid-state-admin@example.com", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveReviewFixture(
            region,
            ContentStatus.PUBLISHED,
            CANDIDATE_PUBLISH_AT,
            true
        );

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 후보_이미지가_ACTIVE_직접_연결이_아니면_URL을_발급하지_않는다() throws Exception {
        Region region = saveRegion("INVALID-IMAGE");
        AppUser regionAdmin = saveRegionAdmin("invalid-image-admin@example.com", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveReviewFixture(region, ContentStatus.PUBLISHED, null, false);

        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", fixture.revisionId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    private ReviewFixture saveReviewFixture(
        Region region,
        ContentStatus contentStatus,
        Instant candidatePublishAt,
        boolean activeImage
    ) {
        return saveReviewFixture(
            region,
            contentStatus,
            candidatePublishAt,
            activeImage,
            ContentRevisionStatus.EDIT_REQUESTED
        );
    }

    private ReviewFixture saveReviewFixture(
        Region region,
        ContentStatus contentStatus,
        Instant candidatePublishAt,
        boolean activeImage,
        ContentRevisionStatus revisionStatus
    ) {
        AppUser operator = saveUser(
            contentStatus.name().toLowerCase() + "-operator@example.com",
            AppUserStatus.ACTIVE
        );
        AppUser editor = saveUser(
            contentStatus.name().toLowerCase() + "-editor@example.com",
            AppUserStatus.ACTIVE
        );
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "원본 콘텐츠 제목",
            "원본 콘텐츠 설명",
            "원본 위치",
            "원본 운영 시간",
            "055-111-1111",
            "원본 유의사항",
            "원본 연령 조건",
            "원본 준비물",
            "원본 취소 안내",
            Instant.parse("2026-08-15T00:00:00Z")
        ));
        String objectKey = IMAGE_OBJECT_KEY + "-" + (imageObjectRepository.count() + 1);
        ImageObject imageObject = imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            editor,
            region,
            "image/webp",
            524_288L,
            CHECKSUM,
            Instant.parse("2026-08-02T00:00:00Z")
        ));
        imageObject.markLinked(IMAGE_ASSIGNED_AT);
        if (!activeImage) {
            imageObject.markDeletePending();
        }
        imageObjectRepository.saveAndFlush(imageObject);

        AppUser reviewer = revisionStatus == ContentRevisionStatus.EDIT_REQUESTED
            ? null
            : saveUser("reviewer-" + contentStatus.name().toLowerCase() + "@example.com", AppUserStatus.ACTIVE);
        ContentRevision revision = new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            editor,
            revisionStatus,
            "김해 가야문화 체험 일정 변경",
            "가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매주 토요일 10:00~16:00",
            "055-000-0000",
            "편한 복장으로 참여해 주세요.",
            "초등학생 이상",
            "필기도구",
            "회차 시작 전까지 예약 전체 취소가 가능합니다.",
            candidatePublishAt,
            SUBMITTED_AT,
            revisionStatus == ContentRevisionStatus.EDIT_REQUESTED ? null : SUBMITTED_AT.plusSeconds(60),
            reviewer,
            revisionStatus == ContentRevisionStatus.EDIT_REJECTED ? "반려 사유" : null,
            null,
            null,
            null
        );
        revision.assignCandidateImage(imageObject, IMAGE_ASSIGNED_AT);
        contentRevisionRepository.saveAndFlush(revision);

        ContentSession session = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-21T01:00:00Z"),
            Instant.parse("2026-08-21T03:00:00Z"),
            Instant.parse("2026-08-21T00:30:00Z"),
            Instant.parse("2026-08-21T02:30:00Z"),
            20
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET status = ? WHERE session_id = ?",
            ContentSessionStatus.SCHEDULED.name(),
            session.getSessionId()
        );
        entityManager.clear();

        return new ReviewFixture(
            content.getContentId(),
            revision.getContentRevisionId(),
            session.getSessionId(),
            imageObject.getImageObjectId(),
            objectKey,
            contentStatus,
            revisionStatus
        );
    }

    private void savePrePublicationHistory(Long contentId) {
        Content content = contentRepository.findById(contentId).orElseThrow();
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            null,
            ContentLogStatus.APPROVED,
            null,
            SUBMITTED_AT.minusSeconds(60)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            null,
            ContentLogStatus.PENDING,
            null,
            SUBMITTED_AT
        ));
        entityManager.clear();
    }

    private AppUser saveRegionAdmin(
        String loginIdentifier,
        Region region,
        AppUserStatus status
    ) {
        AppUser regionAdmin = saveUser(loginIdentifier, status);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            regionAdmin,
            UserRole.REGION_ADMIN,
            region
        ));
        return regionAdmin;
    }

    private AppUser saveUser(String loginIdentifier, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "테스트 사용자",
            "010-1234-5678",
            status
        ));
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "테스트 지역", true));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(
            contentRepository.count(),
            contentRevisionRepository.count(),
            contentSessionRepository.count(),
            imageObjectRepository.count(),
            contentLogRepository.count(),
            auditEventRepository.count()
        );
    }

    private void assertDatabaseUnchanged(DatabaseSnapshot before, ReviewFixture fixture) {
        entityManager.clear();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(contentRepository.findById(fixture.contentId()))
            .get()
            .satisfies(content -> {
                assertThat(content.getStatus()).isEqualTo(fixture.contentStatus());
                assertThat(content.getDeletedAt()).isNull();
                assertThat(content.getTitle()).isEqualTo("원본 콘텐츠 제목");
            });
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .get()
            .satisfies(revision -> {
                assertThat(revision.getStatus()).isEqualTo(fixture.revisionStatus());
                assertThat(revision.getReviewedAt()).isNull();
                assertThat(revision.getCandidateImageObject().getImageObjectId())
                    .isEqualTo(fixture.imageObjectId());
            });
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .get()
            .satisfies(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
                assertThat(session.getRemainingCapacity()).isEqualTo(20);
            });
        assertThat(imageObjectRepository.findById(fixture.imageObjectId()))
            .get()
            .satisfies(imageObject -> {
                assertThat(imageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
                assertThat(imageObject.getObjectKey()).isEqualTo(fixture.objectKey());
                assertThat(imageObject.getLinkedAt()).isEqualTo(IMAGE_ASSIGNED_AT);
            });
    }

    record ReviewFixture(
        Long contentId,
        Long revisionId,
        Long sessionId,
        Long imageObjectId,
        String objectKey,
        ContentStatus contentStatus,
        ContentRevisionStatus revisionStatus
    ) {
    }

    record DatabaseSnapshot(
        long contents,
        long revisions,
        long sessions,
        long images,
        long contentLogs,
        long auditEvents
    ) {
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

        private final List<String> requestedObjectKeys = new ArrayList<>();

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            requestedObjectKeys.add(objectKey);
            int sequence = requestedObjectKeys.size();
            return new PresignedViewUrl(
                "https://example.invalid/view/" + sequence,
                Instant.parse("2026-08-01T00:05:00Z").plusSeconds(sequence)
            );
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }

        List<String> requestedObjectKeys() {
            return List.copyOf(requestedObjectKeys);
        }

        void reset() {
            requestedObjectKeys.clear();
        }
    }
}
