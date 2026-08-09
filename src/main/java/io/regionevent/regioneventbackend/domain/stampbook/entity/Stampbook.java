package io.regionevent.regioneventbackend.domain.stampbook.entity;

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
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Entity
@Table(
    name = "stampbook",
    check = {
        @CheckConstraint(
            name = "ck_stampbook_status",
            constraint = "status REGEXP '^(DRAFT|PENDING_REVIEW|PUBLISHED|ENDED)$'"
        ),
        @CheckConstraint(
            name = "ck_stampbook_status_timestamps",
            constraint = """
                CASE
                    WHEN status = 'DRAFT'
                        AND published_at IS NULL
                        AND ended_at IS NULL THEN 1
                    WHEN status = 'PENDING_REVIEW'
                        AND published_at IS NULL
                        AND ended_at IS NULL THEN 1
                    WHEN status = 'PUBLISHED'
                        AND published_at IS NOT NULL
                        AND ended_at IS NULL THEN 1
                    WHEN status = 'ENDED'
                        AND published_at IS NOT NULL
                        AND ended_at IS NOT NULL THEN 1
                    ELSE 0
                END = 1
                """
        )
    }
)
public class Stampbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stampbook_id")
    private Long stampbookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stampbook_region")
    )
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reward_coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stampbook_reward_coupon_policy")
    )
    private CouponPolicy rewardCouponPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StampbookStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Stampbook() {
    }

    public Stampbook(
        Region region,
        CouponPolicy rewardCouponPolicy
    ) {
        this.region = requireNotNull(region, "region");
        this.rewardCouponPolicy = requireNotNull(rewardCouponPolicy, "rewardCouponPolicy");
        validateRewardCouponPolicy(region, rewardCouponPolicy);
        this.status = StampbookStatus.DRAFT;
    }

    public Long getStampbookId() {
        return stampbookId;
    }

    public Region getRegion() {
        return region;
    }

    public CouponPolicy getRewardCouponPolicy() {
        return rewardCouponPolicy;
    }

    public StampbookStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void updateRewardCouponPolicy(CouponPolicy rewardCouponPolicy) {
        if (status != StampbookStatus.DRAFT) {
            throw new IllegalStateException("only DRAFT stampbook can update reward coupon policy");
        }
        CouponPolicy validatedRewardCouponPolicy = requireNotNull(
            rewardCouponPolicy,
            "rewardCouponPolicy"
        );
        validateRewardCouponPolicy(region, validatedRewardCouponPolicy);
        this.rewardCouponPolicy = validatedRewardCouponPolicy;
    }

    public void requestPublication() {
        if (status != StampbookStatus.DRAFT) {
            throw new IllegalStateException("only DRAFT stampbook can request publication");
        }
        status = StampbookStatus.PENDING_REVIEW;
    }

    private static void validateRewardCouponPolicy(
        Region region,
        CouponPolicy rewardCouponPolicy
    ) {
        if (rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION) {
            throw new IllegalArgumentException("rewardCouponPolicy must use STAMPBOOK_COMPLETION");
        }
        Long regionId = region.getRegionId();
        Long rewardPolicyRegionId = rewardCouponPolicy.getRegion().getRegionId();
        if (regionId != null && rewardPolicyRegionId != null && !regionId.equals(rewardPolicyRegionId)) {
            throw new IllegalArgumentException("rewardCouponPolicy must belong to stampbook region");
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
}
