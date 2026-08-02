package io.regionevent.regioneventbackend.domain.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.dto.MyRoleAssignmentsResponse;
import io.regionevent.regioneventbackend.domain.user.service.GetMyRoleAssignmentsUseCase;
import io.regionevent.regioneventbackend.domain.user.service.MyRoleAssignmentsResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1")
public class MyRoleController {

    private static final String SUCCESS_MESSAGE = "내 역할과 담당 지역 조회에 성공했습니다.";

    private final GetMyRoleAssignmentsUseCase getMyRoleAssignmentsUseCase;

    public MyRoleController(GetMyRoleAssignmentsUseCase getMyRoleAssignmentsUseCase) {
        this.getMyRoleAssignmentsUseCase = getMyRoleAssignmentsUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyRoleAssignmentsResponse>> getMyRoles(Authentication authentication) {
        Long userId = toAuthenticatedUserId(authentication);
        MyRoleAssignmentsResult result = getMyRoleAssignmentsUseCase.get(userId);
        MyRoleAssignmentsResponse response = MyRoleAssignmentsResponse.from(result);
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
