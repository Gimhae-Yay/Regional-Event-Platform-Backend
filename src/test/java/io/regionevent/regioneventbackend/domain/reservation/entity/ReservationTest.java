package io.regionevent.regioneventbackend.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class ReservationTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-08-02T00:05:00Z");

    private final Region region = new Region("GIMHAE", "Gimhae", true);
    private final AppUser operator = createUser("operator@example.com");
    private final AppUser visitor = createUser("visitor@example.com");
    private final Content content = createContent();
    private final ContentSession contentSession = createContentSession();

    @Test
    void cancel_whenConfirmed_recordsCancellationFields() {
        Reservation reservation = createReservation(ReservationStatus.CONFIRMED);

        reservation.cancel("Session cancelled", CANCELLED_AT, CANCELLED_AT);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(reservation.getCancellationReason()).isEqualTo("Session cancelled");
        assertThat(reservation.getCapacityReleasedAt()).isEqualTo(CANCELLED_AT);
    }

    @Test
    void cancel_whenStatusOrReasonIsInvalid_throwsException() {
        Reservation checkedInReservation = createReservation(ReservationStatus.CHECKED_IN);
        Reservation confirmedReservation = createReservation(ReservationStatus.CONFIRMED);

        assertThatThrownBy(
            () -> checkedInReservation.cancel("Session cancelled", CANCELLED_AT, null)
        ).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
            () -> confirmedReservation.cancel(" ", CANCELLED_AT, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private Reservation createReservation(ReservationStatus status) {
        return new Reservation(
            "R-20260802-" + status.name(),
            "qr-reference-" + status.name(),
            region,
            createConsumedHold(),
            contentSession,
            visitor,
            status,
            CONFIRMED_AT,
            null,
            null,
            null,
            null
        );
    }

    private CapacityHold createConsumedHold() {
        return new CapacityHold(
            region,
            contentSession,
            visitor,
            2,
            CapacityHoldStatus.CONSUMED,
            CONFIRMED_AT,
            CONFIRMED_AT,
            null,
            null
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
}
