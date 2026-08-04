package io.regionevent.regioneventbackend.domain.visit.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Component
public class QrExceptionCursorCodec {

    private static final String DELIMITER = "\\|";
    private static final String DELIMITER_VALUE = "|";
    private static final int CURSOR_PART_COUNT = 3;

    public String encode(Cursor cursor) {
        String plainCursor = cursor.regionId()
            + DELIMITER_VALUE
            + cursor.occurredAt()
            + DELIMITER_VALUE
            + cursor.auditEventId();
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(plainCursor.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(
        String encodedCursor,
        Long authenticatedRegionId
    ) {
        try {
            String plainCursor = new String(
                Base64.getUrlDecoder().decode(encodedCursor),
                StandardCharsets.UTF_8
            );
            String[] parts = plainCursor.split(DELIMITER, -1);
            if (parts.length != CURSOR_PART_COUNT) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            Cursor cursor = new Cursor(
                Long.valueOf(parts[0]),
                Instant.parse(parts[1]),
                Long.valueOf(parts[2])
            );
            if (!cursor.regionId().equals(authenticatedRegionId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return cursor;
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public record Cursor(
        Long regionId,
        Instant occurredAt,
        Long auditEventId
    ) {
    }
}
