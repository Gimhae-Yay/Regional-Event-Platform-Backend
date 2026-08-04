package io.regionevent.regioneventbackend.domain.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;

public interface AuditEventActorLinkRepository extends JpaRepository<AuditEventActorLink, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM AuditEventActorLink actorLink
        WHERE actorLink.actor.userId = :userId
        """)
    int deleteByActorUserId(@Param("userId") Long userId);
}
