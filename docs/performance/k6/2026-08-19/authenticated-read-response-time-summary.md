# k6 Authenticated Read Response Time Summary

## Run

- Generated at: 2026-08-19T06:23:42.521Z
- Base URL: http://ci-demo-alb-954894878.ap-northeast-2.elb.amazonaws.com/api/v1
- Iterations: 100 requests per API
- Test tag: authenticated_read_response_time
- Mode: Each GET API is called sequentially before the next API starts.
- Auth users: 7
- Test run duration: 109417.06ms
- Thresholds: PASS

## Highlights

| Metric | Value |
| --- | ---: |
| HTTP requests | 6114 |
| HTTP request rate | 55.88/s |
| HTTP failed rate | 0.00% |
| Checks passed rate | 100.00% |
| Checks passed | 6100 |
| Checks failed | 0 |
| HTTP duration avg | 17.82ms |
| HTTP duration p95 | 24.27ms |
| HTTP duration p99 | 30.02ms |

## Thresholds

| Metric | Condition | Result |
| --- | --- | --- |
| expected_outcome_rate | rate==1 | PASS |
| http_req_duration{endpoint:GET /api/v1/contents/{contentId}/reviews} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/contents/{contentId}/sessions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/contents/{contentId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/contents} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/coupons/available} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/coupons/{couponId}/usage-history} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/coupons/{couponId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/coupons} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/mission-participations/{participationId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/mission-participations} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/payments/{paymentId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/refunds/{refundId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/refunds} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/reservations/{reservationId}/qr} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/reservations/{reservationId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/reservations} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/stampbooks/{stampbookId}/earnings} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/stampbooks/{stampbookId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me/stampbooks} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/me} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/missions/{missionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/contents/{contentId}/reservations} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/contents/{contentId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/contents} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/coupon-policies/{couponPolicyId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/coupon-policies} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/missions/{missionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/missions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/reservations/search} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/operator/reservations/{reservationId}/payment} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/platform-admin/payment-discrepancies/{discrepancyId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/platform-admin/payment-discrepancies} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/platform-admin/refund-failures/{refundId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/platform-admin/refund-failures} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/platform-admin/regions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/platform-admin/users} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/content-revisions/{revisionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/content-revisions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/content-withdrawal-requests} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/contents/{contentId}/history} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/contents/{contentId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/contents} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/missions/{missionId}/history} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/missions/{missionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/missions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/operator-requests/{requestId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/operator-requests} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/qr-exceptions/{exceptionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/qr-exceptions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/reservations/search} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/session-revisions/{revisionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/session-revisions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/sessions/{sessionId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/sessions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/stampbooks/{stampbookId}} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/region-admin/stampbooks} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/regions/{regionId}/home} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/regions/{regionId}/missions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/regions} | p(95)<5000 | PASS |
| http_req_duration{endpoint:GET /api/v1/sessions/{sessionId}} | p(95)<5000 | PASS |

## HTTP Duration By Endpoint

| Endpoint | Avg | Med | P95 | P99 | Max |
| --- | ---: | ---: | ---: | ---: | ---: |
| GET /api/v1/contents/{contentId | 17.03ms | 16.81ms | 18.61ms | 20.61ms | 21.81ms |
| GET /api/v1/contents/{contentId | 15.57ms | 15.44ms | 17.52ms | 18.24ms | 19.56ms |
| GET /api/v1/contents/{contentId | 14.90ms | 14.62ms | 16.55ms | 18.92ms | 19.63ms |
| GET /api/v1/contents | 21.73ms | 20.46ms | 29.12ms | 34.61ms | 57.53ms |
| GET /api/v1/me/coupons/available | 22.06ms | 21.91ms | 24.20ms | 25.25ms | 26.17ms |
| GET /api/v1/me/coupons/{couponId | 21.57ms | 18.57ms | 27.19ms | 41.86ms | 205.29ms |
| GET /api/v1/me/coupons/{couponId | 16.26ms | 15.88ms | 19.51ms | 21.08ms | 21.87ms |
| GET /api/v1/me/coupons | 15.50ms | 15.37ms | 16.73ms | 18.93ms | 22.64ms |
| GET /api/v1/me/mission-participations/{participationId | 23.05ms | 22.58ms | 26.09ms | 31.19ms | 33.34ms |
| GET /api/v1/me/mission-participations | 17.61ms | 17.21ms | 20.23ms | 26.06ms | 26.24ms |
| GET /api/v1/me/payments/{paymentId | 18.10ms | 17.70ms | 20.42ms | 23.37ms | 27.75ms |
| GET /api/v1/me/refunds/{refundId | 16.22ms | 15.35ms | 17.71ms | 34.71ms | 61.88ms |
| GET /api/v1/me/refunds | 15.11ms | 15.10ms | 16.46ms | 18.14ms | 18.87ms |
| GET /api/v1/me/reservations/{reservationId | 14.98ms | 14.81ms | 16.53ms | 18.06ms | 21.45ms |
| GET /api/v1/me/reservations/{reservationId | 16.43ms | 15.80ms | 21.90ms | 24.95ms | 27.98ms |
| GET /api/v1/me/reservations | 17.32ms | 16.60ms | 21.00ms | 25.16ms | 42.24ms |
| GET /api/v1/me/stampbooks/{stampbookId | 14.74ms | 14.66ms | 15.68ms | 17.27ms | 18.37ms |
| GET /api/v1/me/stampbooks/{stampbookId | 15.21ms | 15.03ms | 16.90ms | 19.00ms | 19.05ms |
| GET /api/v1/me/stampbooks | 16.70ms | 16.54ms | 18.08ms | 19.98ms | 20.81ms |
| GET /api/v1/me | 16.74ms | 16.05ms | 19.18ms | 28.61ms | 30.24ms |
| GET /api/v1/missions/{missionId | 14.36ms | 14.31ms | 15.40ms | 16.22ms | 16.60ms |
| GET /api/v1/operator/contents/{contentId | 20.66ms | 20.42ms | 22.52ms | 24.08ms | 28.97ms |
| GET /api/v1/operator/contents/{contentId | 15.98ms | 15.58ms | 18.16ms | 21.68ms | 23.53ms |
| GET /api/v1/operator/contents | 14.95ms | 14.49ms | 15.97ms | 20.36ms | 53.68ms |
| GET /api/v1/operator/coupon-policies/{couponPolicyId | 16.53ms | 16.20ms | 18.55ms | 22.20ms | 25.78ms |
| GET /api/v1/operator/coupon-policies | 17.51ms | 17.04ms | 19.72ms | 26.07ms | 34.07ms |
| GET /api/v1/operator/missions/{missionId | 16.08ms | 15.73ms | 18.84ms | 25.06ms | 25.99ms |
| GET /api/v1/operator/missions | 15.64ms | 15.35ms | 17.72ms | 19.15ms | 20.50ms |
| GET /api/v1/operator/reservations/search | 23.87ms | 23.68ms | 27.22ms | 28.34ms | 28.43ms |
| GET /api/v1/operator/reservations/{reservationId | 25.70ms | 24.05ms | 31.73ms | 54.51ms | 106.41ms |
| GET /api/v1/platform-admin/payment-discrepancies/{discrepancyId | 18.60ms | 18.44ms | 20.29ms | 23.16ms | 23.23ms |
| GET /api/v1/platform-admin/payment-discrepancies | 15.37ms | 15.25ms | 17.09ms | 18.08ms | 20.57ms |
| GET /api/v1/platform-admin/refund-failures/{refundId | 16.48ms | 16.18ms | 18.66ms | 20.19ms | 22.61ms |
| GET /api/v1/platform-admin/refund-failures | 17.94ms | 17.66ms | 20.02ms | 21.83ms | 37.68ms |
| GET /api/v1/platform-admin/regions | 14.67ms | 14.43ms | 16.87ms | 19.74ms | 21.88ms |
| GET /api/v1/platform-admin/users | 14.04ms | 14.05ms | 14.91ms | 15.41ms | 15.58ms |
| GET /api/v1/region-admin/content-revisions/{revisionId | 20.52ms | 20.02ms | 23.36ms | 29.18ms | 32.02ms |
| GET /api/v1/region-admin/content-revisions | 25.46ms | 24.94ms | 27.95ms | 40.48ms | 43.79ms |
| GET /api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId | 15.23ms | 15.05ms | 16.81ms | 17.65ms | 18.01ms |
| GET /api/v1/region-admin/content-withdrawal-requests | 15.72ms | 15.40ms | 18.04ms | 19.34ms | 19.43ms |
| GET /api/v1/region-admin/contents/{contentId | 16.88ms | 16.86ms | 18.64ms | 19.88ms | 20.74ms |
| GET /api/v1/region-admin/contents/{contentId | 19.20ms | 18.41ms | 26.02ms | 35.85ms | 45.68ms |
| GET /api/v1/region-admin/contents | 20.19ms | 19.48ms | 23.62ms | 30.88ms | 33.91ms |
| GET /api/v1/region-admin/missions/{missionId | 16.44ms | 16.47ms | 17.63ms | 17.95ms | 19.44ms |
| GET /api/v1/region-admin/missions/{missionId | 15.79ms | 15.44ms | 19.66ms | 22.44ms | 24.33ms |
| GET /api/v1/region-admin/missions | 14.71ms | 14.60ms | 16.07ms | 18.35ms | 22.45ms |
| GET /api/v1/region-admin/operator-requests/{requestId | 14.85ms | 14.77ms | 16.05ms | 19.17ms | 20.60ms |
| GET /api/v1/region-admin/operator-requests | 14.92ms | 14.60ms | 15.91ms | 19.16ms | 38.76ms |
| GET /api/v1/region-admin/qr-exceptions/{exceptionId | 16.10ms | 15.99ms | 17.35ms | 19.44ms | 19.89ms |
| GET /api/v1/region-admin/qr-exceptions | 16.28ms | 15.80ms | 19.15ms | 27.40ms | 29.49ms |
| GET /api/v1/region-admin/reservations/search | 24.09ms | 23.02ms | 27.49ms | 39.70ms | 54.75ms |
| GET /api/v1/region-admin/session-revisions/{revisionId | 16.13ms | 15.98ms | 17.43ms | 18.96ms | 19.84ms |
| GET /api/v1/region-admin/session-revisions | 16.03ms | 15.36ms | 19.16ms | 22.26ms | 45.41ms |
| GET /api/v1/region-admin/sessions/{sessionId | 16.31ms | 16.09ms | 17.79ms | 20.82ms | 24.00ms |
| GET /api/v1/region-admin/sessions | 17.24ms | 16.56ms | 20.17ms | 28.38ms | 35.59ms |
| GET /api/v1/region-admin/stampbooks/{stampbookId | 16.89ms | 16.67ms | 19.27ms | 21.74ms | 21.87ms |
| GET /api/v1/region-admin/stampbooks | 14.27ms | 14.13ms | 15.56ms | 17.79ms | 20.02ms |
| GET /api/v1/regions/{regionId | 25.16ms | 22.73ms | 32.41ms | 77.96ms | 119.92ms |
| GET /api/v1/regions/{regionId | 16.16ms | 15.93ms | 17.37ms | 21.29ms | 23.26ms |
| GET /api/v1/regions | 17.08ms | 14.92ms | 21.79ms | 69.53ms | 92.10ms |
| GET /api/v1/sessions/{sessionId | 13.89ms | 13.72ms | 15.68ms | 16.48ms | 16.86ms |

## Expected Outcome By Endpoint

No endpoint expected outcome metrics were reported.
