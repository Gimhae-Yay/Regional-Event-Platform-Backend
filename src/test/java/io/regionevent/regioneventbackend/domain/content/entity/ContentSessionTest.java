package io.regionevent.regioneventbackend.domain.content.entity;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class ContentSessionTest {

    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TERMINAL_AT = Instant.parse("2026-08-03T00:00:00Z");

    private final Region region = new Region("GIMHAE", "김해시", true);
    private final AppUser operator = createUser("operator@example.com");
    private final AppUser reviewer = createUser("reviewer@example.com");
    private final Content content = createContent();

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new ContentSessionTest().constructor_createsPendingSession(),
            () -> new ContentSessionTest().approve_whenPending_changesStatusToScheduled(),
            () -> new ContentSessionTest().reject_whenPending_recordsRequiredReviewInformation(),
            () -> new ContentSessionTest().reject_whenReasonIsBlank_throwsExceptionAndKeepsPendingStatus(),
            () -> new ContentSessionTest().complete_whenScheduled_changesStatusToCompleted(),
            () -> new ContentSessionTest().cancel_whenScheduled_recordsRequiredCancellationInformation(),
            () -> new ContentSessionTest().releaseCapacity_whenQuantityIsValid_increasesRemainingCapacity(),
            () -> new ContentSessionTest().transitions_whenCurrentStatusIsNotAllowed_throwException()
        );
    }

    void constructor_createsPendingSession() {
        ContentSession contentSession = createContentSession();

        assertThat(contentSession.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
        assertThat(contentSession.getRemainingCapacity()).isEqualTo(contentSession.getCapacity());
        assertThat(contentSession.getReviewedAt()).isNull();
        assertThat(contentSession.getReviewedByUser()).isNull();
        assertThat(contentSession.getRejectReason()).isNull();
    }

    void approve_whenPending_changesStatusToScheduled() {
        ContentSession contentSession = createContentSession();

        contentSession.approve(reviewer, REVIEWED_AT);

        assertThat(contentSession.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
        assertThat(contentSession.getReviewedByUser()).isSameAs(reviewer);
        assertThat(contentSession.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(contentSession.getRejectReason()).isNull();
    }

    void reject_whenPending_recordsRequiredReviewInformation() {
        ContentSession contentSession = createContentSession();

        contentSession.reject(reviewer, REVIEWED_AT, "운영 시간이 기준에 맞지 않습니다.");

        assertThat(contentSession.getStatus()).isEqualTo(ContentSessionStatus.REJECTED);
        assertThat(contentSession.getReviewedByUser()).isSameAs(reviewer);
        assertThat(contentSession.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(contentSession.getRejectReason()).isEqualTo("운영 시간이 기준에 맞지 않습니다.");
    }

    void reject_whenReasonIsBlank_throwsExceptionAndKeepsPendingStatus() {
        ContentSession contentSession = createContentSession();

        assertThatThrownBy(
            () -> contentSession.reject(reviewer, REVIEWED_AT, " ")
        ).isInstanceOf(IllegalArgumentException.class);
        assertThat(contentSession.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
    }

    void complete_whenScheduled_changesStatusToCompleted() {
        ContentSession contentSession = createContentSession();
        contentSession.approve(reviewer, REVIEWED_AT);

        contentSession.complete(TERMINAL_AT);

        assertThat(contentSession.getStatus()).isEqualTo(ContentSessionStatus.COMPLETED);
        assertThat(contentSession.getCompletedAt()).isEqualTo(TERMINAL_AT);
    }

    void cancel_whenScheduled_recordsRequiredCancellationInformation() {
        ContentSession contentSession = createContentSession();
        contentSession.approve(reviewer, REVIEWED_AT);

        contentSession.cancel(reviewer, TERMINAL_AT, "기상 악화");

        assertThat(contentSession.getStatus()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertThat(contentSession.getCancelledByUser()).isSameAs(reviewer);
        assertThat(contentSession.getCancelledAt()).isEqualTo(TERMINAL_AT);
        assertThat(contentSession.getCancellationReason()).isEqualTo("기상 악화");
    }

    void releaseCapacity_whenQuantityIsValid_increasesRemainingCapacity() {
        ContentSession contentSession = createContentSession();

        contentSession.releaseCapacity(0);

        assertThat(contentSession.getRemainingCapacity()).isEqualTo(20);
        assertThatThrownBy(
            () -> contentSession.releaseCapacity(-1)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> contentSession.releaseCapacity(1)
        ).isInstanceOf(IllegalStateException.class);
    }

    void transitions_whenCurrentStatusIsNotAllowed_throwException() {
        ContentSession pendingSession = createContentSession();
        ContentSession rejectedSession = createContentSession();
        rejectedSession.reject(reviewer, REVIEWED_AT, "기준 미충족");

        assertThatThrownBy(
            () -> pendingSession.complete(TERMINAL_AT)
        ).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
            () -> pendingSession.cancel(reviewer, TERMINAL_AT, "기상 악화")
        ).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
            () -> rejectedSession.approve(reviewer, REVIEWED_AT)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 생성_시_시간_순서와_정원을_검증한다() {
        Instant startsAt = Instant.parse("2026-08-02T01:00:00Z");
        Instant endsAt = Instant.parse("2026-08-02T03:00:00Z");
        Instant checkinOpenAt = Instant.parse("2026-08-02T00:30:00Z");
        Instant checkinCloseAt = Instant.parse("2026-08-02T02:30:00Z");

        assertThatThrownBy(() -> new ContentSession(
            content,
            region,
            endsAt,
            startsAt,
            checkinOpenAt,
            checkinCloseAt,
            20
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentSession(
            content,
            region,
            startsAt,
            endsAt,
            checkinOpenAt,
            endsAt,
            20
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentSession(
            content,
            region,
            startsAt,
            endsAt,
            checkinOpenAt,
            checkinCloseAt,
            0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ContentSession createContentSession() {
        return new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        );
    }

    private Content createContent() {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    private AppUser createUser(String loginIdentifier) {
        return new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
    }
}
