package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;

class AccessTokenAuthorityResolverTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final AccessTokenAuthorityResolver accessTokenAuthorityResolver = new AccessTokenAuthorityResolver(
        appUserRepository
    );

    @Test
    void resolve_whenOrdinaryUserHasMultipleActiveRoles_returnsAuthorityUnion() {
        AppUser user = user(1L, AppUserAccountKind.ORDINARY);
        when(appUserRepository.findActiveUserRolesByUserId(1L))
            .thenReturn(List.of(UserRole.OPERATOR, UserRole.VISITOR));
        when(appUserRepository.findActivePlatformAdminGradesByUserId(1L)).thenReturn(List.of());

        assertThat(accessTokenAuthorityResolver.resolve(user))
            .containsExactly(AccessTokenAuthority.VISITOR, AccessTokenAuthority.OPERATOR);
    }

    @Test
    void resolve_whenPrivilegedUserHasActiveGrade_returnsOnlyItsAuthority() {
        AppUser user = user(1L, AppUserAccountKind.PRIVILEGED);
        when(appUserRepository.findActiveUserRolesByUserId(1L)).thenReturn(List.of());
        when(appUserRepository.findActivePlatformAdminGradesByUserId(1L))
            .thenReturn(List.of(PlatformAdminGrade.SUPER_ADMIN));

        assertThat(accessTokenAuthorityResolver.resolve(user))
            .containsExactly(AccessTokenAuthority.SUPER_ADMIN)
            .doesNotContain(AccessTokenAuthority.PLATFORM_ADMIN);
    }

    @Test
    void resolve_whenPrivilegedUserHasPlatformAdminGrade_returnsOnlyPlatformAdminAuthority() {
        AppUser user = user(1L, AppUserAccountKind.PRIVILEGED);
        when(appUserRepository.findActiveUserRolesByUserId(1L)).thenReturn(List.of());
        when(appUserRepository.findActivePlatformAdminGradesByUserId(1L))
            .thenReturn(List.of(PlatformAdminGrade.PLATFORM_ADMIN));

        assertThat(accessTokenAuthorityResolver.resolve(user))
            .containsExactly(AccessTokenAuthority.PLATFORM_ADMIN)
            .doesNotContain(AccessTokenAuthority.SUPER_ADMIN);
    }

    @Test
    void resolve_whenActiveAuthoritySourceIsAbsent_returnsEmptyAuthorities() {
        AppUser ordinaryUser = user(1L, AppUserAccountKind.ORDINARY);
        AppUser privilegedUser = user(2L, AppUserAccountKind.PRIVILEGED);
        when(appUserRepository.findActiveUserRolesByUserId(1L)).thenReturn(List.of());
        when(appUserRepository.findActivePlatformAdminGradesByUserId(1L)).thenReturn(List.of());
        when(appUserRepository.findActiveUserRolesByUserId(2L)).thenReturn(List.of());
        when(appUserRepository.findActivePlatformAdminGradesByUserId(2L)).thenReturn(List.of());

        assertThat(accessTokenAuthorityResolver.resolve(ordinaryUser)).isEmpty();
        assertThat(accessTokenAuthorityResolver.resolve(privilegedUser)).isEmpty();
    }

    @Test
    void resolve_whenActiveRolesChange_returnsCurrentAuthoritySnapshot() {
        AppUser user = user(1L, AppUserAccountKind.ORDINARY);
        when(appUserRepository.findActiveUserRolesByUserId(1L))
            .thenReturn(List.of(UserRole.VISITOR), List.of(UserRole.OPERATOR));
        when(appUserRepository.findActivePlatformAdminGradesByUserId(1L)).thenReturn(List.of());

        assertThat(accessTokenAuthorityResolver.resolve(user))
            .containsExactly(AccessTokenAuthority.VISITOR);
        assertThat(accessTokenAuthorityResolver.resolve(user))
            .containsExactly(AccessTokenAuthority.OPERATOR);
    }

    @Test
    void resolve_whenAuthoritySourcesConflict_rejectsTokenIssuance() {
        AppUser user = user(1L, AppUserAccountKind.ORDINARY);
        when(appUserRepository.findActiveUserRolesByUserId(1L)).thenReturn(List.of(UserRole.VISITOR));
        when(appUserRepository.findActivePlatformAdminGradesByUserId(1L))
            .thenReturn(List.of(PlatformAdminGrade.PLATFORM_ADMIN));

        assertThatThrownBy(() -> accessTokenAuthorityResolver.resolve(user))
            .isInstanceOf(AccessTokenAuthoritySourceConflictException.class);
    }

    private AppUser user(Long userId, AppUserAccountKind accountKind) {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getAccountKind()).thenReturn(accountKind);
        return user;
    }
}
