package io.regionevent.regioneventbackend.domain.image.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthority;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorityService;

@Service
public class CreatePresignedImageUploadUseCase {

    private final OperatorAuthorityService operatorAuthorityService;
    private final PresignedImageUploadService presignedImageUploadService;

    public CreatePresignedImageUploadUseCase(
        OperatorAuthorityService operatorAuthorityService,
        PresignedImageUploadService presignedImageUploadService
    ) {
        this.operatorAuthorityService = operatorAuthorityService;
        this.presignedImageUploadService = presignedImageUploadService;
    }

    @Transactional
    public PresignedImageUploadResult createUpload(Long userId, PresignedImageUploadCommand command) {
        OperatorAuthority operatorAuthority = operatorAuthorityService.findActiveOperatorAuthority(userId);
        return presignedImageUploadService.createUpload(
            operatorAuthority.appUser(),
            operatorAuthority.region(),
            command
        );
    }
}
