package io.regionevent.regioneventbackend.domain.content.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Entity
@Table(
    name = "content_withdrawal_request",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_content_withdrawal_request_content_key",
            columnNames = {"content_id", "idempotency_key_hash"}
        ),
        @UniqueConstraint(
            name = "uk_content_withdrawal_request_active_content",
            columnNames = "active_request_content_id"
        )
    },
    indexes = @Index(
        name = "idx_content_withdrawal_request_history",
        columnList = "content_id, requested_at, content_withdrawal_request_id"
    ),
    check = {
        @CheckConstraint(
            name = "ck_content_withdrawal_request_key_hash",
            constraint = "idempotency_key_hash REGEXP '^[0-9a-f]{64}$'"
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_reason",
            constraint = "CHAR_LENGTH(TRIM(request_reason)) > 0"
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_status",
            constraint = "status REGEXP '^(PENDING|APPROVED|REJECTED|INVALIDATED)$'"
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_invalidation_reason",
            constraint = """
                invalidation_reason IS NULL
                OR invalidation_reason REGEXP '^(CONTENT_SUSPENDED|CONTENT_ENDED)$'
                """
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_pending_fields",
            constraint = """
                status <> 'PENDING' OR (
                    reviewed_at IS NULL
                    AND reviewed_by_user_id IS NULL
                    AND rejection_reason IS NULL
                    AND invalidated_at IS NULL
                    AND invalidated_by_user_id IS NULL
                    AND invalidation_reason IS NULL
                )
                """
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_approved_fields",
            constraint = """
                status <> 'APPROVED' OR (
                    reviewed_at IS NOT NULL
                    AND rejection_reason IS NULL
                    AND invalidated_at IS NULL
                    AND invalidated_by_user_id IS NULL
                    AND invalidation_reason IS NULL
                )
                """
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_rejected_fields",
            constraint = """
                status <> 'REJECTED' OR (
                    reviewed_at IS NOT NULL
                    AND rejection_reason IS NOT NULL
                    AND CHAR_LENGTH(TRIM(rejection_reason)) > 0
                    AND invalidated_at IS NULL
                    AND invalidated_by_user_id IS NULL
                    AND invalidation_reason IS NULL
                )
                """
        ),
        @CheckConstraint(
            name = "ck_content_withdrawal_request_invalidated_fields",
            constraint = """
                status <> 'INVALIDATED' OR (
                    reviewed_at IS NULL
                    AND reviewed_by_user_id IS NULL
                    AND rejection_reason IS NULL
                    AND invalidated_at IS NOT NULL
                    AND invalidation_reason IS NOT NULL
                )
                """
        )
    }
)
public class ContentWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_withdrawal_request_id")
    private Long contentWithdrawalRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_withdrawal_request_content")
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "requested_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_withdrawal_request_requester")
    )
    private AppUser requestedBy;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContentWithdrawalRequestStatus status;

    @Column(name = "request_reason", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String requestReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewed_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_withdrawal_request_reviewer")
    )
    private AppUser reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "invalidated_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_withdrawal_request_invalidator")
    )
    private AppUser invalidatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "invalidation_reason", length = 30)
    private ContentWithdrawalRequestInvalidationReason invalidationReason;

    @Column(name = "active_request_content_id", insertable = false, updatable = false)
    private Long activeRequestContentId;

    protected ContentWithdrawalRequest() {
    }

    public static ContentWithdrawalRequest createPending(
        Content content,
        AppUser requester,
        String idempotencyKeyHash,
        String requestReason,
        Instant requestedAt
    ) {
        ContentWithdrawalRequest request = new ContentWithdrawalRequest();
        request.content = requireNotNull(content, "content");
        request.requestedBy = requireNotNull(requester, "requester");
        request.idempotencyKeyHash = requireHash(idempotencyKeyHash);
        request.status = ContentWithdrawalRequestStatus.PENDING;
        request.requestReason = requireNotBlank(requestReason, "requestReason").strip();
        request.requestedAt = requireNotNull(requestedAt, "requestedAt");
        return request;
    }

    public void invalidateByUser(
        AppUser invalidator,
        Instant invalidatedAt,
        ContentWithdrawalRequestInvalidationReason reason
    ) {
        requirePending();
        this.invalidatedBy = requireNotNull(invalidator, "invalidator");
        this.invalidatedAt = requireNotNull(invalidatedAt, "invalidatedAt");
        this.invalidationReason = requireNotNull(reason, "reason");
        status = ContentWithdrawalRequestStatus.INVALIDATED;
    }

    public void invalidateBySystem(
        Instant invalidatedAt,
        ContentWithdrawalRequestInvalidationReason reason
    ) {
        requirePending();
        if (reason != ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED) {
            throw new IllegalArgumentException("system invalidation reason must be CONTENT_ENDED");
        }
        this.invalidatedAt = requireNotNull(invalidatedAt, "invalidatedAt");
        invalidationReason = reason;
        invalidatedBy = null;
        status = ContentWithdrawalRequestStatus.INVALIDATED;
    }

    public void approve(AppUser reviewer, Instant reviewedAt) {
        requirePending();
        this.reviewedBy = requireNotNull(reviewer, "reviewer");
        this.reviewedAt = requireNotNull(reviewedAt, "reviewedAt");
        status = ContentWithdrawalRequestStatus.APPROVED;
    }

    public Long getContentWithdrawalRequestId() {
        return contentWithdrawalRequestId;
    }

    public Content getContent() {
        return content;
    }

    public AppUser getRequestedBy() {
        return requestedBy;
    }

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public ContentWithdrawalRequestStatus getStatus() {
        return status;
    }

    public String getRequestReason() {
        return requestReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public AppUser getInvalidatedBy() {
        return invalidatedBy;
    }

    public ContentWithdrawalRequestInvalidationReason getInvalidationReason() {
        return invalidationReason;
    }

    private void requirePending() {
        if (status != ContentWithdrawalRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
    }

    private static String requireHash(String value) {
        String hash = requireNotBlank(value, "idempotencyKeyHash");
        if (!hash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("idempotencyKeyHash must be a SHA-256 hex value");
        }
        return hash;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
