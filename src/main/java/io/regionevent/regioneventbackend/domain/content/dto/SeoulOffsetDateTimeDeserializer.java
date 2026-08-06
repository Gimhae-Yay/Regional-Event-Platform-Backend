package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class SeoulOffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

    private static final Pattern SEOUL_OFFSET_DATE_TIME_PATTERN = Pattern.compile(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?\\+09:00$"
    );

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        String value = parser.getValueAsString();
        if (value == null || !SEOUL_OFFSET_DATE_TIME_PATTERN.matcher(value).matches()) {
            throw context.weirdStringException(
                value,
                OffsetDateTime.class,
                "Asia/Seoul ISO 8601 오프셋 일시여야 합니다."
            );
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeException exception) {
            throw context.weirdStringException(
                value,
                OffsetDateTime.class,
                "유효한 일정 시각이어야 합니다."
            );
        }
    }
}
