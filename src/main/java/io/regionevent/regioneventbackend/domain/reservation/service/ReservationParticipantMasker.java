package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class ReservationParticipantMasker {

    private static final String WITHDRAWN_PARTICIPANT_NAME = "탈퇴한 사용자";

    public MaskedParticipant mask(ReservationReadSnapshot.ParticipantInfo participant) {
        Objects.requireNonNull(participant, "participant must not be null");
        if (participant.userId() == null) {
            return MaskedParticipant.withdrawn();
        }
        return new MaskedParticipant(maskName(participant.name()), maskPhone(participant.phone()));
    }

    private String maskName(String name) {
        validateNonBlank(name, "name");

        int codePointCount = name.codePointCount(0, name.length());
        if (codePointCount == 1) {
            return "*";
        }
        int firstEndIndex = name.offsetByCodePoints(0, 1);
        if (codePointCount == 2) {
            return name.substring(0, firstEndIndex) + "*";
        }
        int lastStartIndex = name.offsetByCodePoints(0, codePointCount - 1);
        return name.substring(0, firstEndIndex) + "*" + name.substring(lastStartIndex);
    }

    private String maskPhone(String phone) {
        validateNonBlank(phone, "phone");

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            throw new IllegalStateException("phone must contain at least seven digits");
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    private void validateNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be null or blank");
        }
    }

    public record MaskedParticipant(
        String name,
        String phone
    ) {

        private static MaskedParticipant withdrawn() {
            return new MaskedParticipant(WITHDRAWN_PARTICIPANT_NAME, null);
        }
    }
}
