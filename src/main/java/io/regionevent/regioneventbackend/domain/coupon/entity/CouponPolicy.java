package io.regionevent.regioneventbackend.domain.coupon.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
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
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Entity
@Table(
    name = "coupon_policy",
    check = {
        @CheckConstraint(
            name = "ck_coupon_policy_issuance_type",
            constraint = "issuance_type REGEXP '^(VISIT|MISSION_REWARD|STAMPBOOK_COMPLETION)$'"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_discount_amount",
            constraint = "discount_amount >= 1"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_minimum_payment_amount",
            constraint = "minimum_payment_amount >= discount_amount"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_valid_days",
            constraint = "valid_days BETWEEN 1 AND 365"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_issue_period",
            constraint = "issue_starts_at < issue_ends_at"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_total_issue_limit",
            constraint = "total_issue_limit IS NULL OR total_issue_limit >= 1"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_issued_count",
            constraint = "issued_count >= 0"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_issued_count_limit",
            constraint = "total_issue_limit IS NULL OR issued_count <= total_issue_limit"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_status",
            constraint = "status REGEXP '^(DRAFT|PUBLISHED|ENDED)$'"
        ),
        @CheckConstraint(
            name = "ck_coupon_policy_status_timestamps",
            constraint = """
                CASE
                    WHEN status = 'DRAFT' AND published_at IS NULL AND ended_at IS NULL THEN 1
                    WHEN status = 'PUBLISHED' AND published_at IS NOT NULL AND ended_at IS NULL THEN 1
                    WHEN status = 'ENDED' AND published_at IS NOT NULL AND ended_at IS NOT NULL THEN 1
                    ELSE 0
                END = 1
                """
        )
    }
)
public class CouponPolicy {

    private static final int MINIMUM_VALID_DAYS = 1;
    private static final int MAXIMUM_VALID_DAYS = 365;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_policy_id")
    private Long couponPolicyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_policy_region")
    )
    private Region region;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuance_type", nullable = false, length = 30)
    private CouponIssuanceType issuanceType;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "minimum_payment_amount", nullable = false)
    private long minimumPaymentAmount;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    @Column(name = "issue_starts_at", nullable = false)
    private Instant issueStartsAt;

    @Column(name = "issue_ends_at", nullable = false)
    private Instant issueEndsAt;

    @Column(name = "total_issue_limit")
    private Long totalIssueLimit;

    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CouponPolicyStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CouponPolicy() {
    }

    public CouponPolicy(
        Content content,
        Region region,
        String name,
        String description,
        CouponIssuanceType issuanceType,
        long discountAmount,
        long minimumPaymentAmount,
        int validDays,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit
    ) {
        this.content = requireNotNull(content, "content");
        this.region = requireNotNull(region, "region");
        validateContentRegion(content, region);
        this.name = requireNotBlank(name, "name");
        this.description = description;
        this.issuanceType = requireNotNull(issuanceType, "issuanceType");
        this.discountAmount = validateDiscountAmount(discountAmount);
        this.minimumPaymentAmount = validateMinimumPaymentAmount(
            minimumPaymentAmount,
            this.discountAmount
        );
        this.validDays = validateValidDays(validDays);
        this.issueStartsAt = requireNotNull(issueStartsAt, "issueStartsAt");
        this.issueEndsAt = requireNotNull(issueEndsAt, "issueEndsAt");
        validateIssuePeriod(issueStartsAt, issueEndsAt);
        this.totalIssueLimit = validateTotalIssueLimit(totalIssueLimit);
        this.issuedCount = 0;
        this.status = CouponPolicyStatus.DRAFT;
    }

    public void publish(Instant publishedAt) {
        validateStatus(CouponPolicyStatus.DRAFT);
        this.status = CouponPolicyStatus.PUBLISHED;
        this.publishedAt = requireNotNull(publishedAt, "publishedAt");
    }

    public void end(Instant endedAt) {
        validateStatus(CouponPolicyStatus.PUBLISHED);
        this.status = CouponPolicyStatus.ENDED;
        this.endedAt = requireNotNull(endedAt, "endedAt");
    }

    public void update(
        String name,
        String description,
        long discountAmount,
        long minimumPaymentAmount,
        int validDays,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit
    ) {
        validateStatus(CouponPolicyStatus.DRAFT);
        this.name = requireNotBlank(name, "name");
        this.description = description;
        this.discountAmount = validateDiscountAmount(discountAmount);
        this.minimumPaymentAmount = validateMinimumPaymentAmount(
            minimumPaymentAmount,
            this.discountAmount
        );
        this.validDays = validateValidDays(validDays);
        this.issueStartsAt = requireNotNull(issueStartsAt, "issueStartsAt");
        this.issueEndsAt = requireNotNull(issueEndsAt, "issueEndsAt");
        validateIssuePeriod(this.issueStartsAt, this.issueEndsAt);
        this.totalIssueLimit = validateTotalIssueLimit(totalIssueLimit);
    }

    public void issue(Instant issuedAt) {
        Instant validatedIssuedAt = requireNotNull(issuedAt, "issuedAt");
        validateStatus(CouponPolicyStatus.PUBLISHED);
        if (validatedIssuedAt.isBefore(issueStartsAt) || validatedIssuedAt.isAfter(issueEndsAt)) {
            throw new IllegalStateException("coupon policy is outside the issue period");
        }
        if (totalIssueLimit != null && issuedCount >= totalIssueLimit) {
            throw new IllegalStateException("coupon policy issue limit is exhausted");
        }
        issuedCount++;
    }

    public Long getCouponPolicyId() {
        return couponPolicyId;
    }

    public Content getContent() {
        return content;
    }

    public Region getRegion() {
        return region;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CouponIssuanceType getIssuanceType() {
        return issuanceType;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public long getMinimumPaymentAmount() {
        return minimumPaymentAmount;
    }

    public int getValidDays() {
        return validDays;
    }

    public Instant getIssueStartsAt() {
        return issueStartsAt;
    }

    public Instant getIssueEndsAt() {
        return issueEndsAt;
    }

    public Long getTotalIssueLimit() {
        return totalIssueLimit;
    }

    public long getIssuedCount() {
        return issuedCount;
    }

    public CouponPolicyStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    private static void validateContentRegion(
        Content content,
        Region region
    ) {
        Long contentRegionId = content.getRegion().getRegionId();
        Long regionId = region.getRegionId();
        if (contentRegionId != null && regionId != null && !contentRegionId.equals(regionId)) {
            throw new IllegalArgumentException("content and region must match");
        }
    }

    private static long validateDiscountAmount(long discountAmount) {
        if (discountAmount < 1) {
            throw new IllegalArgumentException("discountAmount must be at least 1");
        }
        return discountAmount;
    }

    private static long validateMinimumPaymentAmount(
        long minimumPaymentAmount,
        long discountAmount
    ) {
        if (minimumPaymentAmount < discountAmount) {
            throw new IllegalArgumentException("minimumPaymentAmount must be at least discountAmount");
        }
        return minimumPaymentAmount;
    }

    private static int validateValidDays(int validDays) {
        if (validDays < MINIMUM_VALID_DAYS || validDays > MAXIMUM_VALID_DAYS) {
            throw new IllegalArgumentException("validDays must be between 1 and 365");
        }
        return validDays;
    }

    private static void validateIssuePeriod(
        Instant issueStartsAt,
        Instant issueEndsAt
    ) {
        if (!issueStartsAt.isBefore(issueEndsAt)) {
            throw new IllegalArgumentException("issueStartsAt must be before issueEndsAt");
        }
    }

    private static Long validateTotalIssueLimit(Long totalIssueLimit) {
        if (totalIssueLimit != null && totalIssueLimit < 1) {
            throw new IllegalArgumentException("totalIssueLimit must be at least 1");
        }
        return totalIssueLimit;
    }

    private void validateStatus(CouponPolicyStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("coupon policy status must be " + expectedStatus);
        }
    }

    private static <T> T requireNotNull(
        T value,
        String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireNotBlank(
        String value,
        String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
