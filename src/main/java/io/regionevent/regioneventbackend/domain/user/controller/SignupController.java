package io.regionevent.regioneventbackend.domain.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.dto.SignupRequest;
import io.regionevent.regioneventbackend.domain.user.dto.SignupResponse;
import io.regionevent.regioneventbackend.domain.user.service.SignupUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class SignupController {

    private static final String SIGNUP_SUCCESS_MESSAGE = "회원가입에 성공했습니다.";

    private final SignupUseCase signupUseCase;

    public SignupController(SignupUseCase signupUseCase) {
        this.signupUseCase = signupUseCase;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupUseCase.signup(request);
        return ApiResponse.success(HttpStatus.CREATED, SIGNUP_SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
