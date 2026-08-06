package io.regionevent.regioneventbackend.domain.visit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.QrExceptionReadService;
import io.regionevent.regioneventbackend.domain.audit.service.QrExceptionReadService.QrExceptionItem;
import io.regionevent.regioneventbackend.domain.audit.service.QrExceptionReadService.QrExceptionPage;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.visit.dto.GetQrExceptionsResponse;
import io.regionevent.regioneventbackend.domain.visit.service.QrExceptionCursorCodec.Cursor;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetQrExceptionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetQrExceptionsUseCase.class);
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";
    private static final int FAILURE_RESULT_COUNT = 0;

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final QrExceptionReadService qrExceptionReadService;
    private final QrExceptionCursorCodec qrExceptionCursorCodec;

    public GetQrExceptionsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        QrExceptionReadService qrExceptionReadService,
        QrExceptionCursorCodec qrExceptionCursorCodec
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.qrExceptionReadService = qrExceptionReadService;
        this.qrExceptionCursorCodec = qrExceptionCursorCodec;
    }

    @Transactional(readOnly = true)
    public GetQrExceptionsResponse get(
        Long userId,
        String encodedCursor,
        int size
    ) {
        Long regionId = null;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
            Cursor cursor = decodeCursor(encodedCursor, regionId);
            QrExceptionPage page = qrExceptionReadService.findAll(
                regionId,
                cursor == null ? null : cursor.occurredAt(),
                cursor == null ? null : cursor.auditEventId(),
                size
            );
            GetQrExceptionsResponse response = GetQrExceptionsResponse.from(page, encodeNextCursor(page, regionId));
            logResult(regionId, response.exceptions().size(), SUCCESS_RESULT_CODE);
            return response;
        } catch (BusinessException exception) {
            logResult(regionId, FAILURE_RESULT_COUNT, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, FAILURE_RESULT_COUNT, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private Cursor decodeCursor(
        String encodedCursor,
        Long regionId
    ) {
        if (encodedCursor == null) {
            return null;
        }
        if (encodedCursor.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return qrExceptionCursorCodec.decode(encodedCursor, regionId);
    }

    private String encodeNextCursor(
        QrExceptionPage page,
        Long regionId
    ) {
        if (!page.hasNext() || page.items().isEmpty()) {
            return null;
        }
        QrExceptionItem lastItem = page.items().get(page.items().size() - 1);
        return qrExceptionCursorCodec.encode(new Cursor(
            regionId,
            lastItem.occurredAt(),
            lastItem.exceptionId()
        ));
    }

    private void logResult(
        Long regionId,
        int resultCount,
        String resultCode
    ) {
        log.info(
            "QR exception list queried. requestId={}, regionId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            resultCount,
            resultCode
        );
    }
}
