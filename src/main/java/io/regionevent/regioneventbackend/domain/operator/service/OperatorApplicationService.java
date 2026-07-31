package io.regionevent.regioneventbackend.domain.operator.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class OperatorApplicationService {

    private final OperatorApplicationRepository operatorApplicationRepository;

    public OperatorApplicationService(OperatorApplicationRepository operatorApplicationRepository) {
        this.operatorApplicationRepository = operatorApplicationRepository;
    }

    public void createPendingApplication(
        AppUser user,
        Region region,
        String businessInformation
    ) {
        operatorApplicationRepository.save(new OperatorApplication(
            user,
            region,
            businessInformation,
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
    }
}
