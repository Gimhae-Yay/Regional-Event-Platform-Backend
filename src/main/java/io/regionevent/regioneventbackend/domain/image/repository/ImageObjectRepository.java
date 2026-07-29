package io.regionevent.regioneventbackend.domain.image.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public interface ImageObjectRepository extends JpaRepository<ImageObject, Long> {
}
