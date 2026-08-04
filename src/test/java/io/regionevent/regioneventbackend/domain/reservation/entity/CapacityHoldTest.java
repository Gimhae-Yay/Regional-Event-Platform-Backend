package io.regionevent.regioneventbackend.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class CapacityHoldTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-02T00:10:00Z");
    private static final Instant TERMINAL_AT = Instant.parse("2026-08-02T00:05:00Z");

    private final Region region = new Region("GIMHAE", "Gimhae", true);
    private final AppUser operator = createUser("operator@example.com");
    private final AppUser visitor = createUser("visitor@example.com");
    private final Content content = createContent();
    private final ContentSession contentSession = createContentSession();

    @Test
    void invalidate_whenActive_recordsTerminalFields() {
        CapacityHold capacityHold = createCapacityHold(CapacityHoldStatus.ACTIVE);

        capacityHold.invalidate("Session cancelled", TERMINAL_AT);

        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(capacityHold.getTerminalAt()).isEqualTo(TERMINAL_AT);
        assertThat(capacityHold.getCapacityReleasedAt()).isEqualTo(TERMINAL_AT);
        assertThat(capacityHold.getInvalidationReason()).isEqualTo("Session cancelled");
    }

    @Test
    void invalidate_whenStatusOrReasonIsInvalid_throwsException() {
        CapacityHold consumedHold = createCapacityHold(CapacityHoldStatus.CONSUMED);
        CapacityHold activeHold = createCapacityHold(CapacityHoldStatus.ACTIVE);

        assertThatThrownBy(
            () -> consumedHold.invalidate("Session cancelled", TERMINAL_AT)
        ).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
            () -> activeHold.invalidate(" ", TERMINAL_AT)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 생성_시_수량과_상태별_종결_필드와_무효화_사유를_검증한다() {
        assertThatThrownBy(() -> newCapacityHold(
            region,
            0,
            CapacityHoldStatus.ACTIVE,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newCapacityHold(
            region,
            1,
            CapacityHoldStatus.INVALIDATED,
            TERMINAL_AT,
            TERMINAL_AT,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newCapacityHold(
            region,
            1,
            CapacityHoldStatus.INVALIDATED,
            TERMINAL_AT,
            TERMINAL_AT,
            " "
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newCapacityHold(
            region,
            1,
            CapacityHoldStatus.EXPIRED,
            TERMINAL_AT,
            TERMINAL_AT,
            "회차가 취소되었습니다."
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newCapacityHold(
            region,
            1,
            CapacityHoldStatus.CONSUMED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newCapacityHold(
            region,
            1,
            CapacityHoldStatus.EXPIRED,
            TERMINAL_AT,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 생성_시_홀드_지역과_회차_지역이_일치해야_한다() {
        Region busan = new Region("BUSAN", "Busan", true);
        assignId(region, "regionId", 1L);
        assignId(busan, "regionId", 2L);

        assertThatThrownBy(() -> newCapacityHold(
            busan,
            1,
            CapacityHoldStatus.ACTIVE,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private CapacityHold createCapacityHold(CapacityHoldStatus status) {
        Instant terminalAt = status == CapacityHoldStatus.CONSUMED ? TERMINAL_AT : null;
        return newCapacityHold(region, 2, status, terminalAt, null, null);
    }

    private CapacityHold newCapacityHold(
        Region holdRegion,
        int quantity,
        CapacityHoldStatus status,
        Instant terminalAt,
        Instant capacityReleasedAt,
        String invalidationReason
    ) {
        return new CapacityHold(
            holdRegion,
            contentSession,
            visitor,
            quantity,
            status,
            EXPIRES_AT,
            terminalAt,
            invalidationReason,
            capacityReleasedAt
        );
    }

    private ContentSession createContentSession() {
        return new ContentSession(
            content,
            region,
            Instant.parse("2026-08-03T01:00:00Z"),
            Instant.parse("2026-08-03T03:00:00Z"),
            Instant.parse("2026-08-03T00:30:00Z"),
            Instant.parse("2026-08-03T02:30:00Z"),
            20
        );
    }

    private Content createContent() {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "Original title",
            "Original description",
            "Original location",
            "Original hours",
            "055-000-0000",
            "Original precautions",
            "Original age",
            "Original materials",
            "Original policy",
            Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    private AppUser createUser(String loginIdentifier) {
        return new AppUser(
            loginIdentifier,
            "hashed-password",
            "User",
            "01012345678",
            AppUserStatus.ACTIVE
        );
    }

    private void assignId(Object entity, String fieldName, long identifier) {
        ReflectionTestUtils.setField(entity, fieldName, identifier);
    }
}
