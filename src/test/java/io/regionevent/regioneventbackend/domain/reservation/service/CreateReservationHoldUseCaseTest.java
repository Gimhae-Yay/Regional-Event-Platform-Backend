package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

class CreateReservationHoldUseCaseTest {

    private static final Long USER_ID = 7L;
    private static final Long CONTENT_ID = 11L;
    private static final Long SESSION_ID = 13L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-06T00:00:00Z");
    private static final Instant SESSION_STARTS_AT = Instant.parse("2026-08-06T01:00:00Z");

    @Test
    void create_locksContentBeforeSessionAndReservesCapacity() {
        AppUserService appUserService = mock(AppUserService.class);
        ContentService contentService = mock(ContentService.class);
        ContentSessionService contentSessionService = mock(ContentSessionService.class);
        CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
        AppUser user = mock(AppUser.class);
        ContentSession contentSession = mock(ContentSession.class);
        CapacityHold capacityHold = mock(CapacityHold.class);
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(contentSessionService.findPublicContentId(SESSION_ID)).thenReturn(CONTENT_ID);
        when(contentService.lockPublishedReservationTarget(CONTENT_ID)).thenReturn(true);
        when(contentSessionService.findForUpdate(SESSION_ID)).thenReturn(contentSession);
        when(contentSession.getStartsAt()).thenReturn(SESSION_STARTS_AT);
        when(capacityHoldService.createActiveHold(
            user,
            contentSession,
            2,
            CREATED_AT,
            CREATED_AT.plusSeconds(600)
        )).thenReturn(capacityHold);
        when(capacityHold.getHoldId()).thenReturn(17L);
        when(capacityHold.getQuantity()).thenReturn(2);
        when(capacityHold.getStatus()).thenReturn(CapacityHoldStatus.ACTIVE);
        when(capacityHold.getExpiresAt()).thenReturn(CREATED_AT.plusSeconds(600));
        when(capacityHold.getCreatedAt()).thenReturn(CREATED_AT);
        CreateReservationHoldUseCase useCase = new CreateReservationHoldUseCase(
            appUserService,
            contentService,
            contentSessionService,
            capacityHoldService,
            Clock.fixed(CREATED_AT, ZoneOffset.UTC)
        );

        CreateReservationHoldResponse response = useCase.create(
            USER_ID,
            new CreateReservationHoldRequest(SESSION_ID.toString(), 2)
        );

        assertThat(response.holdId()).isEqualTo("17");
        InOrder lockOrder = inOrder(contentService, contentSessionService);
        lockOrder.verify(contentSessionService).findPublicContentId(SESSION_ID);
        lockOrder.verify(contentService).lockPublishedReservationTarget(CONTENT_ID);
        lockOrder.verify(contentSessionService).findForUpdate(SESSION_ID);
        lockOrder.verify(contentSessionService).reserveCapacity(SESSION_ID, 2);
    }
}
