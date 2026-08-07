# 쿠폰 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-05`, `P1-FR-06`, `CPN-01`~`CPN-05` |
| 소유 도메인 | 쿠폰 |
| 기준 문서 | [쿠폰](../../../p1/coupon.md), [로컬스탬프 P1 명세](../../../p1-spec.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 쿠폰 정책 운영, 쿠폰 발급, 내 쿠폰 조회, 사용 가능 판단과 사용 이력 조회를 HTTP API 계약으로 구체화한다.
요청·응답의 공통 형식, 인증, 시간·식별자 표현과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 쿠폰 API에만 적용되는 값과 규칙만 작성한다.

P1 쿠폰은 정책이 가리키는 콘텐츠의 유료 예약에 적용하는 정액 할인으로 한정한다. 발급 근거는 `VISIT`, `MISSION_REWARD`,
`STAMPBOOK_COMPLETION` 중 하나다. 방문은 정책·수령자, 미션·스탬프북은 정책·수령자·보상 근거가 같은 반복 요청을 하나의 쿠폰으로 수렴시킨다.
`MISSION_REWARD` 발급은 [미션 완료 보상 수령](../mission/claim-mission-reward.md)이 소유하며, 범용 쿠폰 발급 요청은
방문과 스탬프북 완료 보상만 처리한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `P1-FR-05`, `CPN-01` | `POST /operator/coupon-policies` | `coupon_policy`, `coupon_policy_history` |
| `P1-FR-05`, `CPN-01` | `PATCH /operator/coupon-policies/{couponPolicyId}` | `coupon_policy`, `coupon_policy_history` |
| `P1-FR-05`, `CPN-01` | `POST /operator/coupon-policies/{couponPolicyId}/publish` | `coupon_policy.status`, `coupon_policy_history` |
| `P1-FR-05`, `CPN-01` | `POST /operator/coupon-policies/{couponPolicyId}/end` | `coupon_policy.status`, `coupon_policy_history` |
| `P1-FR-06`, `CPN-02`, `CPN-05` | `POST /coupon-policies/{couponPolicyId}/coupons` | `coupon`, `coupon_issuance`, `coupon_status_history` |
| `P1-FR-06`, `CPN-03` | `GET /me/coupons` | `coupon`, `coupon_policy` |
| `P1-FR-06`, `CPN-03`, `CPN-04` | `GET /me/coupons/available` | `coupon`, `coupon_policy`, `capacity_hold`, `content_session` |
| `P1-FR-06`, `CPN-03`, `CPN-05` | `GET /me/coupons/{couponId}` | `coupon`, `coupon_policy`, `coupon_status_history` |
| `P1-FR-06`, `CPN-05` | `GET /me/coupons/{couponId}/usage-history` | `coupon_redemption`, `coupon_status_history` |
| `P1-FR-06`, `CPN-04`, `CPN-05` | `POST /me/reservation-holds/{holdId}/payments` | `reservation_price_snapshot`, `coupon`, `coupon_status_history`, `coupon_redemption` |
| `P1-FR-06`, `CPN-04`, `CPN-05` | `POST /webhooks/portone` | `payment`, `reservation`, `coupon`, `coupon_status_history`, `coupon_redemption` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`. 쿠폰 평가·발급·사용·상태 이력 시각은 UTC ISO 8601 일시다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 내 쿠폰 API는 활성 회원 본인 소유권을 검증한다. 운영 API는 활성 회원의 현재 `OPERATOR` 역할, 정책 콘텐츠와 같은 지역, 정책 콘텐츠 소유권을 모두 검증한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P1 쿠폰 목록은 단순 목록으로 반환하며 페이지네이션을 적용하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 쿠폰 공통 값 | 별도 HTTP 경로 없음 | [coupon-common.md](coupon-common.md) |
| 내 쿠폰 목록 조회 | `GET /me/coupons` | [list-my-coupons.md](list-my-coupons.md) |
| 사용 가능한 내 쿠폰 목록 조회 | `GET /me/coupons/available` | [list-my-available-coupons.md](list-my-available-coupons.md) |
| 내 쿠폰 상세 조회 | `GET /me/coupons/{couponId}` | [get-my-coupon.md](get-my-coupon.md) |
| 내 쿠폰 사용 이력 조회 | `GET /me/coupons/{couponId}/usage-history` | [get-my-coupon-usage-history.md](get-my-coupon-usage-history.md) |
| 쿠폰 정책 생성 | `POST /operator/coupon-policies` | [create-coupon-policy.md](create-coupon-policy.md) |
| 쿠폰 정책 수정 | `PATCH /operator/coupon-policies/{couponPolicyId}` | [update-coupon-policy.md](update-coupon-policy.md) |
| 쿠폰 정책 공개 | `POST /operator/coupon-policies/{couponPolicyId}/publish` | [publish-coupon-policy.md](publish-coupon-policy.md) |
| 쿠폰 정책 종료 | `POST /operator/coupon-policies/{couponPolicyId}/end` | [end-coupon-policy.md](end-coupon-policy.md) |
| 방문·스탬프북 쿠폰 발급 요청 | `POST /coupon-policies/{couponPolicyId}/coupons` | [issue-coupon.md](issue-coupon.md) |

### 결제 연계 API

쿠폰 선택·선점과 사용 확정은 결제 도메인이 소유한다. 클라이언트는 결제 생성 요청의 `couponId`로 쿠폰을 선택한다.
최종 금액이 0원이면 결제 생성에서, 양수이면 서버 검증에 성공한 PortOne 웹훅에서 쿠폰 사용을 확정한다.
별도 쿠폰 사용 확정 HTTP API는 제공하지 않는다.

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 가격 스냅샷 생성·쿠폰 선점·0원 사용 확정 | `POST /me/reservation-holds/{holdId}/payments` | [결제 생성](../payment/create-payment.md) |
| 양수 결제 승인·쿠폰 사용 확정 | `POST /webhooks/portone` | [PortOne 결제 웹훅 수신](../payment/receive-portone-webhook.md) |
