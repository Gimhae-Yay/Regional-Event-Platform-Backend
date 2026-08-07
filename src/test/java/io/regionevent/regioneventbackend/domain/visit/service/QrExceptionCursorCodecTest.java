package io.regionevent.regioneventbackend.domain.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.visit.service.QrExceptionCursorCodec.Cursor;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class QrExceptionCursorCodecTest {

    private final QrExceptionCursorCodec codec = new QrExceptionCursorCodec();

    @Test
    void decode_인코딩한_커서를_원래_값으로_복원한다() {
        Cursor cursor = new Cursor(1L, Instant.parse("2026-08-01T01:02:03Z"), 10L);

        Cursor decoded = codec.decode(codec.encode(cursor), 1L);

        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    void decode_형식이_잘못된_커서는_INVALID_INPUT으로_거부한다() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor", 1L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }

    @Test
    void decode_담당_지역과_다른_커서는_INVALID_INPUT으로_거부한다() {
        String cursor = codec.encode(new Cursor(2L, Instant.parse("2026-08-01T01:02:03Z"), 10L));

        assertThatThrownBy(() -> codec.decode(cursor, 1L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }
}
