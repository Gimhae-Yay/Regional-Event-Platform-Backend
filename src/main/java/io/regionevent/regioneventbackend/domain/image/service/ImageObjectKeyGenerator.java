package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class ImageObjectKeyGenerator {

    public String generate(String mediaType, Instant now) {
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return "contents/%04d/%02d/%02d/%s.%s".formatted(
            date.getYear(),
            date.getMonthValue(),
            date.getDayOfMonth(),
            UUID.randomUUID(),
            extension(mediaType)
        );
    }

    private static String extension(String mediaType) {
        return switch (mediaType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("unsupported media type");
        };
    }
}
