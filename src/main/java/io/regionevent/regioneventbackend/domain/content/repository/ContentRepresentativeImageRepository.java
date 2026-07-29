package io.regionevent.regioneventbackend.domain.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRepresentativeImage;

public interface ContentRepresentativeImageRepository
    extends JpaRepository<ContentRepresentativeImage, Long> {
}
