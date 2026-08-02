package io.regionevent.regioneventbackend.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.MismatchedInputException;

public record CreateVisitReviewRequest(
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    @NotNull
    @Min(1)
    @Max(5)
    Integer rating,
    @JsonDeserialize(using = StringValueDeserializer.class)
    @NotBlank
    @Size(max = 2_000)
    String reviewText
) {

    public CreateVisitReviewRequest {
        if (reviewText != null) {
            reviewText = reviewText.strip();
        }
    }

    public static class IntegerValueDeserializer extends ValueDeserializer<Integer> {

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw MismatchedInputException.from(parser, Integer.class, "rating must be an integer");
            }
            return parser.getIntValue();
        }
    }

    public static class StringValueDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                throw MismatchedInputException.from(parser, String.class, "reviewText must be a string");
            }
            return parser.getText();
        }
    }
}
