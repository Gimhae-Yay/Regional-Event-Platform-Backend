package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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

    private Content saveContent(String operatorLoginIdentifier) {
        Region region = regionRepository.saveAndFlush(new Region(
            "REGION-" + operatorLoginIdentifier,
            "테스트 지역",
            true
        ));
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
            Instant.parse("2026-08-02T00:00:00Z")
        ));
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
