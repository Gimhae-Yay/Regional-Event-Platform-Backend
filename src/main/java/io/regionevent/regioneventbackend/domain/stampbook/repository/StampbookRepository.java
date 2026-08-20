package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public interface StampbookRepository extends JpaRepository<Stampbook, Long> {

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    Optional<Stampbook> findByStampbookId(Long stampbookId);

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT stampbook
        FROM StampbookContent targetContent
        JOIN targetContent.stampbook stampbook
        WHERE targetContent.content.contentId = :contentId
          AND stampbook.status = io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus.PUBLISHED
        ORDER BY stampbook.stampbookId ASC
        """)
    List<Stampbook> findPublishedByTargetContentIdForUpdate(@Param("contentId") Long contentId);

    @Query(value = "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))", nativeQuery = true)
    BigDecimal findCurrentEpochSeconds();

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT stampbook
        FROM Stampbook stampbook
        WHERE stampbook.stampbookId = :stampbookId
        """)
    Optional<Stampbook> findByStampbookIdForUpdate(@Param("stampbookId") Long stampbookId);

    @Query("""
        SELECT stampbook
        FROM Stampbook stampbook
        JOIN FETCH stampbook.region region
        JOIN FETCH stampbook.rewardCouponPolicy rewardCouponPolicy
        JOIN FETCH rewardCouponPolicy.region rewardCouponPolicyRegion
        WHERE stampbook.stampbookId = :stampbookId
          AND region.regionId = :regionId
          AND stampbook.status = :status
        """)
    Optional<Stampbook> findReviewDetailByStampbookIdAndRegionIdAndStatus(
        @Param("stampbookId") Long stampbookId,
        @Param("regionId") Long regionId,
        @Param("status") StampbookStatus status
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookReviewTargetContentProjection(
            content.contentId,
            contentRegion.regionId,
            content.title,
            content.status
        )
        FROM StampbookContent targetContent
        JOIN targetContent.content content
        JOIN content.region contentRegion
        WHERE targetContent.stampbook.stampbookId = :stampbookId
        ORDER BY content.contentId ASC
        """)
    List<StampbookReviewTargetContentProjection> findReviewTargetContentsByStampbookId(
        @Param("stampbookId") Long stampbookId
    );

    boolean existsByRewardCouponPolicyCouponPolicyIdAndStatus(
        Long couponPolicyId,
        StampbookStatus status
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.PendingRegionAdminStampbookProjection(
            stampbook.stampbookId,
            stampbook.region.regionId,
            stampbook.status,
            COUNT(DISTINCT targetContent.content.contentId),
            stampbook.rewardCouponPolicy.couponPolicyId,
            MAX(auditEvent.occurredAt)
        )
        FROM Stampbook stampbook
        LEFT JOIN StampbookContent targetContent ON targetContent.stampbook = stampbook
        LEFT JOIN AuditEvent auditEvent
            ON auditEvent.region = stampbook.region
            AND auditEvent.targetType = :targetType
            AND auditEvent.targetId = stampbook.stampbookId
            AND auditEvent.result = :auditResult
            AND auditEvent.previousState = :previousState
            AND auditEvent.nextState = :nextState
        WHERE stampbook.region.regionId = :regionId
          AND stampbook.status = :status
        GROUP BY stampbook.stampbookId,
                 stampbook.region.regionId,
                 stampbook.status,
                 stampbook.rewardCouponPolicy.couponPolicyId
        ORDER BY MAX(auditEvent.occurredAt) ASC, stampbook.stampbookId ASC
        """)
    List<PendingRegionAdminStampbookProjection> findPendingRegionAdminStampbookProjections(
        @Param("regionId") Long regionId,
        @Param("status") StampbookStatus status,
        @Param("targetType") AuditEventTargetType targetType,
        @Param("auditResult") AuditEventResult auditResult,
        @Param("previousState") String previousState,
        @Param("nextState") String nextState
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.OperatorStampbookListProjection(
            stampbook.stampbookId,
            stampbook.title,
            stampbook.region.regionId,
            stampbook.status,
            COUNT(DISTINCT targetContentEntity.contentId),
            MIN(targetContentRegion.regionId),
            MAX(targetContentRegion.regionId),
            rewardCouponPolicy.couponPolicyId,
            rewardCouponPolicyRegion.regionId,
            stampbook.publishedAt,
            stampbook.endedAt
        )
        FROM Stampbook stampbook
        LEFT JOIN StampbookContent targetContent ON targetContent.stampbook = stampbook
        LEFT JOIN targetContent.content targetContentEntity
        LEFT JOIN targetContentEntity.region targetContentRegion
        LEFT JOIN stampbook.rewardCouponPolicy rewardCouponPolicy
        LEFT JOIN rewardCouponPolicy.region rewardCouponPolicyRegion
        WHERE stampbook.region.regionId = :regionId
          AND NOT EXISTS (
              SELECT 1
              FROM StampbookContent ownedTargetContent
              JOIN ownedTargetContent.content ownedContent
              WHERE ownedTargetContent.stampbook = stampbook
                AND (
                    ownedContent.operator.userId IS NULL
                    OR ownedContent.operator.userId <> :operatorUserId
                )
          )
        GROUP BY stampbook.stampbookId,
                 stampbook.title,
                 stampbook.region.regionId,
                 stampbook.status,
                 stampbook.rewardCouponPolicy.couponPolicyId,
                 rewardCouponPolicyRegion.regionId,
                 stampbook.publishedAt,
                 stampbook.endedAt
        ORDER BY stampbook.stampbookId DESC
        """)
    List<OperatorStampbookListProjection> findOperatorStampbookListProjections(
        @Param("operatorUserId") Long operatorUserId,
        @Param("regionId") Long regionId
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection(
            stampbook.stampbookId,
            stampbook.title,
            stampbook.region.regionId,
            stampbook.status,
            stampbook.publishedAt,
            progress.stampbookProgressId,
            progress.user.userId,
            progress.status,
            progress.completedAt,
            COUNT(DISTINCT stampEarn.stampEarnId),
            COUNT(DISTINCT stampbookContent.content.contentId),
            MAX(stampEarn.earnedAt),
            rewardGrant.stampbookRewardGrantId,
            completionRewardCouponPolicy.couponPolicyId,
            stampbook.rewardCouponPolicy.couponPolicyId
        )
        FROM Stampbook stampbook
        LEFT JOIN StampbookProgress progress
            ON progress.stampbook = stampbook
            AND progress.user.userId = :userId
        LEFT JOIN StampEarn stampEarn ON stampEarn.stampbookProgress = progress
        LEFT JOIN StampbookContent stampbookContent ON stampbookContent.stampbook = stampbook
        LEFT JOIN StampbookRewardGrant rewardGrant ON rewardGrant.stampbookProgress = progress
        LEFT JOIN rewardGrant.couponPolicy completionRewardCouponPolicy
        WHERE stampbook.status = :publishedStatus
           OR stampbook.status = :endedStatus
            AND progress.stampbookProgressId IS NOT NULL
        GROUP BY stampbook.stampbookId,
            stampbook.title,
            stampbook.region.regionId,
            stampbook.status,
            stampbook.publishedAt,
            progress.stampbookProgressId,
            progress.user.userId,
            progress.status,
            progress.completedAt,
            rewardGrant.stampbookRewardGrantId,
            completionRewardCouponPolicy.couponPolicyId,
            stampbook.rewardCouponPolicy.couponPolicyId
        ORDER BY stampbook.publishedAt DESC, stampbook.stampbookId DESC
        """)
    List<MyStampbookListProjection> findMyStampbookListProjections(
        @Param("userId") Long userId,
        @Param("publishedStatus") StampbookStatus publishedStatus,
        @Param("endedStatus") StampbookStatus endedStatus
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookDetailProjection(
            stampbook.stampbookId,
            stampbook.title,
            stampbook.region.regionId,
            stampbook.status,
            stampbook.publishedAt,
            stampbook.endedAt,
            progress.stampbookProgressId,
            progress.user.userId,
            progress.status,
            progress.completedAt,
            stampbookContent.content.contentId,
            stampbookContent.content.title,
            stampEarn.earnedAt,
            rewardGrant.stampbookRewardGrantId,
            completionRewardCouponPolicy.couponPolicyId,
            stampbook.rewardCouponPolicy.couponPolicyId
        )
        FROM Stampbook stampbook
        LEFT JOIN StampbookProgress progress
            ON progress.stampbook = stampbook
            AND progress.user.userId = :userId
        LEFT JOIN StampbookContent stampbookContent ON stampbookContent.stampbook = stampbook
        LEFT JOIN StampEarn stampEarn
            ON stampEarn.stampbookProgress = progress
            AND stampEarn.content = stampbookContent.content
        LEFT JOIN StampbookRewardGrant rewardGrant ON rewardGrant.stampbookProgress = progress
        LEFT JOIN rewardGrant.couponPolicy completionRewardCouponPolicy
        WHERE stampbook.stampbookId = :stampbookId
          AND (
            stampbook.status = :publishedStatus
            OR (
                stampbook.status = :endedStatus
                AND progress.stampbookProgressId IS NOT NULL
            )
          )
        ORDER BY stampbookContent.content.contentId ASC
        """)
    List<MyStampbookDetailProjection> findMyStampbookDetailProjections(
        @Param("userId") Long userId,
        @Param("stampbookId") Long stampbookId,
        @Param("publishedStatus") StampbookStatus publishedStatus,
        @Param("endedStatus") StampbookStatus endedStatus
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampEarningProjection(
            stampbook.stampbookId,
            stampbook.status,
            progress.stampbookProgressId,
            progressUser.userId,
            stampEarn.stampEarnId,
            stampEarn.earnedAt,
            visit.visitId,
            visitUser.userId,
            visitContent.contentId,
            visit.checkedAt,
            content.contentId,
            content.title,
            targetContent.content.contentId
        )
        FROM Stampbook stampbook
        LEFT JOIN StampbookProgress progress
            ON progress.stampbook = stampbook
            AND progress.user.userId = :userId
        LEFT JOIN progress.user progressUser
        LEFT JOIN StampEarn stampEarn ON stampEarn.stampbookProgress = progress
        LEFT JOIN stampEarn.visit visit
        LEFT JOIN visit.user visitUser
        LEFT JOIN visit.content visitContent
        LEFT JOIN stampEarn.content content
        LEFT JOIN StampbookContent targetContent
            ON targetContent.stampbook = stampbook
            AND targetContent.content = content
        WHERE stampbook.stampbookId = :stampbookId
          AND (
            stampbook.status = :publishedStatus
            OR (
                stampbook.status = :endedStatus
                AND progress.stampbookProgressId IS NOT NULL
            )
          )
        ORDER BY stampEarn.earnedAt DESC, stampEarn.stampEarnId DESC
        """)
    List<MyStampEarningProjection> findMyStampEarningProjections(
        @Param("userId") Long userId,
        @Param("stampbookId") Long stampbookId,
        @Param("publishedStatus") StampbookStatus publishedStatus,
        @Param("endedStatus") StampbookStatus endedStatus
    );
}
