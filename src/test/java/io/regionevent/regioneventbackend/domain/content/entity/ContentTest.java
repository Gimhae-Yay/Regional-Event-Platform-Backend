package io.regionevent.regioneventbackend.domain.content.entity;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class ContentTest {

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new ContentTest().approve_whenPendingAndActive_changesStatusToApproved(),
            () -> new ContentTest().approve_whenStatusIsNotPending_throwsExceptionWithoutChanges(),
            () -> new ContentTest().approve_whenSoftDeleted_throwsExceptionWithoutChanges(),
            () -> new ContentTest().reject_whenPendingAndActive_changesStatusToRejected(),
            () -> new ContentTest().reject_whenStatusIsNotPending_throwsExceptionWithoutChanges(),
            () -> new ContentTest().reject_whenSoftDeleted_throwsExceptionWithoutChanges(),
            () -> new ContentTest().submitForReview_whenRejectedAndActive_changesStatusToPending(),
            () -> new ContentTest().submitForReview_whenStatusIsNotRejected_throwsExceptionWithoutChanges(),
            () -> new ContentTest().publish_whenApprovedAndActive_changesStatusToPublished(),
            () -> new ContentTest().publish_whenStatusIsNotApproved_throwsExceptionWithoutChanges(),
            () -> new ContentTest().publish_whenSoftDeleted_throwsExceptionWithoutChanges()
        );
    }

    void approve_whenPendingAndActive_changesStatusToApproved() {
        Content content = createContent(ContentStatus.PENDING);

        content.approve();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
    }

    void approve_whenStatusIsNotPending_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.REJECTED);

        assertThatThrownBy(content::approve)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.REJECTED);
    }

    void approve_whenSoftDeleted_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.PENDING);
        content.softDelete();

        assertThatThrownBy(content::approve)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
    }

    void reject_whenPendingAndActive_changesStatusToRejected() {
        Content content = createContent(ContentStatus.PENDING);

        content.reject();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.REJECTED);
    }

    void reject_whenStatusIsNotPending_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.APPROVED);

        assertThatThrownBy(content::reject)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
    }

    void reject_whenSoftDeleted_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.PENDING);
        content.softDelete();

        assertThatThrownBy(content::reject)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
    }

    void submitForReview_whenRejectedAndActive_changesStatusToPending() {
        Content content = createContent(ContentStatus.REJECTED);

        content.submitForReview();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
    }

    void submitForReview_whenStatusIsNotRejected_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.PENDING);

        assertThatThrownBy(content::submitForReview)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
    }

    void publish_whenApprovedAndActive_changesStatusToPublished() {
        Content content = createContent(ContentStatus.APPROVED);

        content.publish();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
    }

    void publish_whenStatusIsNotApproved_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.PENDING);

        assertThatThrownBy(content::publish)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
    }

    void publish_whenSoftDeleted_throwsExceptionWithoutChanges() {
        Content content = createContent(ContentStatus.APPROVED);
        content.softDelete();

        assertThatThrownBy(content::publish)
            .isInstanceOf(IllegalStateException.class);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
    }

    private Content createContent(ContentStatus status) {
        Region region = new Region("GIMHAE", "김해시", true);
        AppUser operator = new AppUser(
            "operator@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
