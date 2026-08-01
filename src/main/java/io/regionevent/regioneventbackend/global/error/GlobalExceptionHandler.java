package io.regionevent.regioneventbackend.global.error;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.databind.exc.MismatchedInputException;

import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStoreUnavailableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ApiResponse.fail(exception.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(RefreshTokenStoreUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefreshTokenStoreUnavailable(
        RefreshTokenStoreUnavailableException exception
    ) {
        return ApiResponse.fail(ErrorCode.AUTH_SERVICE_UNAVAILABLE).toResponseEntity();
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingServletRequestParameterException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception exception) {
        return ApiResponse.fail(ErrorCode.INVALID_INPUT).toResponseEntity();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof MismatchedInputException mismatchedInputException
            && !mismatchedInputException.getPath().isEmpty()) {
            return ApiResponse.fail(ErrorCode.INVALID_TYPE).toResponseEntity();
        }
        return ApiResponse.fail(ErrorCode.INVALID_JSON).toResponseEntity();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidType(MethodArgumentTypeMismatchException exception) {
        return ApiResponse.fail(ErrorCode.INVALID_TYPE).toResponseEntity();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED).toResponseEntity();
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception exception) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND).toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception. requestId={}", RequestIdFilter.currentRequestId(), exception);
        return ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR).toResponseEntity();
    }
}
