package io.regionevent.regioneventbackend.domain.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.Content;

public interface ContentRepository extends JpaRepository<Content, Long> {

    long countByRepresentativeImageObjectImageObjectId(Long imageObjectId);
}
