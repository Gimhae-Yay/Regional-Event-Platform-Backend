package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReservationParticipantMaskerTest {

    private final ReservationParticipantMasker masker = new ReservationParticipantMasker();

    @Test
    void mask_whenParticipantIsLinked_returnsMaskedNameAndPhone() {
        ReservationReadSnapshot.ParticipantInfo participant = new ReservationReadSnapshot.ParticipantInfo(
            1L,
            "김민수",
            "010-1234-5678"
        );

        ReservationParticipantMasker.MaskedParticipant maskedParticipant = masker.mask(participant);

        assertThat(maskedParticipant).isEqualTo(
            new ReservationParticipantMasker.MaskedParticipant("김*수", "010-****-5678")
        );
    }

    @Test
    void mask_whenParticipantIsWithdrawn_returnsWithdrawnDisplayAndNullPhone() {
        ReservationReadSnapshot.ParticipantInfo participant = new ReservationReadSnapshot.ParticipantInfo(
            null,
            null,
            null
        );

        ReservationParticipantMasker.MaskedParticipant maskedParticipant = masker.mask(participant);

        assertThat(maskedParticipant).isEqualTo(
            new ReservationParticipantMasker.MaskedParticipant("탈퇴한 사용자", null)
        );
    }
}
