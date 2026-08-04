package io.regionevent.regioneventbackend.domain.operator.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class OperatorApplicationService {

    private final OperatorApplicationRepository operatorApplicationRepository;

    public OperatorApplicationService(OperatorApplicationRepository operatorApplicationRepository) {
        this.operatorApplicationRepository = operatorApplicationRepository;
    }

    public OperatorApplication createPendingApplication(
        AppUser user,
        Region region,
        String businessInformation
    ) {
        return operatorApplicationRepository.save(new OperatorApplication(
            user,
            region,
            businessInformation,
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
    }

    public boolean hasPendingApplication(AppUser user) {
        return operatorApplicationRepository.existsByApplicantAndStatus(user, OperatorApplicationStatus.PENDING);
    }

    public boolean hasRejectedApplication(AppUser user) {
        return operatorApplicationRepository.existsByApplicantAndStatus(user, OperatorApplicationStatus.REJECTED);
    }

    public List<OperatorApplication> findPendingApplications(Long regionId) {
        return operatorApplicationRepository.findByRequestedRegionRegionIdAndStatusOrderByCreatedAtAscOperatorApplicationIdAsc(
            regionId,
            OperatorApplicationStatus.PENDING
        );
    }

    public OperatorApplication findDetail(Long operatorApplicationId, Long regionId) {
        return operatorApplicationRepository.findDetailByOperatorApplicationIdAndRequestedRegionId(
            operatorApplicationId,
            regionId
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public OperatorApplicationStatus findReviewStatus(Long operatorApplicationId, Long regionId) {
        return operatorApplicationRepository.findStatusByOperatorApplicationIdAndRequestedRegionId(
            operatorApplicationId,
            regionId
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Long findReviewApplicantUserId(Long operatorApplicationId, Long regionId) {
        return operatorApplicationRepository.findApplicantUserIdByOperatorApplicationIdAndRequestedRegionId(
            operatorApplicationId,
            regionId
        ).orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT));
    }

    public OperatorApplication findReviewTargetForUpdate(Long operatorApplicationId, Long regionId) {
        return operatorApplicationRepository.findByOperatorApplicationIdAndRequestedRegionIdForUpdate(
            operatorApplicationId,
            regionId
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

}
