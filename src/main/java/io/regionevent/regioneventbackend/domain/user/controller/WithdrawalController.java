package io.regionevent.regioneventbackend.domain.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.service.WithdrawUserUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenCookie;

@RestController
@RequestMapping("/api/v1/auth")
public class WithdrawalController {

    private static final String WITHDRAWAL_SUCCESS_MESSAGE = "회원탈퇴에 성공했습니다.";

    private final WithdrawUserUseCase withdrawUserUseCase;

    public WithdrawalController(WithdrawUserUseCase withdrawUserUseCase) {
        this.withdrawUserUseCase = withdrawUserUseCase;
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long userId) {
        withdrawUserUseCase.withdraw(userId);
        return ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.expire())
            .body(ApiResponse.success(HttpStatus.OK, WITHDRAWAL_SUCCESS_MESSAGE));
    }
}
