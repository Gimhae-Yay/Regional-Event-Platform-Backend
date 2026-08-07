## 8. 쿠폰 사용 확정

활성 회원이 가격 스냅샷에 선점한 본인 쿠폰을 예약 확정과 함께 한 번만 사용 확정한다.
성공하면 예약 확정, 쿠폰 상태와 사용 이력은 같은 처리 단위로 반영된다.

### Request

```http
POST /me/coupons/{couponId}/use
```

#### Request Example

```http
POST /api/v1/me/coupons/1001/use HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
Accept: application/json

{
  "holdId": "789",
  "priceSnapshotId": "9001"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Idempotency-Key` | Y | 클라이언트가 생성한 비어 있지 않은 멱등 키. 같은 사용 확정 재시도에는 같은 값을 사용한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `couponId` | String | Y | 사용 확정할 쿠폰 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "holdId": "789",
  "priceSnapshotId": "9001"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `holdId` | String | Y | 가격 스냅샷에 연결된 본인 활성 홀드 식별자. 양수여야 한다. |
| `priceSnapshotId` | String | Y | 쿠폰 적용 금액이 계산된 가격 스냅샷 식별자. 양수여야 한다. |

### Response

#### Status

```http
200 OK
```

동일한 `Idempotency-Key`, `couponId`, `holdId`, `priceSnapshotId`로 완료된 요청을 재시도한 경우에도 최초 성공과 동일하게 `200 OK`와 저장된 결과를 반환한다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "쿠폰 사용 확정에 성공했습니다.",
  "data": {
    "couponId": "1001",
    "couponRedemptionId": "3001",
    "reservationId": "123",
    "reservationStatus": "CONFIRMED",
    "priceSnapshotId": "9001",
    "couponStatus": "USED",
    "discountAmount": 3000,
    "payableAmount": 17000,
    "usedAt": "2026-08-06T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.couponId` | String | 사용 확정한 쿠폰 식별자 |
| `data.couponRedemptionId` | String | 생성 또는 재반환한 쿠폰 사용 이력 식별자 |
| `data.reservationId` | String | 홀드를 소비해 생성·확정한 예약 식별자 |
| `data.reservationStatus` | String | 사용 확정과 함께 생성된 예약 상태. 항상 `CONFIRMED` |
| `data.priceSnapshotId` | String | 사용 확정 대상 가격 스냅샷 식별자 |
| `data.couponStatus` | String | 사용 확정 후 쿠폰 상태. 항상 `USED` |
| `data.discountAmount` | Number | 확정 할인 금액 |
| `data.payableAmount` | Number | 쿠폰 적용 후 결제 금액 |
| `data.usedAt` | String | 사용 확정 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `couponId`, `holdId`, `priceSnapshotId` 또는 `Idempotency-Key`가 없거나 형식·범위가 올바르지 않다. 쿠폰과 사용 이력은 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 쿠폰과 사용 이력은 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 식별자의 형식이 올바르지 않다. 쿠폰과 사용 이력은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 쿠폰, 홀드 또는 가격 스냅샷의 소유자가 아니다. |
| `404` | `NOT_FOUND` | 대상 쿠폰, 홀드 또는 가격 스냅샷을 찾을 수 없다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 회원의 `COUPON_USE` 명령에서 이미 다른 요청 해시에 사용한 `Idempotency-Key`다. 새 사용 이력은 생성하지 않는다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 회원·키·요청 해시의 최초 요청이 아직 처리 중이다. 새 사용 이력은 생성하지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `COUPON_NOT_USABLE` | 쿠폰이 같은 가격 스냅샷에 선점된 `RESERVED` 상태가 아니거나 사용·만료·무효 상태이고, 최소 결제 금액, 콘텐츠·지역 또는 홀드 조건을 만족하지 않는다. 홀드, 예약, 쿠폰과 사용 이력은 변경하지 않는다. |
| `409` | `COUPON_USE_CONFLICT` | 같은 홀드 또는 가격 스냅샷에서 다른 쿠폰의 사용 확정이 먼저 성공했거나 같은 쿠폰의 다른 사용 확정이 먼저 성공했다. 새 예약과 사용 이력은 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 사용 확정 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 동일한 `Idempotency-Key`로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "COUPON_NOT_USABLE",
  "message": "사용할 수 없는 쿠폰입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 쿠폰, 홀드, 가격 스냅샷의 소유자가 모두 일치하고 가격 스냅샷은 대상 홀드에 연결돼 있어야 한다.
3. 멱등 키의 논리 유일 범위는 `(actor_user_id, operation = COUPON_USE, idempotency_key_hash)`다.
4. 요청 해시는 정규화한 `(couponId, holdId, priceSnapshotId)`로 계산하며 개인 식별 정보와 인증 정보는 포함하지 않는다.
5. 최초로 수락한 요청은 `PROCESSING` 멱등 기록을 생성한다. 같은 범위의 키에 다른 요청 해시가 있으면 `IDEMPOTENCY_KEY_CONFLICT`, 같은 요청 해시의 `PROCESSING` 기록이 있으면 `IDEMPOTENCY_REQUEST_IN_PROGRESS`를 반환하며 처리 소유권을 탈취하지 않는다.
6. 같은 범위의 키와 요청 해시에 `SUCCEEDED` 기록이 있으면 저장한 `reservationId`와 `couponRedemptionId`를 기준으로 최초 성공 응답을 재구성해 반환한다.
7. 쿠폰·홀드의 종결 상태나 콘텐츠·지역·가격 불일치처럼 같은 요청으로 결과가 바뀌지 않는 `COUPON_NOT_USABLE`과 다른 사용 확정이 먼저 성공한 `COUPON_USE_CONFLICT`는 HTTP 상태와 오류 코드를 `FAILED` 멱등 결과로 저장하고 같은 재시도에 최초 오류를 반환한다.
8. 입력·인증·인가·대상 부재 오류, 결제 승인이 아직 확인되지 않은 실패, 트랜잭션 롤백을 동반한 서버 오류는 완료된 멱등 결과로 저장하지 않는다.
9. 예약 확정의 공유 도메인 행 잠금과 조건부 전이는 ADR-0058에 따라 `content → content_session → capacity_hold` 순서로 수행하고 각 행의 상태와 유효 시각을 잠금 획득 후 다시 검증한다. 가격 스냅샷·결제·쿠폰의 후속 잠금 순서는 P1 결제 계약에서 확정하되 이 공통 잠금 순서를 역전하지 않는다.
10. 쿠폰은 같은 가격 스냅샷에 선점된 `RESERVED` 상태여야 하며 `AVAILABLE → USED` 직접 전이는 허용하지 않는다.
11. `AVAILABLE → RESERVED` 선점 시 쿠폰의 `expiresAt`이 서버 현재 시각보다 미래였어야 한다. 같은 가격 스냅샷에 연결된 홀드가 유효한 `ACTIVE` 상태인 동안에는 선점 뒤 원래 만료 시각이 지나도 이 사유만으로 사용 확정을 거부하지 않는다.
12. 쿠폰 정책의 `contentId`와 `regionId`는 홀드 회차의 콘텐츠·지역과 각각 같아야 한다.
13. 가격 스냅샷의 적용 쿠폰은 요청 `couponId`와 같아야 한다. 스냅샷의 기준 금액은 `minimumPaymentAmount` 이상이고 할인 금액은 정책 금액과 같으며 결제 금액은 기준 금액에서 할인 금액을 뺀 값이어야 한다.
14. 결제 금액이 양수이면 해당 홀드와 가격 스냅샷에 연결된 결제가 서버 검증을 거쳐 승인돼야 하고, 0원이면 외부 결제 없이 가격 확정 조건을 만족해야 한다.
15. 홀드는 유효한 `ACTIVE` 상태이고 예약 확정이 가능한 상태여야 한다. 같은 요청의 성공 재시도만 기존 `CONFIRMED` 예약 결과를 반환한다.
16. 예약당 쿠폰 사용 이력은 최대 하나이며, 쿠폰당 활성 `CONFIRMED` 사용 이력도 최대 하나다.
17. `PROCESSING` 멱등 키 점유, 홀드의 `ACTIVE → CONSUMED`, `CONFIRMED` 예약 생성, `RESERVED → USED`, `coupon_redemption(CONFIRMED)` 생성과 `SUCCEEDED` 전이는 하나의 트랜잭션에서 커밋한다.
18. `SUCCEEDED`와 저장 대상인 `FAILED` 멱등 결과는 완료 시각부터 24시간 보관한다. 보관 기간이 지난 뒤 정리되면 이전 HTTP 응답의 재현을 보장하지 않지만, 도메인 상태와 유일 제약으로 중복 예약과 중복 쿠폰 사용을 방지한다.
19. 사용 확정 실패와 멱등 충돌의 사유는 감사 기록과 구조화 로그로 추적한다.
