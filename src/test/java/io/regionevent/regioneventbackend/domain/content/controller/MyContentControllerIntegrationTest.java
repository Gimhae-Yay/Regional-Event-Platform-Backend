package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
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
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MyContentControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class MyContentControllerIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void getMyContents_whenOperatorHasContents_returnsOwnedRegionalNonDeletedContents() throws Exception {
        Region region = saveRegion("LIST-NORMAL");
        Region otherRegion = saveRegion("LIST-OTHER-REGION");
        AppUser operator = saveUser("list-operator@example.com");
        AppUser otherOperator = saveUser("list-other-operator@example.com");
        assignRole(operator, UserRole.OPERATOR, region);
        assignRole(otherOperator, UserRole.OPERATOR, region);
        Content myContent = saveContent(operator, region, ContentStatus.APPROVED, "내 콘텐츠");
        Content otherOwner = saveContent(otherOperator, region, ContentStatus.APPROVED, "다른 운영자 콘텐츠");
        Content otherRegional = saveContent(operator, otherRegion, ContentStatus.APPROVED, "다른 지역 콘텐츠");
        Content deleted = saveContent(operator, region, ContentStatus.PENDING, "삭제 콘텐츠");
        deleted.softDelete(Instant.parse("2026-08-05T00:00:00Z"));
        contentRepository.saveAndFlush(deleted);

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 콘텐츠 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contents.length()").value(1))
            .andExpect(jsonPath("$.data.contents[0].contentId").value(myContent.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[0].contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.contents[0].title").value("내 콘텐츠"))
            .andExpect(jsonPath("$.data.contents[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.data.contents[0].createdAt").isString());

        assertThat(contentRepository.findById(otherOwner.getContentId())).isPresent();
        assertThat(contentRepository.findById(otherRegional.getContentId())).isPresent();
    }

    @Test
    void getMyContents_whenOperatorHasNoContents_returnsEmptyContentsArray() throws Exception {
        Region region = saveRegion("LIST-EMPTY");
        AppUser operator = saveUser("empty-operator@example.com");
        assignRole(operator, UserRole.OPERATOR, region);

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents").isArray())
            .andExpect(jsonPath("$.data.contents.length()").value(0));
    }

    @Test
    void getMyContents_whenContentsExist_returnsFixedCreatedAtAndContentIdDescendingOrder() throws Exception {
        Region region = saveRegion("LIST-SORT");
        AppUser operator = saveUser("sort-operator@example.com");
        assignRole(operator, UserRole.OPERATOR, region);
        Content older = saveContent(operator, region, ContentStatus.PENDING, "이전 콘텐츠");
        Content firstTie = saveContent(operator, region, ContentStatus.APPROVED, "동률 첫 번째 콘텐츠");
        Content secondTie = saveContent(operator, region, ContentStatus.REJECTED, "동률 두 번째 콘텐츠");
        Content later = saveContent(operator, region, ContentStatus.PUBLISHED, "최근 콘텐츠");
        setCreatedAt(older, Instant.parse("2026-08-01T00:00:00Z"));
        setCreatedAt(firstTie, Instant.parse("2026-08-03T00:00:00Z"));
        setCreatedAt(secondTie, Instant.parse("2026-08-03T00:00:00Z"));
        setCreatedAt(later, Instant.parse("2026-08-04T00:00:00Z"));

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents.length()").value(4))
            .andExpect(jsonPath("$.data.contents[0].contentId").value(later.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[1].contentId").value(secondTie.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[2].contentId").value(firstTie.getContentId().toString()))
            .andExpect(jsonPath("$.data.contents[3].contentId").value(older.getContentId().toString()));
    }

    @Test
    void getMyContents_whenContentIsSoftDeleted_excludesSoftDeletedContent() throws Exception {
        Region region = saveRegion("LIST-DELETED");
        AppUser operator = saveUser("deleted-operator@example.com");
        assignRole(operator, UserRole.OPERATOR, region);
        Content deleted = saveContent(operator, region, ContentStatus.PENDING, "삭제 콘텐츠");
        deleted.softDelete(Instant.parse("2026-08-05T00:00:00Z"));
        contentRepository.saveAndFlush(deleted);

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents.length()").value(0));
    }

    @Test
    void getMyContents_whenAuthorizationHeaderIsMissing_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/operator/contents")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.message").value("인증 정보가 없거나 유효하지 않습니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getMyContents_whenUserIsNotOperator_returnsForbidden() throws Exception {
        AppUser visitor = saveUser("visitor@example.com");
        assignRole(visitor, UserRole.VISITOR, null);

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMyContents_whenRequested_doesNotChangePersistentContentState() throws Exception {
        Region region = saveRegion("LIST-IMMUTABLE");
        AppUser operator = saveUser("immutable-operator@example.com");
        assignRole(operator, UserRole.OPERATOR, region);
        Content content = saveContent(operator, region, ContentStatus.REJECTED, "불변 콘텐츠");
        Long contentId = content.getContentId();
        ContentSnapshot before = snapshot(contentId);

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        assertThat(snapshot(contentId)).isEqualTo(before);
    }

    @Test
    void getMyContents_whenRequested_returnsOnlyContractedFieldsWithoutPagingCursorCountOrFilters()
        throws Exception {

        Region region = saveRegion("LIST-SCHEMA");
        AppUser operator = saveUser("schema-operator@example.com");
        assignRole(operator, UserRole.OPERATOR, region);
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, "스키마 콘텐츠");
        setCreatedAt(content, Instant.parse("2026-08-04T00:00:00Z"));

        mockMvc.perform(get("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents[0].contentId").isString())
            .andExpect(jsonPath("$.data.contents[0].contentId").value(matchesPattern("[1-9]\\d*")))
            .andExpect(jsonPath("$.data.contents[0].contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.contents[0].title").value("스키마 콘텐츠"))
            .andExpect(jsonPath("$.data.contents[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.contents[0].createdAt").value("2026-08-04T00:00:00Z"))
            .andExpect(jsonPath("$.data.page").doesNotExist())
            .andExpect(jsonPath("$.data.cursor").doesNotExist())
            .andExpect(jsonPath("$.data.totalCount").doesNotExist())
            .andExpect(jsonPath("$.data.sort").doesNotExist())
            .andExpect(jsonPath("$.data.status").doesNotExist())
            .andExpect(jsonPath("$.data.regionId").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].description").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].locationText").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].representativeImageUrl").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].operatorId").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].updatedAt").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].deletedAt").doesNotExist());
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode, true));
    }

    private void assignRole(AppUser user, UserRole role, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, role, region));
    }

    private Content saveContent(
        AppUser operator,
        Region region,
        ContentStatus status,
        String title
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            title,
            "설명입니다.",
            "장소",
            "운영 시간",
            "055-000-0000",
            "유의사항",
            "연령 조건",
            "준비물",
            "취소 규정",
            Instant.parse("2026-08-15T00:00:00Z")
        ));
    }

    private void setCreatedAt(Content content, Instant createdAt) {
        jdbcTemplate.update(
            "UPDATE content SET created_at = ? WHERE content_id = ?",
            Timestamp.from(createdAt),
            content.getContentId()
        );
    }

    private ContentSnapshot snapshot(Long contentId) {
        return contentRepository.findById(contentId)
            .map(content -> new ContentSnapshot(
                content.getStatus(),
                content.getVersionNo(),
                content.getUpdatedAt(),
                content.getDeletedAt()
            ))
            .orElseThrow();
    }

    private record ContentSnapshot(
        ContentStatus status,
        int versionNo,
        Instant updatedAt,
        Instant deletedAt
    ) {
    }

    @TestConfiguration
    static class TestImageStorageConfig {

        @Bean
        @Primary
        ImageStorageGateway imageStorageGateway() {
            return new NoOpImageStorageGateway();
        }
    }

    static class NoOpImageStorageGateway implements ImageStorageGateway {

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
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
