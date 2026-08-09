package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class OriginalContentReviewTargetServiceTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final ContentLogRepository contentLogRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;

    @Autowired
    OriginalContentReviewTargetServiceTest(
        ContentLogRepository contentLogRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository
    ) {
        this.contentLogRepository = contentLogRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.originalContentReviewTargetService = new OriginalContentReviewTargetService(
            contentLogRepository,
            new OriginalContentReviewTargetPolicy()
        );
    }

    @Test
    void 최초_심사는_원본_심사_대상으로_판정한다() {
        Content content = saveContent("initial-operator@example.com");
        ContentLog pendingLog = saveLog(content, ContentLogStatus.PENDING, RECORDED_AT);

        OriginalContentReviewTarget target = originalContentReviewTargetService
            .findByContentId(content.getContentId())
            .orElseThrow();

        assertThat(target.content().getContentId()).isEqualTo(content.getContentId());
        assertThat(target.pendingLog().getId()).isEqualTo(pendingLog.getId());
        assertThat(target.previousLog()).isNull();
        assertThat(target.type()).isEqualTo(OriginalContentReviewTargetType.INITIAL_SUBMISSION);
        assertThat(target.isOriginalReviewTarget()).isTrue();
    }

    @Test
    void 반려_후_재제출은_원본_심사_대상으로_판정한다() {
        Content content = saveContent("resubmitted-operator@example.com");
        saveLog(content, ContentLogStatus.PENDING, RECORDED_AT.minusSeconds(2));
        ContentLog rejectedLog = saveLog(content, ContentLogStatus.REJECTED, RECORDED_AT.minusSeconds(1));
        ContentLog pendingLog = saveLog(content, ContentLogStatus.PENDING, RECORDED_AT);

        OriginalContentReviewTarget target = originalContentReviewTargetService
            .findByContentId(content.getContentId())
            .orElseThrow();

        assertThat(target.pendingLog().getId()).isEqualTo(pendingLog.getId());
        assertThat(target.previousLog().getId()).isEqualTo(rejectedLog.getId());
        assertThat(target.type()).isEqualTo(OriginalContentReviewTargetType.RESUBMISSION_AFTER_REJECTION);
        assertThat(target.isOriginalReviewTarget()).isTrue();
    }

    @Test
    void 직전_상태가_APPROVED이면_공개_전_수정_심사로_판정한다() {
        Content content = saveContent("revision-operator@example.com");
        saveLog(content, ContentLogStatus.PENDING, RECORDED_AT.minusSeconds(2));
        ContentLog approvedLog = saveLog(content, ContentLogStatus.APPROVED, RECORDED_AT.minusSeconds(1));
        ContentLog pendingLog = saveLog(content, ContentLogStatus.PENDING, RECORDED_AT);

        OriginalContentReviewTarget target = originalContentReviewTargetService
            .findByContentId(content.getContentId())
            .orElseThrow();

        assertThat(target.pendingLog().getId()).isEqualTo(pendingLog.getId());
        assertThat(target.previousLog().getId()).isEqualTo(approvedLog.getId());
        assertThat(target.type()).isEqualTo(OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION);
        assertThat(target.isOriginalReviewTarget()).isFalse();
    }

    @Test
    void 로그_시각이_같으면_ID가_큰_로그를_최신으로_판정한다() {
        Content content = saveContent("same-date-operator@example.com");
        ContentLog rejectedLog = saveLog(content, ContentLogStatus.REJECTED, RECORDED_AT);
        ContentLog pendingLog = saveLog(content, ContentLogStatus.PENDING, RECORDED_AT);

        OriginalContentReviewTarget target = originalContentReviewTargetService
            .findByContentId(content.getContentId())
            .orElseThrow();

        assertThat(target.pendingLog().getId()).isEqualTo(pendingLog.getId());
        assertThat(target.previousLog().getId()).isEqualTo(rejectedLog.getId());
        assertThat(target.type()).isEqualTo(OriginalContentReviewTargetType.RESUBMISSION_AFTER_REJECTION);
    }

    @Test
    void 소프트_삭제된_콘텐츠는_조회하지_않는다() {
        Content content = saveContent("deleted-operator@example.com");
        saveLog(content, ContentLogStatus.PENDING, RECORDED_AT);
        content.softDelete();
        contentRepository.saveAndFlush(content);

        assertThat(originalContentReviewTargetService.findByContentId(content.getContentId())).isEmpty();
    }

    @Test
    void 여러_콘텐츠의_최신_두_로그를_일괄_조회해_각각_판정한다() {
        Content initialSubmission = saveContent("initial-batch-operator@example.com");
        Content resubmission = saveContent("resubmission-batch-operator@example.com");
        saveLog(initialSubmission, ContentLogStatus.PENDING, RECORDED_AT);
        saveLog(resubmission, ContentLogStatus.REJECTED, RECORDED_AT.minusSeconds(1));
        ContentLog resubmittedPendingLog = saveLog(resubmission, ContentLogStatus.PENDING, RECORDED_AT);

        List<OriginalContentReviewTarget> targets = originalContentReviewTargetService.findByContents(List.of(
            resubmission,
            initialSubmission
        ));

        assertThat(targets).extracting(target -> target.content().getContentId())
            .containsExactly(resubmission.getContentId(), initialSubmission.getContentId());
        assertThat(targets.getFirst().type())
            .isEqualTo(OriginalContentReviewTargetType.RESUBMISSION_AFTER_REJECTION);
        assertThat(targets.getFirst().pendingLog().getId()).isEqualTo(resubmittedPendingLog.getId());
        assertThat(targets.get(1).type())
            .isEqualTo(OriginalContentReviewTargetType.INITIAL_SUBMISSION);
    }

    @Test
    void 담당지역의_미삭제_PENDING_콘텐츠만_목록_후보로_조회한다() {
        Region assignedRegion = regionRepository.saveAndFlush(new Region("ASSIGNED", "담당 지역", true));
        Content pendingContent = saveContent(
            assignedRegion,
            "pending-list-operator@example.com",
            ContentStatus.PENDING
        );
        Content softDeletedContent = saveContent(
            assignedRegion,
            "deleted-list-operator@example.com",
            ContentStatus.PENDING
        );
        softDeletedContent.softDelete();
        contentRepository.saveAndFlush(softDeletedContent);
        saveContent(assignedRegion, "approved-list-operator@example.com", ContentStatus.APPROVED);
        saveContent("other-region-list-operator@example.com");

        List<Content> contents = contentRepository
            .findByRegionRegionIdAndStatusAndDeletedAtIsNullOrderByContentIdAsc(
                assignedRegion.getRegionId(),
                ContentStatus.PENDING
            );

        assertThat(contents).extracting(Content::getContentId)
            .containsExactly(pendingContent.getContentId());
    }

    private Content saveContent(String operatorLoginIdentifier) {
        Region region = regionRepository.saveAndFlush(new Region(
            toRegionCode(operatorLoginIdentifier),
            "테스트 지역",
            true
        ));
        return saveContent(region, operatorLoginIdentifier, ContentStatus.PENDING);
    }

    private Content saveContent(
        Region region,
        String operatorLoginIdentifier,
        ContentStatus contentStatus
    ) {
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            operatorLoginIdentifier,
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));

        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-02T00:00:00Z")
        ));
    }

    private String toRegionCode(String source) {
        return "REGION-" + Integer.toUnsignedString(source.hashCode(), 36).toUpperCase(Locale.ROOT);
    }

    private ContentLog saveLog(
        Content content,
        ContentLogStatus status,
        Instant recordedAt
    ) {
        String reason = status == ContentLogStatus.REJECTED ? "필수 정보가 누락되었습니다." : null;
        return contentLogRepository.saveAndFlush(new ContentLog(content, null, status, reason, recordedAt));
    }
}
