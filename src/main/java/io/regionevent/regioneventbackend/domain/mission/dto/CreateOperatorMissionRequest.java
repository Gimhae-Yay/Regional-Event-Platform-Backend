package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.UniqueElements;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.MismatchedInputException;

public record CreateOperatorMissionRequest(
    @JsonDeserialize(using = StringValueDeserializer.class)
    String title,

    @JsonDeserialize(using = StringValueDeserializer.class)
    @NotBlank
    String conditionType,

    @JsonDeserialize(using = IntegerValueDeserializer.class)
    Integer requiredVisitCount,

    @JsonDeserialize(contentUsing = StringValueDeserializer.class)
    @UniqueElements
    List<
        @NotBlank
        @Pattern(regexp = "^[1-9][0-9]*$")
        String
    > targetContentIds,

    @JsonDeserialize(using = StringValueDeserializer.class)
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    String rewardCouponPolicyId,

    @JsonDeserialize(using = OffsetDateTimeValueDeserializer.class)
    @NotNull
    OffsetDateTime endsAt
) {

    public static class IntegerValueDeserializer extends ValueDeserializer<Integer> {

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw MismatchedInputException.from(parser, Integer.class, "requiredVisitCount must be an integer");
            }
            return parser.getIntValue();
        }
    }

    public static class StringValueDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                throw MismatchedInputException.from(parser, String.class, "request value must be a string");
            }
            return parser.getText();
        }
    }

    public static class OffsetDateTimeValueDeserializer extends ValueDeserializer<OffsetDateTime> {

        @Override
        public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                throw MismatchedInputException.from(parser, OffsetDateTime.class, "endsAt must be a string");
            }

            try {
                return OffsetDateTime.parse(parser.getText());
            } catch (DateTimeException exception) {
                throw new StreamReadException(parser, "endsAt must be an ISO 8601 offset date-time", exception);
            }
        }
    }
}
