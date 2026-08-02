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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
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
@Import(OriginalContentReviewDetailControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class OriginalContentReviewDetailControllerIntegrationTest {

    private static final Instant IMAGE_ASSIGNED_AT = Instant.parse("2026-08-01T00:00:00Z");
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
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private ContentLogRepository contentLogRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FakeImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @Test
    void 원본_심사_대기_콘텐츠와_최초_PENDING_회차를_조회하고_데이터를_변경하지_않는다()
        throws Exception {

        Region region = saveRegion("DETAIL");
        AppUser regionAdmin = saveRegionAdmin("detail-admin", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveOriginalReviewFixture(region, false);
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("승인 검토 콘텐츠 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.regionId").value(region.getRegionId().toString()))
            .andExpect(jsonPath("$.data.operatorId").value(fixture.operatorId().toString()))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.title").value("김해 가야 문화 체험"))
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/1"))
            .andExpect(jsonPath("$.data.representativeImageUrlExpiresAt").value("2026-08-01T00:05:01Z"))
            .andExpect(jsonPath("$.data.publishAt").value("2026-08-20T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions.length()").value(2))
            .andExpect(jsonPath("$.data.sessions[0].sessionId").value(fixture.firstSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.sessions[0].startsAt").value("2026-08-21T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].remainingCapacity").value(20))
            .andExpect(jsonPath("$.data.sessions[1].sessionId").value(fixture.secondSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[1].status").value("PENDING"))
            .andExpect(jsonPath("$.data.imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.representativeImageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.sessions[0].reviewedByUserId").doesNotExist())
            .andExpect(content().string(not(containsString(fixture.objectKey()))))
            .andExpect(content().string(not(containsString(fixture.operatorLoginIdentifier()))));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(fixture.objectKey());
        assertDatabaseUnchanged(before, fixture);
    }

    @Test
    void 단기_URL은_응답마다_새로_발급하고_영속화하지_않는다() throws Exception {
        Region region = saveRegion("FRESH-URL");
        AppUser regionAdmin = saveRegionAdmin("fresh-url-admin", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveOriginalReviewFixture(region, false);

        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/1"));
        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
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
    void 반려_후_재제출한_원본_심사_대기_콘텐츠를_조회한다() throws Exception {
        Region region = saveRegion("RESUBMISSION");
        AppUser regionAdmin = saveRegionAdmin("resubmission-admin", region, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveOriginalReviewFixture(region, false);
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            null,
            ContentLogStatus.REJECTED,
            "회차를 보완해 주세요.",
            Instant.parse("2026-08-01T00:00:30Z")
        ));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.sessions.length()").value(2));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(fixture.objectKey());
    }

    @Test
    void 인증이_없거나_비활성_지역_관리자면_조회하지_않는다() throws Exception {
        Region region = saveRegion("INACTIVE");
        ReviewFixture fixture = saveOriginalReviewFixture(region, false);
        AppUser inactiveAdmin = saveRegionAdmin("inactive-admin", region, AppUserStatus.WITHDRAWING);

        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(inactiveAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 타_지역_관리자는_이미지_URL을_발급하지_않고_거부한다() throws Exception {
        Region assignedRegion = saveRegion("ASSIGNED");
        Region targetRegion = saveRegion("TARGET");
        AppUser regionAdmin = saveRegionAdmin("other-region-admin", assignedRegion, AppUserStatus.ACTIVE);
        ReviewFixture fixture = saveOriginalReviewFixture(targetRegion, false);

        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 소프트_삭제_비대상_상태_공개_전_수정_심사_대기_콘텐츠는_비노출한다() throws Exception {
        Region region = saveRegion("HIDDEN");
        AppUser regionAdmin = saveRegionAdmin("hidden-admin", region, AppUserStatus.ACTIVE);
        ReviewFixture deletedFixture = saveOriginalReviewFixture(region, false);
        Content deletedContent = contentRepository.findById(deletedFixture.contentId()).orElseThrow();
        deletedContent.softDelete();
        contentRepository.saveAndFlush(deletedContent);
        ReviewFixture prePublicationRevisionFixture = saveOriginalReviewFixture(region, true);
        Content approvedContent = contentRepository.saveAndFlush(newContent(
            region,
            saveUser("approved-operator", AppUserStatus.ACTIVE),
            ContentStatus.APPROVED
        ));

        for (Long contentId : List.of(
            9_999_999L,
            deletedFixture.contentId(),
            prePublicationRevisionFixture.contentId(),
            approvedContent.getContentId()
        )) {
            mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", contentId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 식별자_형식과_범위_오류는_계약된_오류로_반환한다() throws Exception {
        Region region = saveRegion("INVALID-ID");
        AppUser regionAdmin = saveRegionAdmin("invalid-id-admin", region, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/contents/0")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/region-admin/contents/not-a-number")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/region-admin/contents/01")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/region-admin/contents/+1")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/region-admin/contents/9223372036854775808")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    private ReviewFixture saveOriginalReviewFixture(Region region, boolean prePublicationRevision) {
        AppUser operator = saveUser("operator", AppUserStatus.ACTIVE);
        AppUser imageUploader = saveUser("image-uploader", AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(newContent(region, operator, ContentStatus.PENDING));
        String objectKey = "content/original-review/" + content.getContentId() + ".webp";
        ImageObject imageObject = imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            imageUploader,
            region,
            "image/webp",
            524_288L,
            CHECKSUM,
            Instant.parse("2026-08-02T00:00:00Z")
        ));
        imageObject.markLinked(IMAGE_ASSIGNED_AT);
        imageObjectRepository.saveAndFlush(imageObject);
        content.assignRepresentativeImage(imageObject, IMAGE_ASSIGNED_AT);
        contentRepository.saveAndFlush(content);

        if (prePublicationRevision) {
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                null,
                ContentLogStatus.APPROVED,
                null,
                Instant.parse("2026-08-01T00:00:00Z")
            ));
        }
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            null,
            ContentLogStatus.PENDING,
            null,
            Instant.parse("2026-08-01T00:01:00Z")
        ));

        ContentSession firstSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-21T01:00:00Z"),
            Instant.parse("2026-08-21T03:00:00Z"),
            Instant.parse("2026-08-21T00:30:00Z"),
            Instant.parse("2026-08-21T02:30:00Z"),
            20
        ));
        ContentSession secondSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-22T01:00:00Z"),
            Instant.parse("2026-08-22T03:00:00Z"),
            Instant.parse("2026-08-22T00:30:00Z"),
            Instant.parse("2026-08-22T02:30:00Z"),
            30
        ));
        entityManager.clear();

        return new ReviewFixture(
            content.getContentId(),
            operator.getUserId(),
            firstSession.getSessionId(),
            secondSession.getSessionId(),
            imageObject.getImageObjectId(),
            objectKey,
            operator.getLoginIdentifier()
        );
    }

    private Content newContent(Region region, AppUser operator, ContentStatus status) {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매주 토요일 10:00~16:00",
            "055-000-0000",
            "편한 복장으로 참여해 주세요.",
            "초등학생 이상",
            "필기도구",
            "회차 시작 전까지 예약 전체 취소가 가능합니다.",
            Instant.parse("2026-08-20T00:00:00Z")
        );
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

    private AppUser saveUser(String loginIdentifierPrefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifierPrefix + suffix + "@example.com",
            "hashed-password",
            "테스트 사용자",
            "010-1234-5678",
            status
        ));
    }

    private Region saveRegion(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(
            prefix + suffix,
            prefix + " 지역",
            true
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(
            contentRepository.count(),
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
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
                assertThat(content.getDeletedAt()).isNull();
                assertThat(content.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(fixture.imageObjectId());
            });
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .get()
            .satisfies(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
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
        Long operatorId,
        Long firstSessionId,
        Long secondSessionId,
        Long imageObjectId,
        String objectKey,
        String operatorLoginIdentifier
    ) {
    }

    record DatabaseSnapshot(
        long contents,
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
