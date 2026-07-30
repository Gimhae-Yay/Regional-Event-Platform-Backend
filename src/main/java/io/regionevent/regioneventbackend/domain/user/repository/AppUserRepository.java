package io.regionevent.regioneventbackend.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByLoginIdentifier(String loginIdentifier);
}
