package io.regionevent.regioneventbackend.domain.mission.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Entity
@Table(
    name = "mission",
    check = {
        @CheckConstraint(
            name = "ck_mission_condition_type",
            constraint = "condition_type REGEXP '^(VISIT_COUNT|CONTENT_SET)$'"
        ),
        @CheckConstraint(
            name = "ck_mission_required_visit_count",
            constraint = """
                CASE
                    WHEN condition_type = 'VISIT_COUNT' AND required_visit_count > 0 THEN 1
                    WHEN condition_type = 'CONTENT_SET' AND required_visit_count IS NULL THEN 1
                    ELSE 0
                END = 1
                """
        ),
        @CheckConstraint(
            name = "ck_mission_status",
            constraint = "status REGEXP '^(DRAFT|PENDING_REVIEW|PUBLISHED|ENDED)$'"
        ),
        @CheckConstraint(
            name = "ck_mission_status_timestamps",
            constraint = """
                CASE
                    WHEN (status = 'DRAFT' OR status = 'PENDING_REVIEW')
                        AND published_at IS NULL
                        AND ended_at IS NULL THEN 1
                    WHEN status = 'PUBLISHED'
                        AND published_at IS NOT NULL
                        AND published_at < ends_at
                        AND ended_at IS NULL THEN 1
                    WHEN status = 'ENDED'
                        AND published_at IS NOT NULL
                        AND published_at < ends_at
                        AND ended_at IS NOT NULL THEN 1
                    ELSE 0
                END = 1
                """
        )
    }
)
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_id")
    private Long missionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_region")
    )
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 30)
    private MissionConditionType conditionType;

    @Column(name = "required_visit_count")
    private Integer requiredVisitCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reward_coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_reward_coupon_policy")
    )
    private CouponPolicy rewardCouponPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MissionStatus status;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MissionTargetContent> targetContents = new ArrayList<>();

    protected Mission() {
    }

    public Mission(
        Region region,
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        CouponPolicy rewardCouponPolicy,
        Instant endsAt
    ) {
        this.region = requireNotNull(region, "region");
        this.conditionType = requireNotNull(conditionType, "conditionType");
        this.requiredVisitCount = validateRequiredVisitCount(conditionType, requiredVisitCount);
        this.rewardCouponPolicy = validateRewardCouponPolicy(rewardCouponPolicy, region);
        this.endsAt = requireNotNull(endsAt, "endsAt");
        this.status = MissionStatus.DRAFT;
    }

    public MissionTargetContent addTargetContent(Content content) {
        validateContentSetCondition();
        MissionTargetContent targetContent = new MissionTargetContent(this, content);
        targetContents.add(targetContent);
        return targetContent;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Region getRegion() {
        return region;
    }

    public MissionConditionType getConditionType() {
        return conditionType;
    }

    public Integer getRequiredVisitCount() {
        return requiredVisitCount;
    }

    public CouponPolicy getRewardCouponPolicy() {
        return rewardCouponPolicy;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public List<MissionTargetContent> getTargetContents() {
        return List.copyOf(targetContents);
    }

    private static Integer validateRequiredVisitCount(
        MissionConditionType conditionType,
        Integer requiredVisitCount
    ) {
        if (conditionType == MissionConditionType.VISIT_COUNT) {
            if (requiredVisitCount == null || requiredVisitCount < 1) {
                throw new IllegalArgumentException("VISIT_COUNT requires a positive requiredVisitCount");
            }
            return requiredVisitCount;
        }
        if (requiredVisitCount != null) {
            throw new IllegalArgumentException("CONTENT_SET must not have requiredVisitCount");
        }
        return null;
    }

    private static CouponPolicy validateRewardCouponPolicy(
        CouponPolicy rewardCouponPolicy,
        Region region
    ) {
        CouponPolicy validatedRewardCouponPolicy = requireNotNull(rewardCouponPolicy, "rewardCouponPolicy");
        if (validatedRewardCouponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD) {
            throw new IllegalArgumentException("rewardCouponPolicy must use MISSION_REWARD issuance type");
        }
        if (validatedRewardCouponPolicy.getStatus() == CouponPolicyStatus.ENDED) {
            throw new IllegalArgumentException("ended rewardCouponPolicy is not allowed");
        }
        validateSameRegion(region, validatedRewardCouponPolicy.getRegion(), "rewardCouponPolicy");
        return validatedRewardCouponPolicy;
    }

    private void validateContentSetCondition() {
        if (conditionType != MissionConditionType.CONTENT_SET) {
            throw new IllegalStateException("only CONTENT_SET can add target contents");
        }
    }

    static void validateContentRegion(
        Mission mission,
        Content content
    ) {
        requireNotNull(mission, "mission");
        requireNotNull(content, "content");
        validateSameRegion(mission.region, content.getRegion(), "content");
    }

    private static void validateSameRegion(
        Region expectedRegion,
        Region actualRegion,
        String fieldName
    ) {
        Long expectedRegionId = expectedRegion.getRegionId();
        Long actualRegionId = actualRegion.getRegionId();
        if (expectedRegionId != null && actualRegionId != null && !expectedRegionId.equals(actualRegionId)) {
            throw new IllegalArgumentException(fieldName + " must belong to the mission region");
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
