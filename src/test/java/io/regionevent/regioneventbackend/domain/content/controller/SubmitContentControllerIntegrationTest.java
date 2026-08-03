package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
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
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class SubmitContentControllerIntegrationTest {

    private static final Instant PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant INITIAL_SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-08-02T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;
    private final List<Long> createdContentIds = new ArrayList<>();
    private final List<Long> createdImageObjectIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdRegionIds = new ArrayList<>();
    private Set<Long> auditEventIdsBeforeTest = new HashSet<>();

    @Autowired
    SubmitContentControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void rememberExistingAuditEvents() {
        auditEventIdsBeforeTest = new HashSet<>(auditEventRepository.findAll()
            .stream()
            .map(AuditEvent::getAuditEventId)
            .toList());
    }

    @AfterEach
    void tearDown() {
        List<Long> auditEventIds = currentAuditEvents()
            .stream()
            .map(AuditEvent::getAuditEventId)
            .toList();
        auditEventActorLinkRepository.deleteAllByIdInBatch(auditEventIds);
        auditEventRepository.deleteAllByIdInBatch(auditEventIds);
        for (Long contentId : createdContentIds) {
            contentSessionRepository.deleteAllInBatch(
                contentSessionRepository.findByContentContentIdOrderByStartsAtAscSessionIdAsc(contentId)
            );
            contentLogRepository.deleteAllInBatch(
                contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(contentId)
            );
        }
        contentRepository.deleteAllByIdInBatch(createdContentIds);
        imageObjectRepository.deleteAllByIdInBatch(createdImageObjectIds);
        for (Long userId : createdUserIds) {
            userRoleAssignmentRepository.deleteAllInBatch(
                userRoleAssignmentRepository.findAllByIdUserId(userId)
            );
        }
        appUserRepository.deleteAllByIdInBatch(createdUserIds);
        regionRepository.deleteAllByIdInBatch(createdRegionIds);
    }

    @Test
    void submitContent_whenRejectedContentIsValid_changesStatusAndRecordsLogAndAudit() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, true, true);

        performSubmit(fixture.operator(), fixture.content().getContentId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 승인 재요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.submittedAt").isString());

        assertSubmitted(fixture);
    }

    @Test
    void submitContent_whenUserIsNotOperator_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, true, true);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        performSubmit(visitor, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.REJECTED, 2);
        assertFailureAudits(ErrorCode.FORBIDDEN);
    }

    @Test
    void submitContent_whenOperatorDoesNotOwnContent_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, true, true);
        AppUser otherOperator = saveUser("other-operator", AppUserStatus.ACTIVE);
        assignOperator(otherOperator, fixture.region());

        performSubmit(otherOperator, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.REJECTED, 2);
        assertFailureAudits(ErrorCode.FORBIDDEN);
    }

    @Test
    void submitContent_whenOperatorRegionDiffers_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, true, true);
        AppUser otherOperator = saveUser("other-region-operator", AppUserStatus.ACTIVE);
        assignOperator(otherOperator, saveRegion("OTHER"));

        performSubmit(otherOperator, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.REJECTED, 2);
        assertFailureAudits(ErrorCode.FORBIDDEN);
    }

    @Test
    void submitContent_whenContentStateIsNotRejected_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, true, true);

        performSubmit(fixture.operator(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
        assertFailureAudits(ErrorCode.CONTENT_STATE_CONFLICT);
    }

    @Test
    void submitContent_whenRetriedAfterSuccess_returnsConflictWithoutNewRecords() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, true, true);

        performSubmit(fixture.operator(), fixture.content().getContentId().toString())
            .andExpect(status().isOk());
        performSubmit(fixture.operator(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).hasSize(3);
        assertThat(currentAuditEvents())
            .extracting(AuditEvent::getResult)
            .containsExactlyInAnyOrder(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
    }

    @Test
    void submitContent_whenContentIsMissingOrSoftDeleted_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, true, true);
        fixture.content().softDelete();
        contentRepository.saveAndFlush(fixture.content());

        performSubmit(fixture.operator(), fixture.content().getContentId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        performSubmit(fixture.operator(), "999999999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertFailureAudits(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    @Test
    void submitContent_whenSubmitRequirementsAreInvalid_returnsInvalidInputWithoutChanges() throws Exception {
        Fixture withoutImage = createFixture(ContentStatus.REJECTED, false, true);
        Fixture withoutPendingSession = createFixture(ContentStatus.REJECTED, true, false);

        performSubmit(withoutImage.operator(), withoutImage.content().getContentId().toString())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performSubmit(withoutPendingSession.operator(), withoutPendingSession.content().getContentId().toString())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertUnchanged(withoutImage.content().getContentId(), ContentStatus.REJECTED, 2);
        assertUnchanged(withoutPendingSession.content().getContentId(), ContentStatus.REJECTED, 2);
        assertFailureAudits(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT);
    }

    @Test
    void submitContent_whenContentIdIsInvalid_returnsInvalidInput() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, true, true);

        for (String invalidContentId : new String[]{"0", "-1", "01", "not-a-number", "9223372036854775808"}) {
            performSubmit(fixture.operator(), invalidContentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        assertUnchanged(fixture.content().getContentId(), ContentStatus.REJECTED, 2);
        assertThat(currentAuditEvents()).isEmpty();
    }

    @Test
    void submitContent_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/operator/contents/1/submit"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(currentAuditEvents()).isEmpty();
    }

    private void assertSubmitted(Fixture fixture) {
        entityManager.clear();
        Content submittedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            submittedContent.getContentId()
        );

        assertThat(submittedContent.getStatus()).isEqualTo(ContentStatus.PENDING);
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING, ContentLogStatus.REJECTED, ContentLogStatus.PENDING);
        ContentLog submittedLog = logs.get(2);
        assertThat(submittedLog.getActor().getUserId()).isEqualTo(fixture.operator().getUserId());
        assertThat(submittedLog.getReason()).isNull();

        assertThat(currentAuditEvents()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(submittedContent.getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("REJECTED");
            assertThat(auditEvent.getNextState()).isEqualTo("PENDING");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getOccurredAt()).isEqualTo(submittedLog.getDate());
            assertThat(auditEvent.getActorRole()).isEqualTo("OPERATOR");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.operator().getUserId())
                );
        });
    }

    private void assertUnchanged(Long contentId, ContentStatus expectedStatus, int expectedLogCount) {
        entityManager.clear();
        assertThat(contentRepository.findById(contentId)).hasValueSatisfying(content ->
            assertThat(content.getStatus()).isEqualTo(expectedStatus)
        );
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(contentId))
            .hasSize(expectedLogCount);
    }

    private void assertFailureAudits(ErrorCode... errorCodes) {
        List<String> expectedReasonCodes = Arrays.stream(errorCodes)
            .map(ErrorCode::code)
            .toList();
        List<AuditEvent> auditEvents = currentAuditEvents();

        assertThat(auditEvents)
            .hasSize(errorCodes.length)
            .allSatisfy(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(auditEvent.getNextState()).isNull();
                assertThat(auditEvent.getActorRole()).isNull();
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
            });
        assertThat(auditEvents)
            .extracting(AuditEvent::getReasonCode)
            .containsExactlyInAnyOrderElementsOf(expectedReasonCodes);
    }

    private ResultActions performSubmit(AppUser user, String contentId) throws Exception {
        return mockMvc.perform(post("/api/v1/operator/contents/{contentId}/submit", contentId)
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private List<AuditEvent> currentAuditEvents() {
        return auditEventRepository.findAll()
            .stream()
            .filter(auditEvent -> !auditEventIdsBeforeTest.contains(auditEvent.getAuditEventId()))
            .toList();
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        boolean assignImage,
        boolean createPendingSession
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        assignOperator(operator, region);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "Gimhae culture experience",
            "A local culture experience in Gimhae.",
            "Gimhae civic hall",
            "Every day 10:00-18:00",
            "055-123-4567",
            "Please follow safety guidance.",
            "Age 7 and up",
            "Comfortable clothes",
            "Cancellation is available until one day before.",
            PUBLISH_AT
        ));
        createdContentIds.add(content.getContentId());
        if (assignImage) {
            content.assignRepresentativeImage(saveLinkedImage(region, operator, suffix), INITIAL_SUBMITTED_AT);
            content = contentRepository.saveAndFlush(content);
        }
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PENDING,
            null,
            INITIAL_SUBMITTED_AT
        ));
        if (contentStatus == ContentStatus.REJECTED) {
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                operator,
                ContentLogStatus.REJECTED,
                "Need more details.",
                REJECTED_AT
            ));
        }
        if (createPendingSession) {
            savePendingSession(content, region);
        }
        return new Fixture(region, operator, content);
    }

    private Region saveRegion(String codePrefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region(codePrefix + suffix, codePrefix + " region", true));
        createdRegionIds.add(region.getRegionId());
        return region;
    }

    private AppUser saveUser(String identifierPrefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            "User",
            "010-1234-5678",
            status
        ));
        createdUserIds.add(user.getUserId());
        return user;
    }

    private void assignOperator(AppUser user, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.OPERATOR, region));
    }

    private ImageObject saveLinkedImage(Region region, AppUser operator, String suffix) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "contents/" + suffix + "/representative.jpg",
            operator,
            region,
            "image/jpeg",
            1024L,
            "checksum-" + suffix,
            INITIAL_SUBMITTED_AT.plusSeconds(3600)
        );
        ImageObject savedImageObject = imageObjectRepository.saveAndFlush(imageObject);
        savedImageObject.markLinked(INITIAL_SUBMITTED_AT);
        ImageObject linkedImageObject = imageObjectRepository.saveAndFlush(savedImageObject);
        createdImageObjectIds.add(linkedImageObject.getImageObjectId());
        return linkedImageObject;
    }

    private void savePendingSession(Content content, Region region) {
        Instant startsAt = Instant.parse("2026-08-10T01:00:00Z");
        contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            20
        ));
    }

    private record Fixture(
        Region region,
        AppUser operator,
        Content content
    ) {
    }
}
