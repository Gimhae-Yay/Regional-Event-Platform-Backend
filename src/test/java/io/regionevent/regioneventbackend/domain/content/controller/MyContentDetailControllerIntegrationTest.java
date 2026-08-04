package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;
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
@Import(MyContentDetailControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class MyContentDetailControllerIntegrationTest {

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
    private EntityManager entityManager;

    @Autowired
    private FakeImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @Test
    void 소유_운영자는_내_콘텐츠_상세를_조회하고_데이터를_변경하지_않는다() throws Exception {
        Region region = saveRegion("MY-CONTENT-DETAIL");
        AppUser operator = saveOperator("owner-operator", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.PENDING, true);
        DatabaseSnapshot before = snapshot(fixture);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 콘텐츠 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.title").value("김해 가야 문화 체험"))
            .andExpect(jsonPath("$.data.description").value("김해 가야 문화를 체험하는 행사입니다."))
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/1"))
            .andExpect(jsonPath("$.data.representativeImageUrlExpiresAt").value("2026-08-01T00:05:01Z"))
            .andExpect(jsonPath("$.data.locationText").value("김해문화의전당"))
            .andExpect(jsonPath("$.data.operatingHoursText").value("매주 토요일 10:00~16:00"))
            .andExpect(jsonPath("$.data.contactText").value("055-000-0000"))
            .andExpect(jsonPath("$.data.precautions").value("편한 복장으로 참여해 주세요."))
            .andExpect(jsonPath("$.data.ageRequirement").value("초등학생 이상"))
            .andExpect(jsonPath("$.data.materials").value("필기도구"))
            .andExpect(jsonPath("$.data.cancellationPolicyText").value("회차 시작 전까지 예약 전체 취소가 가능합니다."))
            .andExpect(jsonPath("$.data.publishAt").value("2026-08-20T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.rejectionReason").value(nullValue()))
            .andExpect(jsonPath("$.data.createdAt", endsWith("Z")))
            .andExpect(jsonPath("$.data.updatedAt", endsWith("Z")))
            .andExpect(jsonPath("$.data.imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.representativeImageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.operatorId").doesNotExist())
            .andExpect(jsonPath("$.data.reviewerId").doesNotExist())
            .andExpect(jsonPath("$.data.adminUserId").doesNotExist())
            .andExpect(jsonPath("$.data.sessions").doesNotExist())
            .andExpect(content().string(not(containsString(fixture.objectKey()))))
            .andExpect(content().string(not(containsString(fixture.operatorLoginIdentifier()))));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(fixture.objectKey());
        assertDatabaseUnchanged(before, fixture);
    }

    @Test
    void 반려된_콘텐츠는_최신_반려_사유를_반환한다() throws Exception {
        Region region = saveRegion("REJECTED-DETAIL");
        AppUser operator = saveOperator("rejected-owner", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.REJECTED, true);
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.REJECTED,
            "이전 반려 사유",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            null,
            ContentLogStatus.REJECTED,
            "최신 반려 사유",
            Instant.parse("2026-08-01T01:00:00Z")
        ));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectionReason").value("최신 반려 사유"));
    }

    @Test
    void 반려_상태가_아닌_콘텐츠는_반려_사유를_null로_반환한다() throws Exception {
        Region region = saveRegion("NON-REJECTED");
        AppUser operator = saveOperator("non-rejected-owner", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.APPROVED, true);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.rejectionReason").value(nullValue()));
    }

    @Test
    void 반려_상태인데_반려_로그가_없으면_내부_오류를_반환한다() throws Exception {
        Region region = saveRegion("REJECTED-MISSING-LOG");
        AppUser operator = saveOperator("rejected-missing-log-owner", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.REJECTED, true);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void 잘못된_콘텐츠_식별자는_INVALID_INPUT을_반환한다() throws Exception {
        Region region = saveRegion("INVALID-MY-CONTENT-ID");
        AppUser operator = saveOperator("invalid-id-owner", region, AppUserStatus.ACTIVE);

        for (String invalidContentId : List.of("0", "01", "+1", "abc", "9223372036854775808")) {
            mockMvc.perform(get("/api/v1/operator/contents/{contentId}", invalidContentId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 인증이_없거나_무효_토큰이면_UNAUTHENTICATED를_반환한다() throws Exception {
        Region region = saveRegion("AUTH-DETAIL");
        AppUser operator = saveOperator("auth-owner", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.PENDING, true);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", fixture.contentId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 운영자_인가가_맞지_않으면_FORBIDDEN을_반환하고_URL을_발급하지_않는다() throws Exception {
        Region ownerRegion = saveRegion("OWNER-REGION");
        Region otherRegion = saveRegion("OTHER-REGION");
        AppUser owner = saveOperator("detail-owner", ownerRegion, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(owner, ownerRegion, ContentStatus.PENDING, true);
        AppUser inactiveOperator = saveOperator("inactive-operator", ownerRegion, AppUserStatus.WITHDRAWING);
        AppUser visitor = saveVisitor("visitor-user");
        AppUser noRoleUser = saveUser("no-role-user", AppUserStatus.ACTIVE);
        AppUser otherOperator = saveOperator("other-operator", ownerRegion, AppUserStatus.ACTIVE);
        AppUser otherRegionOwner = saveOperator("other-region-owner", otherRegion, AppUserStatus.ACTIVE);
        ContentFixture regionMismatchFixture = saveContentFixture(
            otherRegionOwner,
            ownerRegion,
            ContentStatus.PENDING,
            true
        );

        for (RequestCase requestCase : List.of(
            new RequestCase(inactiveOperator, fixture.contentId()),
            new RequestCase(visitor, fixture.contentId()),
            new RequestCase(noRoleUser, fixture.contentId()),
            new RequestCase(otherOperator, fixture.contentId()),
            new RequestCase(otherRegionOwner, regionMismatchFixture.contentId())
        )) {
            mockMvc.perform(get("/api/v1/operator/contents/{contentId}", requestCase.contentId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(requestCase.user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 없는_콘텐츠와_소프트_삭제_콘텐츠는_NOT_FOUND를_반환한다() throws Exception {
        Region region = saveRegion("NOT-FOUND-DETAIL");
        AppUser operator = saveOperator("not-found-owner", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.PENDING, true);
        Content deletedContent = contentRepository.findById(fixture.contentId()).orElseThrow();
        deletedContent.softDelete();
        contentRepository.saveAndFlush(deletedContent);
        entityManager.clear();

        for (Long contentId : List.of(9_999_999L, fixture.contentId())) {
            mockMvc.perform(get("/api/v1/operator/contents/{contentId}", contentId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 대표_이미지_정합성이_깨졌거나_S3가_실패하면_INTERNAL_SERVER_ERROR를_반환한다() throws Exception {
        Region region = saveRegion("IMAGE-FAIL-DETAIL");
        AppUser operator = saveOperator("image-fail-owner", region, AppUserStatus.ACTIVE);
        ContentFixture missingImageFixture = saveContentFixture(operator, region, ContentStatus.PENDING, false);
        ContentFixture unlinkedImageFixture = saveContentFixture(
            operator,
            region,
            ContentStatus.PENDING,
            true,
            false,
            false
        );
        ContentFixture inactiveImageFixture = saveContentFixture(
            operator,
            region,
            ContentStatus.PENDING,
            true,
            true,
            true
        );

        for (Long contentId : List.of(
            missingImageFixture.contentId(),
            unlinkedImageFixture.contentId(),
            inactiveImageFixture.contentId()
        )) {
            mockMvc.perform(get("/api/v1/operator/contents/{contentId}", contentId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        }
        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();

        ContentFixture s3FailureFixture = saveContentFixture(operator, region, ContentStatus.PENDING, true);
        imageStorageGateway.failGetUrl();

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", s3FailureFixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(s3FailureFixture.objectKey());
    }

    @Test
    void 비버전_경로는_지원하지_않는다() throws Exception {
        Region region = saveRegion("UNVERSIONED");
        AppUser operator = saveOperator("unversioned-owner", region, AppUserStatus.ACTIVE);
        ContentFixture fixture = saveContentFixture(operator, region, ContentStatus.PENDING, true);

        mockMvc.perform(get("/operator/contents/{contentId}", fixture.contentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.invalid/view/1"));

        assertThat(imageStorageGateway.requestedObjectKeys()).containsExactly(fixture.objectKey());
    }

    private ContentFixture saveContentFixture(
        AppUser operator,
        Region region,
        ContentStatus status,
        boolean assignRepresentativeImage
    ) {
        return saveContentFixture(operator, region, status, assignRepresentativeImage, true, false);
    }

    private ContentFixture saveContentFixture(
        AppUser operator,
        Region region,
        ContentStatus status,
        boolean assignRepresentativeImage,
        boolean markImageLinked,
        boolean markImageInactive
    ) {
        Content content = contentRepository.saveAndFlush(newContent(region, operator, status));
        ImageObject imageObject = null;
        String objectKey = null;
        if (assignRepresentativeImage) {
            objectKey = "content/my-detail/" + content.getContentId() + ".webp";
            imageObject = imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
                objectKey,
                operator,
                region,
                "image/webp",
                524_288L,
                CHECKSUM,
                Instant.parse("2026-08-02T00:00:00Z")
            ));
            if (markImageLinked) {
                imageObject.markLinked(IMAGE_ASSIGNED_AT);
            }
            if (markImageInactive) {
                imageObject.markDeletePending();
            }
            imageObjectRepository.saveAndFlush(imageObject);
            content.assignRepresentativeImage(imageObject, IMAGE_ASSIGNED_AT);
            contentRepository.saveAndFlush(content);
        }
        ContentSession session = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-21T01:00:00Z"),
            Instant.parse("2026-08-21T03:00:00Z"),
            Instant.parse("2026-08-21T00:30:00Z"),
            Instant.parse("2026-08-21T02:30:00Z"),
            20
        ));
        entityManager.clear();
        Long imageObjectId = imageObject == null ? null : imageObject.getImageObjectId();
        return new ContentFixture(
            content.getContentId(),
            session.getSessionId(),
            imageObjectId,
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

    private AppUser saveOperator(
        String loginIdentifierPrefix,
        Region region,
        AppUserStatus status
    ) {
        AppUser operator = saveUser(loginIdentifierPrefix, status);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
    }

    private AppUser saveVisitor(String loginIdentifierPrefix) {
        AppUser visitor = saveUser(loginIdentifierPrefix, AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        return visitor;
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

    private DatabaseSnapshot snapshot(ContentFixture fixture) {
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        ContentSession session = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        ImageObject imageObject = imageObjectRepository.findById(fixture.imageObjectId()).orElseThrow();
        return new DatabaseSnapshot(
            contentRepository.count(),
            contentSessionRepository.count(),
            imageObjectRepository.count(),
            contentLogRepository.count(),
            content.getStatus(),
            content.getUpdatedAt(),
            content.getRepresentativeImageObject().getImageObjectId(),
            session.getStatus(),
            session.getRemainingCapacity(),
            imageObject.getLifecycleStatus(),
            imageObject.getLinkedAt()
        );
    }

    private void assertDatabaseUnchanged(DatabaseSnapshot before, ContentFixture fixture) {
        entityManager.clear();
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    record ContentFixture(
        Long contentId,
        Long sessionId,
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
        ContentStatus contentStatus,
        Instant contentUpdatedAt,
        Long representativeImageObjectId,
        ContentSessionStatus sessionStatus,
        int remainingCapacity,
        ImageLifecycleStatus imageLifecycleStatus,
        Instant imageLinkedAt
    ) {
    }

    record RequestCase(AppUser user, Long contentId) {
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
        private boolean failGetUrl;

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
            if (failGetUrl) {
                throw new ImageStorageException("failed to create presigned get url");
            }
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

        void failGetUrl() {
            failGetUrl = true;
        }

        void reset() {
            requestedObjectKeys.clear();
            failGetUrl = false;
        }
    }
}
