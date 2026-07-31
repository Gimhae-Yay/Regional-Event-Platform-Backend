package io.regionevent.regioneventbackend.domain.user.service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public record OperatorAuthority(
    AppUser appUser,
    Region region
) {
}
