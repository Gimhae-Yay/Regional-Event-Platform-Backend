package io.regionevent.regioneventbackend.domain.operator.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public interface OperatorApplicationRepository extends JpaRepository<OperatorApplication, Long> {

    boolean existsByApplicantAndStatus(AppUser applicant, OperatorApplicationStatus status);

    @EntityGraph(attributePaths = {"requestedRegion", "applicant"})
    List<OperatorApplication> findByRequestedRegionRegionIdAndStatusOrderByCreatedAtAscOperatorApplicationIdAsc(
        Long regionId,
        OperatorApplicationStatus status
    );

    @EntityGraph(attributePaths = {"requestedRegion", "applicant", "inspectedUser"})
    @Query("""
        select application
        from OperatorApplication application
        where application.operatorApplicationId = :operatorApplicationId
          and application.requestedRegion.regionId = :regionId
        """)
    Optional<OperatorApplication> findDetailByOperatorApplicationIdAndRequestedRegionId(
        Long operatorApplicationId,
        Long regionId
    );

    @Query("""
        select application.status
        from OperatorApplication application
        where application.operatorApplicationId = :operatorApplicationId
          and application.requestedRegion.regionId = :regionId
        """)
    Optional<OperatorApplicationStatus> findStatusByOperatorApplicationIdAndRequestedRegionId(
        Long operatorApplicationId,
        Long regionId
    );

    @Query("""
        select application.applicant.userId
        from OperatorApplication application
        where application.operatorApplicationId = :operatorApplicationId
          and application.requestedRegion.regionId = :regionId
        """)
    Optional<Long> findApplicantUserIdByOperatorApplicationIdAndRequestedRegionId(
        Long operatorApplicationId,
        Long regionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select application
        from OperatorApplication application
        left join fetch application.applicant
        where application.operatorApplicationId = :operatorApplicationId
          and application.requestedRegion.regionId = :regionId
        """)
    Optional<OperatorApplication> findByOperatorApplicationIdAndRequestedRegionIdForUpdate(
        Long operatorApplicationId,
        Long regionId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE operator_application
        SET status = CASE WHEN status = 'PENDING' THEN 'CANCELLED' ELSE status END,
            applicant_user_id = NULL,
            business_information = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE applicant_user_id = :userId
        """, nativeQuery = true)
    int cancelAndUnlinkByApplicantUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE operator_application
        SET inspected_user_id = NULL
        WHERE inspected_user_id = :userId
          AND status IN ('APPROVED', 'REJECTED')
        """, nativeQuery = true)
    int unlinkInspectorByUserId(@Param("userId") Long userId);
}
