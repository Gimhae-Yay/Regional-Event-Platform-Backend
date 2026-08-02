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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Entity
@Table(
    name = "content_revision",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_content_revision_content_revision_no",
            columnNames = {"content_id", "revision_no"}
        ),
        @UniqueConstraint(
            name = "uk_content_revision_active_request",
            columnNames = "active_request_content_id"
        )
    },
    check = {
        @CheckConstraint(
            name = "ck_content_revision_status",
            constraint = "status REGEXP '^(EDIT_REQUESTED|EDIT_APPROVED|EDIT_REJECTED|EDIT_WITHDRAWN)$'"
        ),
        @CheckConstraint(
            name = "ck_content_revision_reviewed",
            constraint = """
                status NOT IN ('EDIT_APPROVED', 'EDIT_REJECTED')
                OR (reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL AND review_reason IS NOT NULL)
                """
        ),
        @CheckConstraint(
            name = "ck_content_revision_withdrawn",
            constraint = """
                status <> 'EDIT_WITHDRAWN'
                OR (withdrawn_at IS NOT NULL AND withdrawn_by_user_id IS NOT NULL AND withdrawal_reason IS NOT NULL)
                """
        )
    }
)
public class ContentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_revision_id")
    private Long contentRevisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_revision_content")
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "candidate_image_object_id",
        foreignKey = @ForeignKey(name = "fk_content_revision_candidate_image_object")
    )
    private ImageObject candidateImageObject;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "base_content_version", nullable = false)
    private int baseContentVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "editor_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_revision_editor")
    )
    private AppUser editor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContentRevisionStatus status;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_text", nullable = false, length = 255)
    private String locationText;

    @Column(name = "operating_hours_text", nullable = false, columnDefinition = "TEXT")
    private String operatingHoursText;

    @Column(name = "contact_text", nullable = false, length = 255)
    private String contactText;

    @Column(name = "precautions", nullable = false, columnDefinition = "TEXT")
    private String precautions;

    @Column(name = "age_requirement", nullable = false, length = 255)
    private String ageRequirement;

    @Column(name = "materials", nullable = false, columnDefinition = "TEXT")
    private String materials;

    @Column(name = "cancellation_policy_text", nullable = false, columnDefinition = "TEXT")
    private String cancellationPolicyText;

    @Column(name = "publish_at")
    private Instant publishAt;

    @Column(name = "candidate_image_assigned_at")
    private Instant candidateImageAssignedAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewed_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_revision_reviewer")
    )
    private AppUser reviewedBy;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "withdrawn_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_revision_withdrawer")
    )
    private AppUser withdrawnBy;

    @Column(name = "withdrawal_reason", columnDefinition = "TEXT")
    private String withdrawalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "active_request_content_id", insertable = false, updatable = false)
    private Long activeRequestContentId;

    protected ContentRevision() {
    }

    public ContentRevision(
        Content content,
        int revisionNo,
        int baseContentVersion,
        AppUser editor,
        ContentRevisionStatus status,
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        Instant publishAt,
        Instant submittedAt,
        Instant reviewedAt,
        AppUser reviewedBy,
        String reviewReason,
        Instant withdrawnAt,
        AppUser withdrawnBy,
        String withdrawalReason
    ) {
        this.content = requireNotNull(content, "content");
        this.revisionNo = revisionNo;
        this.baseContentVersion = baseContentVersion;
        this.editor = requireNotNull(editor, "editor");
        this.status = requireNotNull(status, "status");
        this.title = requireNotBlank(title, "title");
        this.description = requireNotBlank(description, "description");
        this.locationText = requireNotBlank(locationText, "locationText");
        this.operatingHoursText = requireNotBlank(operatingHoursText, "operatingHoursText");
        this.contactText = requireNotBlank(contactText, "contactText");
        this.precautions = requireNotBlank(precautions, "precautions");
        this.ageRequirement = requireNotBlank(ageRequirement, "ageRequirement");
        this.materials = requireNotBlank(materials, "materials");
        this.cancellationPolicyText = requireNotBlank(cancellationPolicyText, "cancellationPolicyText");
        this.publishAt = publishAt;
        this.submittedAt = requireNotNull(submittedAt, "submittedAt");
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.reviewReason = reviewReason;
        this.withdrawnAt = withdrawnAt;
        this.withdrawnBy = withdrawnBy;
        this.withdrawalReason = withdrawalReason;
        validateStatusDetails();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public void assignCandidateImage(ImageObject imageObject, Instant assignedAt) {
        candidateImageObject = requireNotNull(imageObject, "imageObject");
        candidateImageAssignedAt = requireNotNull(assignedAt, "assignedAt");
    }

    public void reject(AppUser reviewer, Instant reviewTime, String reason) {
        AppUser validatedReviewer = requireNotNull(reviewer, "reviewer");
        Instant validatedReviewTime = requireNotNull(reviewTime, "reviewTime");
        String normalizedReason = requireNotBlank(reason, "reason").strip();
        if (status != ContentRevisionStatus.EDIT_REQUESTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        status = ContentRevisionStatus.EDIT_REJECTED;
        reviewedAt = validatedReviewTime;
        reviewedBy = validatedReviewer;
        reviewReason = normalizedReason;
    }

    public Long getContentRevisionId() {
        return contentRevisionId;
    }

    public Content getContent() {
        return content;
    }

    public ImageObject getCandidateImageObject() {
        return candidateImageObject;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public int getBaseContentVersion() {
        return baseContentVersion;
    }

    public AppUser getEditor() {
        return editor;
    }

    public ContentRevisionStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocationText() {
        return locationText;
    }

    public String getOperatingHoursText() {
        return operatingHoursText;
    }

    public String getContactText() {
        return contactText;
    }

    public String getPrecautions() {
        return precautions;
    }

    public String getAgeRequirement() {
        return ageRequirement;
    }

    public String getMaterials() {
        return materials;
    }

    public String getCancellationPolicyText() {
        return cancellationPolicyText;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getCandidateImageAssignedAt() {
        return candidateImageAssignedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public AppUser getWithdrawnBy() {
        return withdrawnBy;
    }

    public String getWithdrawalReason() {
        return withdrawalReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void validateStatusDetails() {
        if (status == ContentRevisionStatus.EDIT_APPROVED || status == ContentRevisionStatus.EDIT_REJECTED) {
            requireNotNull(reviewedAt, "reviewedAt");
            requireNotNull(reviewedBy, "reviewedBy");
            requireNotBlank(reviewReason, "reviewReason");
        }

        if (status == ContentRevisionStatus.EDIT_WITHDRAWN) {
            requireNotNull(withdrawnAt, "withdrawnAt");
            requireNotNull(withdrawnBy, "withdrawnBy");
            requireNotBlank(withdrawalReason, "withdrawalReason");
        }
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
