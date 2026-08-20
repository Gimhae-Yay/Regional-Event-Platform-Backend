# k6 Write API Response Time Summary

- Requests per write case: 100
- Independent fixture rounds: 100

| Endpoint | Requests | Avg | P95 | Max |
| --- | ---: | ---: | ---: | ---: |
| DELETE /api/v1/auth/delete | 100 | 52.42ms | 67.67ms | 137.61ms |
| DELETE /api/v1/region-admin/contents/{{ids.deletableContentId}} | 100 | 25.97ms | 31.28ms | 40.62ms |
| DELETE /api/v1/reviews/{{ids.createdReviewId}} | 100 | 20.71ms | 24.48ms | 49.12ms |
| PATCH /api/v1/operator/coupon-policies/{{ids.createdCouponPolicyId}} | 100 | 24.28ms | 28.48ms | 35.17ms |
| PATCH /api/v1/operator/missions/{{ids.createdMissionId}} | 100 | 26.54ms | 29.76ms | 38.81ms |
| PATCH /api/v1/operator/stampbooks/{{ids.createdStampbookId}} | 100 | 28.59ms | 31.63ms | 42.57ms |
| PATCH /api/v1/platform-admin/regions/{{ids.createdRegionId}}/status | 100 | 20.09ms | 22.02ms | 24.47ms |
| PATCH /api/v1/platform-admin/users/{{ids.roleChangeUserId}}/role | 100 | 24.48ms | 28.36ms | 43.28ms |
| PATCH /api/v1/reviews/{{ids.createdReviewId}} | 100 | 21.4ms | 25.36ms | 45.38ms |
| POST /api/v1/auth/logout | 100 | 6.31ms | 7.26ms | 10.58ms |
| POST /api/v1/auth/refresh | 100 | 13.17ms | 14.79ms | 31.24ms |
| POST /api/v1/auth/signup | 100 | 414.33ms | 432.96ms | 543.39ms |
| POST /api/v1/coupon-policies/{{ids.couponPolicyId}}/coupons | 100 | 31.83ms | 39.65ms | 60.73ms |
| POST /api/v1/me/mission-participations/{{ids.completedMissionParticipationId}}/rewards/claim | 100 | 44.9ms | 52.21ms | 75.44ms |
| POST /api/v1/me/reservation-holds/{{ids.createdHoldId}}/payments | 100 | 41.79ms | 47.53ms | 62.51ms |
| POST /api/v1/me/reservations/{{ids.cancellableReservationId}}/cancel | 100 | 36.4ms | 40.66ms | 63.61ms |
| POST /api/v1/missions/{{ids.joinableMissionId}}/participations | 100 | 28.63ms | 31.91ms | 39.46ms |
| POST /api/v1/operator/check-ins | 100 | 80.16ms | 112.62ms | 163.65ms |
| POST /api/v1/operator/check-ins/manual | 100 | 60.42ms | 92.41ms | 197.11ms |
| POST /api/v1/operator/content-revisions/{{ids.withdrawableContentRevisionId}}/withdraw | 100 | 29.62ms | 32.89ms | 66.63ms |
| POST /api/v1/operator/contents | 100 | 22.22ms | 25.61ms | 58.64ms |
| POST /api/v1/operator/contents/{{ids.createdContentId}}/sessions | 100 | 21.43ms | 25.95ms | 60.78ms |
| POST /api/v1/operator/contents/{{ids.revisableContentId}}/revisions | 100 | 20.07ms | 23.46ms | 35.61ms |
| POST /api/v1/operator/contents/{{ids.submittableContentId}}/submit | 100 | 23.66ms | 27.82ms | 44.15ms |
| POST /api/v1/operator/contents/{{ids.withdrawalContentId}}/withdrawal-requests | 100 | 31.05ms | 37.16ms | 73.82ms |
| POST /api/v1/operator/coupon-policies | 100 | 19.9ms | 23.97ms | 34.23ms |
| POST /api/v1/operator/coupon-policies/{{ids.createdCouponPolicyId}}/publish | 100 | 21.98ms | 24.44ms | 32.34ms |
| POST /api/v1/operator/coupon-policies/{{ids.endableCouponPolicyId}}/end | 100 | 24.64ms | 28.33ms | 36.4ms |
| POST /api/v1/operator/missions | 100 | 21.53ms | 24.69ms | 36.77ms |
| POST /api/v1/operator/missions/{{ids.createdMissionId}}/submit | 100 | 24.61ms | 28.32ms | 38.43ms |
| POST /api/v1/operator/missions/{{ids.endableMissionId}}/end | 100 | 21.15ms | 23.13ms | 41.07ms |
| POST /api/v1/operator/operator-requests | 100 | 20.03ms | 22.43ms | 87.74ms |
| POST /api/v1/operator/sessions/{{ids.cancellableSessionId}}/cancel | 100 | 27.21ms | 30.51ms | 34.3ms |
| POST /api/v1/operator/sessions/{{ids.revisableSessionId}}/change-requests | 100 | 25.02ms | 30.27ms | 50.23ms |
| POST /api/v1/operator/stampbooks | 100 | 24.86ms | 27.54ms | 47.34ms |
| POST /api/v1/operator/stampbooks/{{ids.createdStampbookId}}/publish | 100 | 25.76ms | 30.39ms | 54.44ms |
| POST /api/v1/operator/stampbooks/{{ids.endableStampbookId}}/end | 100 | 24.78ms | 28.64ms | 47.12ms |
| POST /api/v1/operator/uploads/presigned-url | 100 | 15.15ms | 16.82ms | 77.85ms |
| POST /api/v1/platform-admin/admin-accounts | 100 | 418.33ms | 423.57ms | 508.34ms |
| POST /api/v1/platform-admin/admin-accounts/{{ids.createdPlatformAdminId}}/deactivate | 100 | 23.77ms | 26.33ms | 54.29ms |
| POST /api/v1/platform-admin/payment-discrepancies/{{ids.resolvablePaymentDiscrepancyId}}/manual-actions | 100 | 24.57ms | 26.93ms | 35.42ms |
| POST /api/v1/platform-admin/payments/{{ids.paidPaymentId}}/refund | 100 | 42.31ms | 50.07ms | 75.23ms |
| POST /api/v1/platform-admin/refund-failures/{{ids.resolvableRefundFailureId}}/manual-actions | 100 | 23.18ms | 26.17ms | 36.59ms |
| POST /api/v1/platform-admin/refunds/{{ids.retryableRefundId}}/retry | 100 | 41.32ms | 45.51ms | 54.98ms |
| POST /api/v1/platform-admin/regions | 100 | 19.23ms | 21.99ms | 34.18ms |
| POST /api/v1/region-admin/content-revisions/{{ids.approvableContentRevisionId}}/approve | 100 | 33.37ms | 36.95ms | 46.14ms |
| POST /api/v1/region-admin/content-revisions/{{ids.rejectableContentRevisionId}}/reject | 100 | 32.46ms | 35.69ms | 188.41ms |
| POST /api/v1/region-admin/content-withdrawal-requests/{{ids.createdWithdrawalRequestId}}/approve | 100 | 38.58ms | 47.73ms | 83.25ms |
| POST /api/v1/region-admin/content-withdrawal-requests/{{ids.rejectableWithdrawalRequestId}}/reject | 100 | 31.58ms | 35.28ms | 54.44ms |
| POST /api/v1/region-admin/contents/{{ids.createdContentId}}/approve | 100 | 28.65ms | 32.7ms | 65.08ms |
| POST /api/v1/region-admin/contents/{{ids.endableContentId}}/end | 100 | 37.62ms | 41.81ms | 57.83ms |
| POST /api/v1/region-admin/contents/{{ids.rejectableContentId}}/reject | 100 | 24.74ms | 31.21ms | 57.49ms |
| POST /api/v1/region-admin/contents/{{ids.suspendableContentId}}/suspend | 100 | 35.09ms | 41.67ms | 50.2ms |
| POST /api/v1/region-admin/missions/{{ids.createdMissionId}}/approve | 100 | 31.92ms | 36.54ms | 66.66ms |
| POST /api/v1/region-admin/missions/{{ids.rejectedMissionId}}/reject | 100 | 27.76ms | 31.66ms | 47.64ms |
| POST /api/v1/region-admin/operator-requests/{{ids.createdOperatorApplicationId}}/approve | 100 | 24.83ms | 28.25ms | 43.43ms |
| POST /api/v1/region-admin/operator-requests/{{ids.rejectedOperatorApplicationId}}/reject | 100 | 23.43ms | 26.61ms | 48.65ms |
| POST /api/v1/region-admin/session-revisions/{{ids.approvableSessionRevisionId}}/approve | 100 | 31.14ms | 35.51ms | 46.59ms |
| POST /api/v1/region-admin/session-revisions/{{ids.rejectableSessionRevisionId}}/reject | 100 | 21.42ms | 25.05ms | 69.74ms |
| POST /api/v1/region-admin/sessions/{{ids.createdContentSessionId}}/approve | 100 | 24.43ms | 28.42ms | 99.5ms |
| POST /api/v1/region-admin/sessions/{{ids.rejectableSessionId}}/reject | 100 | 21.5ms | 24.46ms | 41.12ms |
| POST /api/v1/region-admin/stampbooks/{{ids.createdStampbookId}}/approve | 100 | 37.17ms | 41.03ms | 55.85ms |
| POST /api/v1/region-admin/stampbooks/{{ids.rejectedStampbookId}}/reject | 100 | 33.93ms | 38.49ms | 46.25ms |
| POST /api/v1/reservation-holds/{{ids.confirmableHoldId}}/confirm | 100 | 50.97ms | 59.54ms | 74.95ms |
| POST /api/v1/reservations | 200 | 22.44ms | 25.23ms | 106.75ms |
| POST /api/v1/visits/{{ids.reviewVisitId}}/reviews | 100 | 20.44ms | 23.11ms | 36.65ms |
| POST /api/v1/webhooks/portone | 100 | 48.66ms | 62.05ms | 73.9ms |
| PUT /api/v1/operator/content-revisions/{{ids.updatableContentRevisionId}} | 100 | 17.51ms | 20.74ms | 27.46ms |
| PUT /api/v1/operator/contents/{{ids.editableContentId}} | 100 | 17.42ms | 19.63ms | 38.99ms |
