package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.service.DeleteContentUseCase;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectCleanupService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import(AtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class ContentDeletionAuditAtomicityTest {

    @Autowired
    private DeleteContentUseCase deleteContentUseCase;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentLogRepository contentLogRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditEventActorLinkRepository auditEventActorLinkRepository;

    @Autowired
    private ImageObjectCleanupService imageObjectCleanupService;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Test
    void deleteContent_whenSuccessAuditRecordingFails_rollsBackDomainChangesAndRecordsFailureAudit() {

        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> deleteContentUseCase.delete(
            fixture.adminId(),
            fixture.contentId(),
            "행사 준비가 취소되었습니다.",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(contentRepository.findById(fixture.contentId()))
            .get()
            .satisfies(content -> {
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
                assertThat(content.getDeletedAt()).isNull();
                assertThat(content.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(fixture.imageObjectId());
            });
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING);
        assertThat(imageObjectRepository.findById(fixture.imageObjectId()))
            .get()
            .extracting(ImageObject::getLifecycleStatus)
            .isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("INTERNAL_SERVER_ERROR");
            assertThat(auditEvent.getActorRole()).isNull();
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
        });
        verifyNoInteractions(imageObjectCleanupService);
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("AUDIT-" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        AppUser operator = saveUser("operator-" + suffix);
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/audit-" + suffix + ".webp",
            operator,
            region,
            "image/webp",
            1024L,
            "checksum",
            Instant.now().plusSeconds(3600)
        );
        imageObject.markLinked(Instant.now());
        imageObjectRepository.saveAndFlush(imageObject);
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-05T00:00:00Z")
        );
        content.assignRepresentativeImage(imageObject, Instant.now());
        contentRepository.saveAndFlush(content);
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PENDING,
            null,
            Instant.now()
        ));
        return new Fixture(admin.getUserId(), content.getContentId(), imageObject.getImageObjectId());
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

    private record Fixture(
        Long adminId,
        Long contentId,
        Long imageObjectId
    ) {
    }
}
