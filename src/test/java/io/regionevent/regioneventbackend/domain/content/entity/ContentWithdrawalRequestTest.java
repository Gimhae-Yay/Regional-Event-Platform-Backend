package io.regionevent.regioneventbackend.domain.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ContentWithdrawalRequestTest {

    private static final String KEY_HASH = "a".repeat(64);
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-16T04:00:00Z");
    private static final Instant INVALIDATED_AT = Instant.parse("2026-08-16T05:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-16T06:00:00Z");

    @Test
    void 대기_요청을_생성할_때_사유를_정규화한다() {
        Content content = mock(Content.class);
        AppUser requester = mock(AppUser.class);

        ContentWithdrawalRequest request = ContentWithdrawalRequest.createPending(
            content,
            requester,
            KEY_HASH,
            "  운영 계획 변경  ",
            REQUESTED_AT
        );

        assertThat(request.getContent()).isSameAs(content);
        assertThat(request.getRequestedBy()).isSameAs(requester);
        assertThat(request.getIdempotencyKeyHash()).isEqualTo(KEY_HASH);
        assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
        assertThat(request.getRequestReason()).isEqualTo("운영 계획 변경");
        assertThat(request.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(request.getReviewedAt()).isNull();
        assertThat(request.getInvalidatedAt()).isNull();
    }

    @Test
    void 수동_중단은_처리자와_사유로_대기_요청을_무효화한다() {
        AppUser invalidator = mock(AppUser.class);
        ContentWithdrawalRequest request = pendingRequest();

        request.invalidateByUser(
            invalidator,
            INVALIDATED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_SUSPENDED
        );

        assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.INVALIDATED);
        assertThat(request.getInvalidatedBy()).isSameAs(invalidator);
        assertThat(request.getInvalidatedAt()).isEqualTo(INVALIDATED_AT);
        assertThat(request.getInvalidationReason())
            .isEqualTo(ContentWithdrawalRequestInvalidationReason.CONTENT_SUSPENDED);
    }

    @Test
    void 자동_종료는_처리자_없이_CONTENT_ENDED로만_무효화한다() {
        ContentWithdrawalRequest request = pendingRequest();

        request.invalidateBySystem(
            INVALIDATED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        );

        assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.INVALIDATED);
        assertThat(request.getInvalidatedBy()).isNull();
        assertThat(request.getInvalidationReason())
            .isEqualTo(ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED);
    }

    @Test
    void 시스템이_중단_사유로_무효화할_수_없다() {
        ContentWithdrawalRequest request = pendingRequest();

        assertThatThrownBy(() -> request.invalidateBySystem(
            INVALIDATED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_SUSPENDED
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 대기_요청을_승인하면_심사자와_심사_시각을_기록한다() {
        AppUser reviewer = mock(AppUser.class);
        ContentWithdrawalRequest request = pendingRequest();

        request.approve(reviewer, REVIEWED_AT);

        assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(request.getReviewedBy()).isSameAs(reviewer);
        assertThat(request.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(request.getRequestReason()).isEqualTo("운영 계획 변경");
    }

    @Test
    void 대기_요청을_반려하면_심사자와_시각과_정규화된_사유를_기록한다() {
        AppUser reviewer = mock(AppUser.class);
        ContentWithdrawalRequest request = pendingRequest();

        request.reject(reviewer, REVIEWED_AT, "  운영 근거 부족  ");

        assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.REJECTED);
        assertThat(request.getReviewedBy()).isSameAs(reviewer);
        assertThat(request.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(request.getRejectionReason()).isEqualTo("운영 근거 부족");
    }

    @Test
    void 반려_심사_메타데이터는_null이나_공백일_수_없다() {
        assertThatThrownBy(() -> pendingRequest().reject(null, REVIEWED_AT, "반려 사유"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pendingRequest().reject(mock(AppUser.class), null, "반려 사유"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pendingRequest().reject(mock(AppUser.class), REVIEWED_AT, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pendingRequest().reject(mock(AppUser.class), REVIEWED_AT, "   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 종결된_요청은_반려로_다시_전이할_수_없다() {
        ContentWithdrawalRequest request = pendingRequest();
        request.approve(mock(AppUser.class), REVIEWED_AT);

        assertThatThrownBy(() -> request.reject(
            mock(AppUser.class),
            REVIEWED_AT.plusSeconds(60),
            "반려 사유"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
    }

    @Test
    void 대기가_아닌_요청은_승인할_수_없다() {
        ContentWithdrawalRequest request = pendingRequest();
        request.invalidateBySystem(
            INVALIDATED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        );

        assertThatThrownBy(() -> request.approve(mock(AppUser.class), REVIEWED_AT))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    @Test
    void 종결된_요청은_다시_전이할_수_없다() {
        ContentWithdrawalRequest request = pendingRequest();
        request.invalidateBySystem(
            INVALIDATED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        );

        assertThatThrownBy(() -> request.invalidateBySystem(
            INVALIDATED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
    }

    private ContentWithdrawalRequest pendingRequest() {
        return ContentWithdrawalRequest.createPending(
            mock(Content.class),
            mock(AppUser.class),
            KEY_HASH,
            "운영 계획 변경",
            REQUESTED_AT
        );
    }
}
