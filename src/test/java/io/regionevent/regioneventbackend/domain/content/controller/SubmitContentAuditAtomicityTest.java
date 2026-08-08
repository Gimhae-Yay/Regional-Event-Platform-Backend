package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.service.SubmitContentUseCase;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import(AtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class SubmitContentAuditAtomicityTest {

    private static final Instant INITIAL_SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-08-02T00:00:00Z");

    private final SubmitContentUseCase submitContentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final EntityManager entityManager;
    private final List<Long> createdContentIds = new ArrayList<>();
    private final List<Long> createdImageObjectIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdRegionIds = new ArrayList<>();
    private Set<Long> auditEventIdsBeforeTest = new HashSet<>();

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    SubmitContentAuditAtomicityTest(
        SubmitContentUseCase submitContentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        EntityManager entityManager
    ) {
        this.submitContentUseCase = submitContentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
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
                userRoleAssignmentRepository.findAllByAppUserUserIdAndStatus(userId, UserRoleAssignmentStatus.ACTIVE)
            );
        }
        appUserRepository.deleteAllByIdInBatch(createdUserIds);
        regionRepository.deleteAllByIdInBatch(createdRegionIds);
    }

    @Test
    void submitContent_whenAuditRecordingFails_rollsBackStatusAndLog() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> submitContentUseCase.submit(
            fixture.operator().getUserId(),
            fixture.content().getContentId(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        Content unchangedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.REJECTED);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            unchangedContent.getContentId()
        )).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING, ContentLogStatus.REJECTED);
        assertThat(currentAuditEvents()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        });
    }

    private List<AuditEvent> currentAuditEvents() {
        return auditEventRepository.findAll()
            .stream()
            .filter(auditEvent -> !auditEventIdsBeforeTest.contains(auditEvent.getAuditEventId()))
            .toList();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "region", true));
        createdRegionIds.add(region.getRegionId());
        AppUser operator = saveUser("operator-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.REJECTED,
            "Gimhae culture experience",
            "A local culture experience in Gimhae.",
            "Gimhae civic hall",
            "Every day 10:00-18:00",
            "055-123-4567",
            "Please follow safety guidance.",
            "Age 7 and up",
            "Comfortable clothes",
            "Cancellation is available until one day before.",
            Instant.parse("2026-08-05T00:00:00Z")
        ));
        createdContentIds.add(content.getContentId());
        content.assignRepresentativeImage(saveLinkedImage(region, operator, suffix), INITIAL_SUBMITTED_AT);
        content = contentRepository.saveAndFlush(content);
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PENDING,
            null,
            INITIAL_SUBMITTED_AT
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.REJECTED,
            "Need more details.",
            REJECTED_AT
        ));
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
        return new Fixture(operator, content);
    }

    private AppUser saveUser(String identifierPrefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            "User",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        createdUserIds.add(user.getUserId());
        return user;
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

    private record Fixture(
        AppUser operator,
        Content content
    ) {
    }
}
