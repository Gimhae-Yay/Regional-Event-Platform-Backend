## 3. 쿠폰 도메인 공통 값

쿠폰 API에서 공통으로 사용하는 정책 상태, 발급 근거, 쿠폰 상태, 사용 이력 상태와 `couponSummary` 응답 객체를 정의한다.
별도 HTTP 엔드포인트는 없으며 각 쿠폰 API 명세에서 이 계약을 참조한다.

### 상태와 분류

| 값 | 설명 |
| --- | --- |
| `DRAFT` | 작성 중인 쿠폰 정책. 발급 근거가 될 수 없다. |
| `PUBLISHED` | 공개된 쿠폰 정책. 신규 발급 근거가 될 수 있다. |
| `ENDED` | 종료된 쿠폰 정책. 신규 발급은 중단되며 기존 쿠폰의 만료 시각은 바꾸지 않는다. |
| `VISIT` | 유효 방문 기록을 근거로 발급한다. |
| `MISSION_REWARD` | 지역 미션 보상 수령 이력을 근거로 발급한다. |
| `STAMPBOOK_COMPLETION` | 스탬프북 완료 보상 이력을 근거로 발급한다. |
| `AVAILABLE` | 현재 사용 가능한 쿠폰 상태 |
| `RESERVED` | 결제 생성이 가격 스냅샷을 만들면서 만료 전에 선점한 쿠폰 상태. 원래 만료 시각을 연장하지 않지만 연결된 홀드가 `ACTIVE`인 동안 선점 효력을 유지한다. |
| `USED` | 확정 예약과 가격 스냅샷에 사용 확정된 쿠폰 상태 |
| `EXPIRED` | 자체 만료 시각이 지나 만료 처리된 쿠폰 상태 |
| `INVALIDATED` | 운영 보정 등 확정된 근거로 무효화된 쿠폰 상태 |
| `CONFIRMED` | 사용 이력이 확정된 상태 |
| `REVERSED` | 취소·환불 복구로 반전된 사용 이력 상태 |

### 상태 전이와 처리 주체

| 전이 | 처리 주체와 조건 |
| --- | --- |
| 발급 → `AVAILABLE` | 쿠폰 발급 요청이 쿠폰·발급 이력·상태 이력을 같은 트랜잭션에서 생성한다. |
| `AVAILABLE → RESERVED` | [결제 생성](../payment/create-payment.md)이 가격 스냅샷에 쿠폰과 할인 금액을 고정하면서 같은 트랜잭션에서 선점한다. |
| `RESERVED → USED` | 최종 금액이 0원이면 [결제 생성](../payment/create-payment.md), 양수이면 서버 검증에 성공한 [PortOne 웹훅](../payment/receive-portone-webhook.md)이 홀드 소비·`CONFIRMED` 예약·`coupon_redemption(CONFIRMED)`과 같은 트랜잭션에서 전이한다. 선점 뒤 원래 만료 시각이 지나도 홀드가 유효한 `ACTIVE`이면 확정할 수 있다. |
| `RESERVED → AVAILABLE` | 결제 거절·취소·만료 또는 홀드 만료·무효화로 예약 확정 전에 체크아웃이 종료되고 원래 만료 시각 전이면 해당 종결 처리가 선점을 해제한다. 홀드 만료·무효화는 [홀드 만료·무효화 작업](../../p0/reservation/expire-or-invalidate-holds.md)이 연결된 `PENDING` 결제를 `EXPIRED`로 종결하면서 함께 처리한다. |
| `AVAILABLE → EXPIRED` | 만료 작업이 원래 만료 시각이 지난 미사용 쿠폰을 전이하고 상태 이력을 남긴다. |
| `RESERVED → EXPIRED` | 결제 또는 홀드의 종결 처리 시 원래 만료 시각이 지났으면 선점 해제 처리가 전이하고 상태 이력을 남긴다. |
| `USED → AVAILABLE·EXPIRED` | 최종 금액 0원 예약은 회차 시작 전 취소 성공 시, 양수 결제 예약은 회차 시작 전 취소의 전액 환불이 최초 `SUCCEEDED`로 확정될 때 사용 이력을 `REVERSED`로 남기고 원래 만료 여부에 따라 복구한다. 모든 환불 성공 경로는 [환불 공통 쿠폰 복구 계약](../refund/refund.md#쿠폰-복구-계약)을 따른다. |
| 현재 상태 → `INVALIDATED` | 별도로 확정된 운영 보정만 허용한다. 이번 API 목록에는 공개 무효화 명령을 제공하지 않는다. |

`AVAILABLE → USED` 직접 전이와 별도 쿠폰 사용 확정 HTTP 요청은 허용하지 않는다. 모든 전이는 원인, 처리자 또는 시스템 주체와 처리 시각을 상태 이력에 기록한다.

### 공통 응답 객체

`couponSummary`는 목록과 발급 응답에서 사용한다.

| Name | Type | Description |
| --- | --- | --- |
| `couponId` | String | 쿠폰 식별자 |
| `couponPolicyId` | String | 쿠폰 정책 식별자 |
| `contentId` | String | 쿠폰 정책이 적용되는 콘텐츠 식별자 |
| `regionId` | String | 정책 콘텐츠의 지역 식별자 |
| `policyName` | String | 쿠폰 정책 이름 |
| `issueSourceType` | String | `VISIT`, `MISSION_REWARD`, `STAMPBOOK_COMPLETION` 중 하나 |
| `status` | String | `AVAILABLE`, `RESERVED`, `USED`, `EXPIRED`, `INVALIDATED` 중 하나 |
| `discountAmount` | Number | 정액 할인 금액. 1 이상 정수 |
| `minimumPaymentAmount` | Number | 사용 가능한 최소 결제 금액. 0 이상 정수 |
| `issuedAt` | String | 발급 시각. API 공통 규칙에 따른 UTC ISO 8601 일시 |
| `expiresAt` | String | 쿠폰 자체 만료 시각. API 공통 규칙에 따른 UTC ISO 8601 일시 |
