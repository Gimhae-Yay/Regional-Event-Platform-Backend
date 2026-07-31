package io.regionevent.regioneventbackend.domain.image.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public interface ImageObjectRepository extends JpaRepository<ImageObject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImageObject> findByImageObjectId(Long imageObjectId);
}
