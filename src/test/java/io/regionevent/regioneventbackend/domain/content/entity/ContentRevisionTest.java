package io.regionevent.regioneventbackend.domain.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ContentRevisionTest {

    private static final Instant REVIEWED_AT = Instant.parse("2026-08-02T01:00:00Z");

    @Test
    void reject_whenEditIsRequested_recordsNormalizedReviewDetails() {
        AppUser reviewer = newUser("reviewer@example.com");
        ContentRevision revision = newRevision();

        revision.reject(reviewer, REVIEWED_AT, "  공개 일정의 정합성을 보완해 주세요.  ");

        assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(revision.getReviewedBy()).isSameAs(reviewer);
        assertThat(revision.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(revision.getReviewReason()).isEqualTo("공개 일정의 정합성을 보완해 주세요.");
    }

    @ParameterizedTest
    @EnumSource(
        value = ContentRevisionStatus.class,
        names = {"EDIT_APPROVED", "EDIT_REJECTED", "EDIT_WITHDRAWN"}
    )
    void reject_whenRevisionIsAlreadyTerminal_throwsContentStateConflictWithoutOverwritingState(
        ContentRevisionStatus terminalStatus
    ) {
        AppUser firstReviewer = newUser("first-reviewer@example.com");
        ContentRevision revision = newRevision(terminalStatus, firstReviewer);

        assertThatThrownBy(() -> revision.reject(
            newUser("second-reviewer@example.com"),
            REVIEWED_AT.plusSeconds(60),
            "두 번째 반려 사유"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
        assertThat(revision.getStatus()).isEqualTo(terminalStatus);
        if (terminalStatus == ContentRevisionStatus.EDIT_WITHDRAWN) {
            assertThat(revision.getWithdrawnBy()).isSameAs(firstReviewer);
            assertThat(revision.getWithdrawalReason()).isEqualTo("기존 철회 사유");
        } else {
            assertThat(revision.getReviewedBy()).isSameAs(firstReviewer);
            if (terminalStatus == ContentRevisionStatus.EDIT_APPROVED) {
                assertThat(revision.getReviewReason()).isNull();
            } else {
                assertThat(revision.getReviewReason()).isEqualTo("기존 심사 사유");
            }
        }
    }

    @Test
    void reject_whenReviewDetailsAreMissing_rejectsTransitionWithoutChangingStatus() {
        ContentRevision revision = newRevision();

        assertThatThrownBy(() -> revision.reject(newUser("reviewer@example.com"), REVIEWED_AT, "  "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(revision.getReviewedBy()).isNull();
        assertThat(revision.getReviewedAt()).isNull();
        assertThat(revision.getReviewReason()).isNull();
    }

    @Test
    void approve_whenEditIsRequested_recordsReviewerWithoutReason() {
        AppUser reviewer = newUser("reviewer@example.com");
        ContentRevision revision = newRevision();

        revision.approve(reviewer, REVIEWED_AT);

        assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_APPROVED);
        assertThat(revision.getReviewedBy()).isSameAs(reviewer);
        assertThat(revision.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(revision.getReviewReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ContentRevisionStatus.class,
        names = {"EDIT_APPROVED", "EDIT_REJECTED", "EDIT_WITHDRAWN"}
    )
    void approve_whenRevisionIsAlreadyTerminal_throwsContentStateConflict(
        ContentRevisionStatus terminalStatus
    ) {
        ContentRevision revision = newRevision(
            terminalStatus,
            newUser("first-reviewer@example.com")
        );

        assertThatThrownBy(() -> revision.approve(
            newUser("second-reviewer@example.com"),
            REVIEWED_AT.plusSeconds(60)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
        assertThat(revision.getStatus()).isEqualTo(terminalStatus);
    }

    @Test
    void constructor_whenApprovedRevisionHasReason_rejectsInvalidState() {
        assertThatThrownBy(() -> newRevision(
            ContentRevisionStatus.EDIT_APPROVED,
            newUser("reviewer@example.com"),
            "승인 사유"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ContentRevision newRevision() {
        return newRevision(ContentRevisionStatus.EDIT_REQUESTED, null);
    }

    private ContentRevision newRevision(ContentRevisionStatus status, AppUser reviewer) {
        return newRevision(
            status,
            reviewer,
            status == ContentRevisionStatus.EDIT_REJECTED ? "기존 심사 사유" : null
        );
    }

    private ContentRevision newRevision(
        ContentRevisionStatus status,
        AppUser reviewer,
        String reviewReason
    ) {
        Region region = new Region("GIMHAE", "김해시", true);
        AppUser operator = newUser("operator@example.com");
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
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
        return new ContentRevision(
            content,
            1,
            0,
            operator,
            status,
            "김해 가야 문화 체험 수정본",
            "김해 가야 문화를 체험하는 행사 수정 설명입니다.",
            "김해문화의전당 대공연장",
            "매일 11:00~19:00",
            "055-987-6543",
            "현장 안내를 따라주세요.",
            "만 8세 이상",
            "운동화",
            "시작 이틀 전까지 취소할 수 있습니다.",
            null,
            Instant.parse("2026-08-01T00:00:00Z"),
            isReviewed(status) ? REVIEWED_AT : null,
            isReviewed(status) ? reviewer : null,
            reviewReason,
            status == ContentRevisionStatus.EDIT_WITHDRAWN ? REVIEWED_AT : null,
            status == ContentRevisionStatus.EDIT_WITHDRAWN ? reviewer : null,
            status == ContentRevisionStatus.EDIT_WITHDRAWN ? "기존 철회 사유" : null
        );
    }

    private boolean isReviewed(ContentRevisionStatus status) {
        return status == ContentRevisionStatus.EDIT_APPROVED
            || status == ContentRevisionStatus.EDIT_REJECTED;
    }

    private AppUser newUser(String loginIdentifier) {
        return new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
    }
}
