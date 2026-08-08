package io.regionevent.regioneventbackend.domain.coupon.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@Entity
@Table(
    name = "coupon_issuance",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_issuance_coupon", columnNames = "coupon_id"),
        @UniqueConstraint(name = "uk_coupon_issuance_identity_hash", columnNames = "issuance_identity_hash"),
        @UniqueConstraint(
            name = "uk_coupon_issuance_mission_reward_claim",
            columnNames = "mission_reward_claim_id"
        ),
        @UniqueConstraint(
            name = "uk_coupon_issuance_stampbook_reward_grant",
            columnNames = "stampbook_reward_grant_id"
        )
    },
    check = @CheckConstraint(
        name = "ck_coupon_issuance_exactly_one_source",
        constraint = """
            (CASE WHEN visit_id IS NOT NULL THEN 1 ELSE 0 END)
            + (CASE WHEN mission_reward_claim_id IS NOT NULL THEN 1 ELSE 0 END)
            + (CASE WHEN stampbook_reward_grant_id IS NOT NULL THEN 1 ELSE 0 END) = 1
            """
    )
)
public class CouponIssuance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_issuance_id")
    private Long couponIssuanceId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_coupon_issuance_coupon")
    )
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_issuance_coupon_policy")
    )
    private CouponPolicy couponPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "recipient_user_id",
        foreignKey = @ForeignKey(name = "fk_coupon_issuance_recipient_user")
    )
    private AppUser recipientUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "visit_id",
        foreignKey = @ForeignKey(name = "fk_coupon_issuance_visit")
    )
    private Visit visit;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "mission_reward_claim_id",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_coupon_issuance_mission_reward_claim")
    )
    private MissionRewardClaim missionRewardClaim;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "stampbook_reward_grant_id",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_coupon_issuance_stampbook_reward_grant")
    )
    private StampbookRewardGrant stampbookRewardGrant;

    @Column(name = "issuance_identity_hash", nullable = false, length = 255, updatable = false)
    private String issuanceIdentityHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected CouponIssuance() {
    }

    public CouponIssuance(
        Coupon coupon,
        CouponPolicy couponPolicy,
        AppUser recipientUser,
        Visit visit,
        MissionRewardClaim missionRewardClaim,
        StampbookRewardGrant stampbookRewardGrant,
        String issuanceIdentityHash,
        Instant issuedAt
    ) {
        this.coupon = requireNotNull(coupon, "coupon");
        this.couponPolicy = requireNotNull(couponPolicy, "couponPolicy");
        this.recipientUser = requireNotNull(recipientUser, "recipientUser");
        this.visit = visit;
        this.missionRewardClaim = missionRewardClaim;
        this.stampbookRewardGrant = stampbookRewardGrant;
        validateCouponOwnership(coupon, couponPolicy, recipientUser);
        validateSource(couponPolicy, recipientUser, visit, missionRewardClaim, stampbookRewardGrant);
        this.issuanceIdentityHash = requireNotBlank(issuanceIdentityHash, "issuanceIdentityHash");
        this.issuedAt = requireNotNull(issuedAt, "issuedAt");
    }

    public Long getCouponIssuanceId() {
        return couponIssuanceId;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public CouponPolicy getCouponPolicy() {
        return couponPolicy;
    }

    public AppUser getRecipientUser() {
        return recipientUser;
    }

    public Visit getVisit() {
        return visit;
    }

    public MissionRewardClaim getMissionRewardClaim() {
        return missionRewardClaim;
    }

    public StampbookRewardGrant getStampbookRewardGrant() {
        return stampbookRewardGrant;
    }

    public String getIssuanceIdentityHash() {
        return issuanceIdentityHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    private static void validateCouponOwnership(
        Coupon coupon,
        CouponPolicy couponPolicy,
        AppUser recipientUser
    ) {
        if (!isSameCouponPolicy(coupon.getCouponPolicy(), couponPolicy)) {
            throw new IllegalArgumentException("couponPolicy must match coupon couponPolicy");
        }
        if (!isSameUser(coupon.getUser(), recipientUser)) {
            throw new IllegalArgumentException("recipientUser must match coupon user");
        }
    }

    private static void validateSource(
        CouponPolicy couponPolicy,
        AppUser recipientUser,
        Visit visit,
        MissionRewardClaim missionRewardClaim,
        StampbookRewardGrant stampbookRewardGrant
    ) {
        int sourceCount = countSources(visit, missionRewardClaim, stampbookRewardGrant);
        if (sourceCount != 1) {
            throw new IllegalArgumentException("exactly one issuance source is required");
        }
        if (visit != null && couponPolicy.getIssuanceType() != CouponIssuanceType.VISIT) {
            throw new IllegalArgumentException("visit source requires VISIT coupon policy");
        }
        if (visit != null) {
            validateVisitSource(couponPolicy, recipientUser, visit);
        }
        if (missionRewardClaim != null && couponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD) {
            throw new IllegalArgumentException("mission reward source requires MISSION_REWARD coupon policy");
        }
        if (missionRewardClaim != null) {
            validateMissionRewardSource(couponPolicy, recipientUser, missionRewardClaim);
        }
        if (stampbookRewardGrant != null
            && couponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION) {
            throw new IllegalArgumentException("stampbook reward source requires STAMPBOOK_COMPLETION coupon policy");
        }
        if (stampbookRewardGrant != null) {
            validateStampbookRewardSource(couponPolicy, recipientUser, stampbookRewardGrant);
        }
    }

    private static void validateVisitSource(
        CouponPolicy couponPolicy,
        AppUser recipientUser,
        Visit visit
    ) {
        if (!isSameUser(visit.getUser(), recipientUser)) {
            throw new IllegalArgumentException("visit user must match recipientUser");
        }
        if (!isSameContent(visit.getContent(), couponPolicy.getContent())) {
            throw new IllegalArgumentException("visit content must match couponPolicy content");
        }
        if (!isSameRegion(visit.getRegion(), couponPolicy.getRegion())) {
            throw new IllegalArgumentException("visit region must match couponPolicy region");
        }
    }

    private static void validateMissionRewardSource(
        CouponPolicy couponPolicy,
        AppUser recipientUser,
        MissionRewardClaim missionRewardClaim
    ) {
        if (!isSameCouponPolicy(missionRewardClaim.getCouponPolicy(), couponPolicy)) {
            throw new IllegalArgumentException("mission reward couponPolicy must match couponPolicy");
        }
        if (!isSameUser(missionRewardClaim.getMissionParticipation().getUser(), recipientUser)) {
            throw new IllegalArgumentException("mission reward user must match recipientUser");
        }
        if (!isSameRegion(
            missionRewardClaim.getMissionParticipation().getMission().getRegion(),
            couponPolicy.getRegion()
        )) {
            throw new IllegalArgumentException("mission reward region must match couponPolicy region");
        }
    }

    private static void validateStampbookRewardSource(
        CouponPolicy couponPolicy,
        AppUser recipientUser,
        StampbookRewardGrant stampbookRewardGrant
    ) {
        if (!isSameCouponPolicy(stampbookRewardGrant.getCouponPolicy(), couponPolicy)) {
            throw new IllegalArgumentException("stampbook reward couponPolicy must match couponPolicy");
        }
        if (!isSameUser(stampbookRewardGrant.getStampbookProgress().getUser(), recipientUser)) {
            throw new IllegalArgumentException("stampbook reward user must match recipientUser");
        }
        if (!isSameRegion(
            stampbookRewardGrant.getStampbookProgress().getStampbook().getRegion(),
            couponPolicy.getRegion()
        )) {
            throw new IllegalArgumentException("stampbook reward region must match couponPolicy region");
        }
    }

    private static int countSources(
        Visit visit,
        MissionRewardClaim missionRewardClaim,
        StampbookRewardGrant stampbookRewardGrant
    ) {
        return (visit == null ? 0 : 1)
            + (missionRewardClaim == null ? 0 : 1)
            + (stampbookRewardGrant == null ? 0 : 1);
    }

    private static boolean isSameCouponPolicy(
        CouponPolicy firstCouponPolicy,
        CouponPolicy secondCouponPolicy
    ) {
        if (firstCouponPolicy == secondCouponPolicy) {
            return true;
        }
        if (firstCouponPolicy == null || secondCouponPolicy == null) {
            return false;
        }
        Long firstCouponPolicyId = firstCouponPolicy.getCouponPolicyId();
        Long secondCouponPolicyId = secondCouponPolicy.getCouponPolicyId();
        return firstCouponPolicyId != null
            && firstCouponPolicyId.equals(secondCouponPolicyId);
    }

    private static boolean isSameContent(
        Content firstContent,
        Content secondContent
    ) {
        if (firstContent == secondContent) {
            return true;
        }
        if (firstContent == null || secondContent == null) {
            return false;
        }
        Long firstContentId = firstContent.getContentId();
        Long secondContentId = secondContent.getContentId();
        return firstContentId != null && firstContentId.equals(secondContentId);
    }

    private static boolean isSameRegion(
        Region firstRegion,
        Region secondRegion
    ) {
        if (firstRegion == secondRegion) {
            return true;
        }
        if (firstRegion == null || secondRegion == null) {
            return false;
        }
        Long firstRegionId = firstRegion.getRegionId();
        Long secondRegionId = secondRegion.getRegionId();
        return firstRegionId != null && firstRegionId.equals(secondRegionId);
    }

    private static boolean isSameUser(
        AppUser firstUser,
        AppUser secondUser
    ) {
        if (firstUser == secondUser) {
            return true;
        }
        if (firstUser == null || secondUser == null) {
            return false;
        }
        Long firstUserId = firstUser.getUserId();
        Long secondUserId = secondUser.getUserId();
        return firstUserId != null && firstUserId.equals(secondUserId);
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
