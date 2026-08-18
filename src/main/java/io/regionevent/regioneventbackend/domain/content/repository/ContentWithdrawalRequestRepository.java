package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;

public interface ContentWithdrawalRequestRepository
    extends JpaRepository<ContentWithdrawalRequest, Long> {

    @Query("""
        SELECT request
        FROM ContentWithdrawalRequest request
        LEFT JOIN FETCH request.content content
        LEFT JOIN FETCH content.region
        LEFT JOIN FETCH request.requestedBy
        WHERE request.contentWithdrawalRequestId = :withdrawalRequestId
        """)
    Optional<ContentWithdrawalRequest> findReviewDetailById(
        @Param("withdrawalRequestId") Long withdrawalRequestId
    );

    @Query("""
        SELECT request.content.contentId
        FROM ContentWithdrawalRequest request
        WHERE request.contentWithdrawalRequestId = :withdrawalRequestId
        """)
    Optional<Long> findContentIdByWithdrawalRequestId(
        @Param("withdrawalRequestId") Long withdrawalRequestId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM ContentWithdrawalRequest request
        WHERE request.contentWithdrawalRequestId = :withdrawalRequestId
        """)
    Optional<ContentWithdrawalRequest> findReviewTargetForUpdate(
        @Param("withdrawalRequestId") Long withdrawalRequestId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM ContentWithdrawalRequest request
        WHERE request.content.contentId = :contentId
            AND request.idempotencyKeyHash = :idempotencyKeyHash
        """)
    Optional<ContentWithdrawalRequest> findByContentIdAndIdempotencyKeyHashForUpdate(
        @Param("contentId") Long contentId,
        @Param("idempotencyKeyHash") String idempotencyKeyHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM ContentWithdrawalRequest request
        WHERE request.content.contentId = :contentId
            AND request.status = :status
        """)
    Optional<ContentWithdrawalRequest> findByContentIdAndStatusForUpdate(
        @Param("contentId") Long contentId,
        @Param("status") ContentWithdrawalRequestStatus status
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE content_withdrawal_request
        SET requested_by_user_id = NULL
        WHERE requested_by_user_id = :userId
        """, nativeQuery = true)
    int unlinkRequesterByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE content_withdrawal_request
        SET reviewed_by_user_id = NULL
        WHERE reviewed_by_user_id = :userId
        """, nativeQuery = true)
    int unlinkReviewerByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE content_withdrawal_request
        SET invalidated_by_user_id = NULL
        WHERE invalidated_by_user_id = :userId
        """, nativeQuery = true)
    int unlinkInvalidatorByUserId(@Param("userId") Long userId);
}
