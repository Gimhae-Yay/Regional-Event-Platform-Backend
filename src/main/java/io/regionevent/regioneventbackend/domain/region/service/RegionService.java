package io.regionevent.regioneventbackend.domain.region.service;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.repository.PublicRegionVerificationProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RegionService {

    private static final String REGION_CODE_UNIQUE_CONSTRAINT = "uk_region_region_code";

    private final RegionRepository regionRepository;

    public RegionService(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    public Region findPublicRegion(Long regionId) {
        return regionRepository.findByRegionIdAndIsPublicTrue(regionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Region findRegionForUpdate(Long regionId) {
        return regionRepository.findByRegionId(regionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Region createPrivateRegion(
        String regionCode,
        String name
    ) {
        if (regionRepository.existsByRegionCode(regionCode)) {
            throw new BusinessException(ErrorCode.REGION_CODE_ALREADY_EXISTS);
        }

        try {
            return regionRepository.saveAndFlush(Region.createPrivate(regionCode, name));
        } catch (DataIntegrityViolationException exception) {
            if (isRegionCodeUniqueConstraintViolation(exception)) {
                throw new BusinessException(ErrorCode.REGION_CODE_ALREADY_EXISTS, exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<PublicRegionVerificationProjection> findPublicRegionVerifications() {
        return regionRepository.findPublicRegionVerifications();
    }

    public PublicRegionStaticInfo findPublicRegionStaticInfo(Long regionId) {
        return regionRepository.findPublicRegionStaticInfo(regionId)
            .map(PublicRegionStaticInfo::from)
            .orElseThrow(() -> new IllegalStateException("public region static info must exist after verification"));
    }

    private boolean isRegionCodeUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                && isRegionCodeUniqueConstraint(constraintViolationException.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isRegionCodeUniqueConstraint(String constraintName) {
        return REGION_CODE_UNIQUE_CONSTRAINT.equals(constraintName)
            || constraintName != null
                && constraintName.endsWith("." + REGION_CODE_UNIQUE_CONSTRAINT);
    }
}
