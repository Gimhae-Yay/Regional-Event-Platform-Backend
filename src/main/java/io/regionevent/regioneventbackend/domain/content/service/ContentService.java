package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ContentService {

    private final ContentRepository contentRepository;

    public ContentService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    public Content createPendingContent(
        Region region,
        AppUser operator,
        ImageObject representativeImageObject,
        CreateContentCommand command,
        Instant representativeImageAssignedAt
    ) {
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            command.title(),
            command.description(),
            command.locationText(),
            command.operatingHoursText(),
            command.contactText(),
            command.precautions(),
            command.ageRequirement(),
            command.materials(),
            command.cancellationPolicyText(),
            command.publishAt()
        );
        content.assignRepresentativeImage(representativeImageObject, representativeImageAssignedAt);
        return contentRepository.saveAndFlush(content);
    }

    public boolean existsPublishedAndNotDeletedById(Long contentId) {
        return contentRepository.existsByContentIdAndStatusAndDeletedAtIsNull(
            contentId,
            ContentStatus.PUBLISHED
        );
    }

    public record CreateContentCommand(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        Instant publishAt
    ) {
    }
}
