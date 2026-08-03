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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
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
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PublicContentControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class PublicContentControllerIntegrationTest {

    private static final Instant PUBLISH_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final String CHECKSUM = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private ContentRevisionRepository contentRevisionRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

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
    void 공개_콘텐츠를_조회하고_단기_URL만_반환하며_영속_상태를_변경하지_않는다() throws Exception {
        Region region = saveRegion("PUBLIC", true);
        AppUser operator = saveOperator();
        Content content = saveContent(region, operator, "김해 가야문화 체험", PUBLISH_AT, true);
        ContentSession session = saveScheduledSession(
            content,
            region,
            operator,
            Instant.now().plusSeconds(3_600),
            10
        );
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 콘텐츠 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contents[0].contentId").value(content.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[0].contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.contents[0].title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.contents[0].locationText").value("김해시 가야의길 190"))
            .andExpect(jsonPath("$.data.contents[0].representativeImageUrl")
                .value("https://example.invalid/view/1"))
            .andExpect(jsonPath("$.data.contents[0].representativeImageUrlExpiresAt")
                .value("2026-08-01T00:05:01Z"))
            .andExpect(jsonPath("$.data.contents[0].reservationAvailable").value(true))
            .andExpect(jsonPath("$.data.contents[0].imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].operatorId").doesNotExist())
            .andExpect(content().string(not(containsString("contents/"))));

        assertThat(imageStorageGateway.requestedObjectKeys()).hasSize(1);
        assertDatabaseUnchanged(before, content, session);
    }

    @Test
    void 콘텐츠_유형과_예약_가능_여부_필터를_함께_적용한다() throws Exception {
        Region region = saveRegion("FILTER", true);
        AppUser operator = saveOperator();
        Content reservable = saveContent(region, operator, "예약 가능", PUBLISH_AT, true);
        Content noCapacity = saveContent(region, operator, "잔여석 없음", PUBLISH_AT.minusSeconds(1), true);
        Content pastSession = saveContent(region, operator, "지난 회차", PUBLISH_AT.minusSeconds(2), true);
        saveScheduledSession(reservable, region, operator, Instant.now().plusSeconds(3_600), 10);
        saveScheduledSession(noCapacity, region, operator, Instant.now().plusSeconds(3_600), 0);
        saveScheduledSession(pastSession, region, operator, Instant.now().minusSeconds(3_600), 10);

        mockMvc.perform(get("/api/v1/contents")
                .param("regionId", region.getRegionId().toString())
                .param("contentType", "EVENT_EXPERIENCE")
                .param("reservationAvailable", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents.length()").value(1))
            .andExpect(jsonPath("$.data.contents[0].contentId").value(reservable.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[0].reservationAvailable").value(true));
        mockMvc.perform(get("/api/v1/contents")
                .param("regionId", region.getRegionId().toString())
                .param("contentType", "EVENT_EXPERIENCE")
                .param("reservationAvailable", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents.length()").value(2))
            .andExpect(jsonPath("$.data.contents[0].contentId").value(noCapacity.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[1].contentId").value(pastSession.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[0].reservationAvailable").value(false))
            .andExpect(jsonPath("$.data.contents[1].reservationAvailable").value(false));
    }

    @Test
    void 공개시각이_같으면_콘텐츠_식별자_내림차순으로_정렬한다() throws Exception {
        Region region = saveRegion("SORT", true);
        AppUser operator = saveOperator();
        Content first = saveContent(region, operator, "첫 번째", PUBLISH_AT, true);
        Content second = saveContent(region, operator, "두 번째", PUBLISH_AT, true);

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents[0].contentId").value(second.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[1].contentId").value(first.getContentId().toString()));
    }

    @Test
    void 공개_대상이_없으면_빈_목록을_반환하고_단기_URL을_발급하지_않는다() throws Exception {
        Region region = saveRegion("EMPTY", true);
        AppUser operator = saveOperator();
        saveContent(region, operator, "미공개 콘텐츠", PUBLISH_AT, true, ContentStatus.PENDING);

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents").isArray())
            .andExpect(jsonPath("$.data.contents").isEmpty());

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 미존재하거나_비공개인_지역은_NOT_FOUND로_노출하지_않는다() throws Exception {
        Region privateRegion = saveRegion("PRIVATE", false);

        mockMvc.perform(get("/api/v1/contents").param("regionId", "999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", privateRegion.getRegionId().toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 잘못된_쿼리_값을_계약된_오류로_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/contents"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", "01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", "+1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", "not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", "9223372036854775808"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/contents")
                .param("regionId", "1")
                .param("contentType", "UNSUPPORTED"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents")
                .param("regionId", "1")
                .param("reservationAvailable", "invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 대표_이미지가_현재_연결된_ACTIVE_이미지가_아니면_URL을_발급하지_않는다() throws Exception {
        Region region = saveRegion("IMAGE", true);
        Region otherRegion = saveRegion("OTHER-IMAGE", true);
        AppUser operator = saveOperator();
        saveContentWithoutImage(region, operator, "대표 이미지 없음", PUBLISH_AT);

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        Content content = saveContentWithoutImage(
            region,
            operator,
            "다른 지역 대표 이미지",
            PUBLISH_AT.plusSeconds(1)
        );
        content.assignRepresentativeImage(saveLinkedImageObject(otherRegion, operator), Instant.now());
        contentRepository.saveAndFlush(content);

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void 단기_URL은_조회마다_새로_발급한다() throws Exception {
        Region region = saveRegion("FRESH", true);
        AppUser operator = saveOperator();
        saveContent(region, operator, "새 URL", PUBLISH_AT, true);

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents[0].representativeImageUrl")
                .value("https://example.invalid/view/1"));
        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents[0].representativeImageUrl")
                .value("https://example.invalid/view/2"));

        assertThat(imageStorageGateway.requestedObjectKeys()).hasSize(2);
    }

    @Test
    void 대표_이미지가_ACTIVE_상태가_아니면_URL을_발급하지_않는다() throws Exception {
        Region region = saveRegion("INACTIVE-IMAGE", true);
        AppUser operator = saveOperator();
        Content content = saveContent(region, operator, "삭제 예정 이미지", PUBLISH_AT, true);
        ImageObject imageObject = content.getRepresentativeImageObject();
        imageObject.markDeletePending();
        imageObjectRepository.saveAndFlush(imageObject);

        mockMvc.perform(get("/api/v1/contents").param("regionId", region.getRegionId().toString()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(imageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void getPublicContent_returnsCurrentContentAndShortLivedImageUrlWithoutPersistingIt() throws Exception {
        Region region = saveRegion("DETAIL", true);
        AppUser operator = saveOperator();
        Content content = saveContent(region, operator, "current title", PUBLISH_AT, true);
        long contentCount = contentRepository.count();
        long imageCount = imageObjectRepository.count();
        long auditEventCount = auditEventRepository.count();

        mockMvc.perform(get("/api/v1/contents/" + content.getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.contentId").value(content.getContentId().toString()))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.title").value("current title"))
            .andExpect(jsonPath("$.data.description").exists())
            .andExpect(jsonPath("$.data.locationText").exists())
            .andExpect(jsonPath("$.data.operatingHoursText").exists())
            .andExpect(jsonPath("$.data.contactText").exists())
            .andExpect(jsonPath("$.data.precautions").exists())
            .andExpect(jsonPath("$.data.ageRequirement").exists())
            .andExpect(jsonPath("$.data.materials").exists())
            .andExpect(jsonPath("$.data.cancellationPolicyText").exists())
            .andExpect(jsonPath("$.data.representativeImageUrl")
                .value("https://example.invalid/view/1"))
            .andExpect(jsonPath("$.data.representativeImageUrlExpiresAt")
                .value("2026-08-01T00:05:01Z"))
            .andExpect(jsonPath("$.data.imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.objectKey").doesNotExist())
            .andExpect(jsonPath("$.data.originalFileName").doesNotExist())
            .andExpect(content().string(not(containsString("contents/"))));

        entityManager.clear();
        assertThat(imageStorageGateway.requestedObjectKeys())
            .containsExactly(content.getRepresentativeImageObject().getObjectKey());
        assertThat(contentRepository.count()).isEqualTo(contentCount);
        assertThat(imageObjectRepository.count()).isEqualTo(imageCount);
        assertThat(auditEventRepository.count()).isEqualTo(auditEventCount);
        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(foundContent -> {
                assertThat(foundContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
                assertThat(foundContent.getDeletedAt()).isNull();
                assertThat(foundContent.getTitle()).isEqualTo("current title");
            });
    }

    @Test
    void getPublicContent_rejectsInvalidContentIdAsInvalidInput() throws Exception {
        for (String invalidContentId : List.of("0", "01", "-1", "content", "9223372036854775808")) {
            mockMvc.perform(get("/api/v1/contents/" + invalidContentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        mockMvc.perform(get("/api/v1/contents/10000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void getPublicContent_hidesMissingNonPublishedAndDeletedContentWithNotFound() throws Exception {
        Region region = saveRegion("DETAIL-HIDDEN", true);
        AppUser operator = saveOperator();
        Content nonPublished = saveContent(
            region,
            operator,
            "non-published",
            PUBLISH_AT,
            true,
            ContentStatus.PENDING
        );
        Content deleted = saveContent(
            region,
            operator,
            "deleted",
            PUBLISH_AT,
            true,
            ContentStatus.PENDING
        );
        deleted.softDelete();
        contentRepository.saveAndFlush(deleted);

        for (String contentId : List.of(
            "999999",
            nonPublished.getContentId().toString(),
            deleted.getContentId().toString()
        )) {
            mockMvc.perform(get("/api/v1/contents/" + contentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    @Test
    void getPublicContent_usesCurrentContentInsteadOfPendingRevisionCandidates() throws Exception {
        Region region = saveRegion("DETAIL-REVISION", true);
        AppUser operator = saveOperator();
        Content content = saveContent(region, operator, "current title", PUBLISH_AT, true);
        ContentRevision revision = new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "candidate title",
            "candidate description",
            "candidate location",
            "candidate hours",
            "candidate contact",
            "candidate precautions",
            "candidate age",
            "candidate materials",
            "candidate cancellation policy",
            PUBLISH_AT.plusSeconds(3_600),
            Instant.now(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        revision.assignCandidateImage(saveLinkedImageObject(region, operator), Instant.now());
        contentRevisionRepository.saveAndFlush(revision);

        mockMvc.perform(get("/api/v1/contents/" + content.getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("current title"))
            .andExpect(jsonPath("$.data.representativeImageUrl")
                .value("https://example.invalid/view/1"))
            .andExpect(content().string(not(containsString("candidate title"))));

        assertThat(imageStorageGateway.requestedObjectKeys())
            .containsExactly(content.getRepresentativeImageObject().getObjectKey());
    }

    @Test
    void getPublicContent_rejectsMissingOrInactiveRepresentativeImageWithoutIssuingUrl() throws Exception {
        Region region = saveRegion("DETAIL-IMAGE", true);
        AppUser operator = saveOperator();
        Content missingImage = saveContentWithoutImage(region, operator, "missing image", PUBLISH_AT);

        mockMvc.perform(get("/api/v1/contents/" + missingImage.getContentId()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        Content inactiveImage = saveContent(region, operator, "inactive image", PUBLISH_AT, true);
        ImageObject imageObject = inactiveImage.getRepresentativeImageObject();
        imageObject.markDeletePending();
        imageObjectRepository.saveAndFlush(imageObject);

        mockMvc.perform(get("/api/v1/contents/" + inactiveImage.getContentId()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(imageStorageGateway.requestedObjectKeys()).isEmpty();
    }

    private Region saveRegion(String suffix, boolean isPublic) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "테스트 지역", isPublic));
    }

    private AppUser saveOperator() {
        return appUserRepository.saveAndFlush(new AppUser(
            "operator-" + appUserRepository.count() + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String title,
        Instant publishAt,
        boolean withRepresentativeImage
    ) {
        return saveContent(
            region,
            operator,
            title,
            publishAt,
            withRepresentativeImage,
            ContentStatus.PUBLISHED
        );
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String title,
        Instant publishAt,
        boolean withRepresentativeImage,
        ContentStatus status
    ) {
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            title,
            "설명",
            "김해시 가야의길 190",
            "운영 시간",
            "055-000-0000",
            "유의사항",
            "연령 조건",
            "준비물",
            "취소 규정",
            publishAt
        );
        if (withRepresentativeImage) {
            ImageObject representativeImageObject = saveLinkedImageObject(region, operator);
            content.assignRepresentativeImage(representativeImageObject, Instant.now());
        }
        return contentRepository.saveAndFlush(content);
    }

    private Content saveContentWithoutImage(
        Region region,
        AppUser operator,
        String title,
        Instant publishAt
    ) {
        return saveContent(region, operator, title, publishAt, false);
    }

    private ImageObject saveLinkedImageObject(Region region, AppUser operator) {
        ImageObject imageObject = imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            "contents/" + imageObjectRepository.count() + ".webp",
            operator,
            region,
            "image/webp",
            524_288L,
            CHECKSUM,
            Instant.now().plusSeconds(3_600)
        ));
        imageObject.markLinked(Instant.now());
        return imageObjectRepository.saveAndFlush(imageObject);
    }

    private ContentSession saveScheduledSession(
        Content content,
        Region region,
        AppUser reviewer,
        Instant startsAt,
        int remainingCapacity
    ) {
        int capacity = Math.max(remainingCapacity, 1);
        ContentSession contentSession = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(3_600),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(1_800),
            capacity
        );
        contentSession.approve(reviewer, Instant.now());
        ContentSession savedContentSession = contentSessionRepository.saveAndFlush(contentSession);
        if (remainingCapacity == 0) {
            contentSessionRepository.decreaseRemainingCapacityIfReservable(
                savedContentSession.getSessionId(),
                capacity,
                ContentStatus.PUBLISHED,
                ContentSessionStatus.SCHEDULED
            );
        }
        return savedContentSession;
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(
            contentRepository.count(),
            contentSessionRepository.count(),
            imageObjectRepository.count(),
            auditEventRepository.count()
        );
    }

    private void assertDatabaseUnchanged(
        DatabaseSnapshot before,
        Content content,
        ContentSession session
    ) {
        entityManager.clear();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(foundContent -> {
                assertThat(foundContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
                assertThat(foundContent.getDeletedAt()).isNull();
                assertThat(foundContent.getTitle()).isEqualTo("김해 가야문화 체험");
            });
        assertThat(contentSessionRepository.findById(session.getSessionId()))
            .get()
            .satisfies(foundSession -> {
                assertThat(foundSession.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
                assertThat(foundSession.getRemainingCapacity()).isEqualTo(10);
            });
    }

    record DatabaseSnapshot(
        long contents,
        long sessions,
        long images,
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
