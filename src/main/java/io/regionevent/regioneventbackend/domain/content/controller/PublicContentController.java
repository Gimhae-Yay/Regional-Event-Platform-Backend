package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetPublicContentsResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentListResult;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentSearchCondition;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/contents")
public class PublicContentController {

    private static final String SUCCESS_MESSAGE = "공개 콘텐츠 목록 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetPublicContentsUseCase getPublicContentsUseCase;

    public PublicContentController(GetPublicContentsUseCase getPublicContentsUseCase) {
        this.getPublicContentsUseCase = getPublicContentsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetPublicContentsResponse>> getPublicContents(
        @RequestParam String regionId,
        @RequestParam(required = false) String contentType,
        @RequestParam(required = false) Boolean reservationAvailable
    ) {
        PublicContentListResult result = getPublicContentsUseCase.get(
            new PublicContentSearchCondition(
                toRegionId(regionId),
                toContentType(contentType),
                reservationAvailable
            )
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPublicContentsResponse.from(result)
        ).toResponseEntity();
    }

    private Long toRegionId(String value) {
        Long regionId;
        try {
            regionId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return regionId;
    }

    private ContentType toContentType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ContentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
