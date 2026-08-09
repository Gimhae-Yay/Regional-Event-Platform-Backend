package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.platformadmin.dto.CreateAdminAccountRequest;
import io.regionevent.regioneventbackend.domain.platformadmin.dto.CreateAdminAccountResponse;
import io.regionevent.regioneventbackend.domain.platformadmin.service.CreateAdminAccountResult;
import io.regionevent.regioneventbackend.domain.platformadmin.service.CreateAdminAccountUseCase;
import io.regionevent.regioneventbackend.domain.platformadmin.service.CreateAdminAccountUseCase.CreateAdminAccountCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/admin-accounts")
public class CreateAdminAccountController {

    private static final String SUCCESS_MESSAGE = "전체관리자 계정 생성에 성공했습니다.";

    private final CreateAdminAccountUseCase createAdminAccountUseCase;

    public CreateAdminAccountController(CreateAdminAccountUseCase createAdminAccountUseCase) {
        this.createAdminAccountUseCase = createAdminAccountUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateAdminAccountResponse>> createAdminAccount(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateAdminAccountRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateAdminAccountResult result = createAdminAccountUseCase.create(
            userId,
            new CreateAdminAccountCommand(
                request.email(),
                request.password(),
                request.name(),
                request.phone(),
                request.grade(),
                request.reasonCode(),
                request.evidenceReference()
            ),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            CreateAdminAccountResponse.from(result)
        ).toResponseEntity();
    }
}
