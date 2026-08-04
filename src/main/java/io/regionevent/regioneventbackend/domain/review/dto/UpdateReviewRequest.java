package io.regionevent.regioneventbackend.domain.review.dto;

import jakarta.validation.constraints.AssertTrue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.MismatchedInputException;

public class UpdateReviewRequest {

    private Integer rating;
    private String reviewText;
    private boolean ratingProvided;
    private boolean reviewTextProvided;

    @JsonDeserialize(using = IntegerValueDeserializer.class)
    public void setRating(Integer rating) {
        this.rating = rating;
        ratingProvided = true;
    }

    @JsonDeserialize(using = StringValueDeserializer.class)
    public void setReviewText(String reviewText) {
        this.reviewText = reviewText == null ? null : reviewText.strip();
        reviewTextProvided = true;
    }

    public Integer rating() {
        return rating;
    }

    public String reviewText() {
        return reviewText;
    }

    @AssertTrue
    public boolean hasValidUpdateFields() {
        return (ratingProvided || reviewTextProvided)
            && (!ratingProvided || rating != null && rating >= 1 && rating <= 5)
            && (!reviewTextProvided || reviewText != null && !reviewText.isBlank() && reviewText.length() <= 2_000);
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
