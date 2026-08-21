package io.regionevent.regioneventbackend.domain.platformadmin.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.platformadmin.service.AdminAccountListInfo;

public record GetAdminAccountsResponse(List<AdminAccountResponse> adminAccounts) {

    public GetAdminAccountsResponse {
        adminAccounts = List.copyOf(adminAccounts);
    }

    public static GetAdminAccountsResponse from(List<AdminAccountListInfo> adminAccounts) {
        return new GetAdminAccountsResponse(adminAccounts.stream()
            .map(AdminAccountResponse::from)
            .toList());
    }

    public record AdminAccountResponse(
        String userId,
        String loginIdentifier,
        String name,
        String grade,
        String status,
        Instant createdAt,
        Instant inactivatedAt
    ) {

        private static AdminAccountResponse from(AdminAccountListInfo adminAccount) {
            return new AdminAccountResponse(
                adminAccount.userId().toString(),
                adminAccount.loginIdentifier(),
                adminAccount.name(),
                adminAccount.grade().name(),
                adminAccount.status().name(),
                adminAccount.createdAt(),
                adminAccount.inactivatedAt()
            );
        }
    }
}
