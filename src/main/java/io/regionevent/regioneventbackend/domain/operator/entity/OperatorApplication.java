package io.regionevent.regioneventbackend.domain.operator.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "operator_application",
    check = {
        @CheckConstraint(
            name = "ck_operator_application_status",
            constraint = "status REGEXP '^(PENDING|APPROVED|REJECTED|CANCELLED)$'"
        ),
        @CheckConstraint(
            name = "ck_operator_application_approved_review_result",
            constraint = """
                status <> 'APPROVED'
                OR (inspected_user_id IS NOT NULL AND rejected_reason IS NULL)
                """
        ),
        @CheckConstraint(
            name = "ck_operator_application_rejected_review_result",
            constraint = """
                status <> 'REJECTED'
                OR (inspected_user_id IS NOT NULL AND rejected_reason IS NOT NULL)
                """
        )
    }
)
public class OperatorApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operator_application_id")
    private Long operatorApplicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "applicant_user_id",
        foreignKey = @ForeignKey(name = "fk_operator_application_applicant_user")
    )
    private AppUser applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "requested_region_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_operator_application_requested_region")
    )
    private Region requestedRegion;

    @Column(name = "business_information", columnDefinition = "TEXT")
    private String businessInformation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OperatorApplicationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "inspected_user_id",
        foreignKey = @ForeignKey(name = "fk_operator_application_inspected_user")
    )
    private AppUser inspectedUser;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean updatedAtExplicitlySet;

    protected OperatorApplication() {
    }

    public OperatorApplication(
        AppUser applicant,
        Region requestedRegion,
        String businessInformation,
        OperatorApplicationStatus status,
        AppUser inspectedUser,
        String rejectedReason
    ) {
        this.applicant = requireNotNull(applicant, "applicant");
        this.requestedRegion = requireNotNull(requestedRegion, "requestedRegion");
        this.businessInformation = requireNotBlank(businessInformation, "businessInformation");
        this.status = requireNotNull(status, "status");
        this.inspectedUser = inspectedUser;
        this.rejectedReason = rejectedReason;
        validateReviewResult();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        if (!updatedAtExplicitlySet) {
            updatedAt = Instant.now();
        }
    }

    public Long getOperatorApplicationId() {
        return operatorApplicationId;
    }

    public AppUser getApplicant() {
        return applicant;
    }

    public Region getRequestedRegion() {
        return requestedRegion;
    }

    public String getBusinessInformation() {
        return businessInformation;
    }

    public OperatorApplicationStatus getStatus() {
        return status;
    }

    public AppUser getInspectedUser() {
        return inspectedUser;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void approve(AppUser reviewer, Instant approvedAt) {
        if (status != OperatorApplicationStatus.PENDING) {
            throw new IllegalStateException("only pending application can be approved");
        }
        status = OperatorApplicationStatus.APPROVED;
        inspectedUser = requireNotNull(reviewer, "reviewer");
        rejectedReason = null;
        updatedAt = requireNotNull(approvedAt, "approvedAt").truncatedTo(ChronoUnit.MICROS);
        updatedAtExplicitlySet = true;
    }

    private void validateReviewResult() {
        if (status == OperatorApplicationStatus.APPROVED) {
            requireNotNull(inspectedUser, "inspectedUser");
            if (rejectedReason != null) {
                throw new IllegalArgumentException("rejectedReason must be null when status is APPROVED");
            }
        }

        if (status == OperatorApplicationStatus.REJECTED) {
            requireNotNull(inspectedUser, "inspectedUser");
            requireNotBlank(rejectedReason, "rejectedReason");
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
