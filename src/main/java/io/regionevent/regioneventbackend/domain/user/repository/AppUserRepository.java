package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByLoginIdentifier(String loginIdentifier);

    Optional<AppUser> findByLoginIdentifier(String loginIdentifier);
}
