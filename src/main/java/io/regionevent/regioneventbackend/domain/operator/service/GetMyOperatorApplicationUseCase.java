package io.regionevent.regioneventbackend.domain.operator.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.dto.MyOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyOperatorApplicationUseCase {

    private final AppUserService appUserService;
    private final OperatorApplicationService operatorApplicationService;

    public GetMyOperatorApplicationUseCase(
        AppUserService appUserService,
        OperatorApplicationService operatorApplicationService
    ) {
        this.appUserService = appUserService;
        this.operatorApplicationService = operatorApplicationService;
    }

    @Transactional(readOnly = true)
    public MyOperatorApplicationResponse get(Long userId) {
        AppUser applicant = appUserService.findActiveUser(userId);
        Optional<OperatorApplication> application = operatorApplicationService.findLatestApplication(applicant);
        return application
            .map(MyOperatorApplicationResponse::from)
            .orElseGet(MyOperatorApplicationResponse::empty);
    }
}
