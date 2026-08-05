package io.regionevent.regioneventbackend.domain.region.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.repository.RegionHomeContentSessionVerificationProjection;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentCacheAside;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentStaticInfo;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetRegionHomeUseCase {

    private static final int CONTENT_LIMIT = 10;

    private final RegionService regionService;
    private final ContentService contentService;
    private final PublicRegionCacheAside publicRegionCacheAside;
    private final PublicContentCacheAside publicContentCacheAside;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetRegionHomeUseCase(
        RegionService regionService,
        ContentService contentService,
        PublicRegionCacheAside publicRegionCacheAside,
        PublicContentCacheAside publicContentCacheAside,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionService = regionService;
        this.contentService = contentService;
        this.publicRegionCacheAside = publicRegionCacheAside;
        this.publicContentCacheAside = publicContentCacheAside;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public RegionHomeResult get(Long regionId) {
        regionService.findPublicRegion(regionId);
        PublicRegionStaticInfo region = publicRegionCacheAside.resolve(
            regionId,
            () -> regionService.findPublicRegionStaticInfo(regionId)
        );
        Map<Long, List<RegionHomeContentSessionVerificationProjection>> contentSessions =
            contentService.findRegionHomeContentSessionVerifications(regionId).stream()
                .collect(Collectors.groupingBy(RegionHomeContentSessionVerificationProjection::contentId));

        List<RegionHomeContentSessionVerificationProjection> ongoingContents = new ArrayList<>();
        List<RegionHomeContentSessionVerificationProjection> upcomingContents = new ArrayList<>();
        contentSessions.values().forEach(sessions -> addDisplaySession(
            sessions,
            ongoingContents,
            upcomingContents
        ));

        return new RegionHomeResult(
            region,
            toOngoingContents(ongoingContents),
            toUpcomingContents(upcomingContents)
        );
    }

    private void addDisplaySession(
        List<RegionHomeContentSessionVerificationProjection> sessions,
        List<RegionHomeContentSessionVerificationProjection> ongoingContents,
        List<RegionHomeContentSessionVerificationProjection> upcomingContents
    ) {
        sessions.stream()
            .filter(RegionHomeContentSessionVerificationProjection::ongoing)
            .min(Comparator.comparing(RegionHomeContentSessionVerificationProjection::endsAt)
                .thenComparing(RegionHomeContentSessionVerificationProjection::sessionId))
            .ifPresentOrElse(
                ongoingContents::add,
                () -> upcomingContents.add(sessions.stream()
                    .min(Comparator.comparing(RegionHomeContentSessionVerificationProjection::startsAt)
                        .thenComparing(RegionHomeContentSessionVerificationProjection::sessionId))
                    .orElseThrow())
            );
    }

    private List<RegionHomeResult.Content> toOngoingContents(
        List<RegionHomeContentSessionVerificationProjection> contents
    ) {
        return contents.stream()
            .sorted(Comparator.comparing(RegionHomeContentSessionVerificationProjection::endsAt)
                .thenComparing(RegionHomeContentSessionVerificationProjection::contentId))
            .limit(CONTENT_LIMIT)
            .map(this::toContent)
            .toList();
    }

    private List<RegionHomeResult.Content> toUpcomingContents(
        List<RegionHomeContentSessionVerificationProjection> contents
    ) {
        return contents.stream()
            .sorted(Comparator.comparing(RegionHomeContentSessionVerificationProjection::startsAt)
                .thenComparing(RegionHomeContentSessionVerificationProjection::contentId))
            .limit(CONTENT_LIMIT)
            .map(this::toContent)
            .toList();
    }

    private RegionHomeResult.Content toContent(
        RegionHomeContentSessionVerificationProjection projection
    ) {
        PublicContentStaticInfo staticInfo = publicContentCacheAside.resolveContent(
            projection.regionId(),
            projection.contentId(),
            projection.versionNo(),
            () -> contentService.findPublicContentStaticInfo(
                projection.regionId(),
                projection.contentId(),
                projection.versionNo()
            )
        );
        RepresentativeImageViewUrl representativeImageViewUrl = createRepresentativeImageViewUrl(projection);
        return new RegionHomeResult.Content(
            staticInfo.contentId(),
            staticInfo.contentType(),
            staticInfo.title(),
            staticInfo.locationText(),
            representativeImageViewUrl.url(),
            representativeImageViewUrl.expiresAt(),
            projection.reservationAvailable(),
            new RegionHomeResult.DisplaySession(
                projection.sessionId(),
                projection.startsAt(),
                projection.endsAt(),
                projection.remainingCapacity()
            )
        );
    }

    private RepresentativeImageViewUrl createRepresentativeImageViewUrl(
        RegionHomeContentSessionVerificationProjection projection
    ) {
        ImageObject representativeImageObject = projection.representativeImageObject();
        if (representativeImageObject == null
            || projection.representativeImageAssignedAt() == null
            || !representativeImageObject.isScopedTo(projection.regionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return representativeImageViewUrlService.createViewUrl(representativeImageObject);
    }
}
