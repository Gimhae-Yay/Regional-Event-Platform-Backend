## 8. QR 검증·체크인과 방문 자동 생성

소유 운영자가 방문자의 단기 예약 QR을 검증하고 체크인을 완료한다.
성공하면 예약을 `CHECKED_IN`으로 전환하고 `QR` 방식의 방문 기록을 최대 한 건 생성한다.
동일 키 재시도와 이미 체크인된 예약의 새로운 키 재스캔은
[ADR-0101](../../../adr/0101-reject-new-qr-rescans-after-check-in.md)의 결정을 따르고, 기존 검증 우선순위는
[ADR-0102](../../../adr/0102-preserve-qr-check-in-validation-precedence-for-rescan-conflict.md)에 따라 유지한다.
거부된 재스캔의 멱등 결과 참조와 후속 호출은
[ADR-0103](../../../adr/0103-record-rejected-qr-rescans-without-visit-result-or-progress-trigger.md)을 따른다.

### Request

```http
POST /operator/check-ins
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/operator/check-ins
```

#### Request Example

```http
POST /api/v1/operator/check-ins HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
Accept: application/json

{
  "qrToken": "v1.k1.eyJ...signature"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` |
| `Idempotency-Key` | Y | 한 번의 체크인 시도에 대해 클라이언트가 생성한 비어 있지 않은 `checkInRequestId`. 네트워크 재시도에만 같은 값을 사용하고 새로운 스캔에는 새 값을 사용한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "qrToken": "v1.k1.eyJ...signature"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `qrToken` | String | Y | 방문자가 제시한 비어 있지 않은 단기 HMAC QR 토큰 |

#### QR 토큰 프로필

`qrToken`의 직렬화·payload·서명·TTL·키 회전 계약은 [내 예약 QR 조회·발급의 QR 토큰 프로필](get-my-reservation-qr.md#qr-토큰-프로필)을 따른다.
체크인 API는 QR 원문을 변경하지 않고, MySQL 기준 현재 시각으로 해당 프로필의 서명·만료를 검증한다.

### Response

#### Status

```http
200 OK
```

같은 `Idempotency-Key`, 운영자와 QR 토큰으로 완료된 요청을 재시도하면 토큰이 현재 만료됐더라도 방문과 예약을
다시 변경하지 않고 최초 성공과 동일한 `200 OK`와 저장된 비개인 결과를 반환한다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "QR 체크인에 성공했습니다.",
  "data": {
    "visitId": "321",
    "reservationId": "123",
    "sessionId": "456",
    "reservationStatus": "CHECKED_IN",
    "checkInMethod": "QR",
    "checkedAt": "2026-08-01T01:05:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.visitId` | String | 최초 성공에서 생성했거나 동일 `Idempotency-Key` 재시도로 재생한 방문 식별자 |
| `data.reservationId` | String | 체크인된 예약 식별자 |
| `data.sessionId` | String | 체크인된 회차 식별자 |
| `data.reservationStatus` | String | 항상 `CHECKED_IN` |
| `data.checkInMethod` | String | QR 체크인 최초 성공과 그 동일 키 재시도에서 항상 `QR` |
| `data.checkedAt` | String | 최초 방문의 MySQL 기준 체크인 처리 시각. API 공통 규칙에 따른 UTC ISO 8601 사건 시각이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `Idempotency-Key`가 없거나 비어 있거나 `qrToken`이 없거나 공백이다. 멱등 기록, 예약, 방문과 성공 감사 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 멱등 기록, 예약, 방문과 성공 감사 기록을 변경하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `400` | `QR_VERIFICATION_FAILED` | QR 형식·버전·키 식별자·서명·만료 또는 서명된 예약·회차 참조를 검증할 수 없다. 예약과 방문을 변경하지 않으며 새 QR을 발급받아 새 `Idempotency-Key`로 다시 스캔해야 한다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 멱등 기록, 예약과 방문을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 공통 권한 행렬 또는 이 API의 활성 계정·지역·소유권 조건을 충족하지 않는다. 예약과 방문을 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 운영자의 `CHECK_IN` 명령에서 이미 다른 QR·예약·회차·방식에 사용한 `Idempotency-Key`다. 새 방문을 만들지 않으며 새 스캔에는 새 키를 사용해야 한다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 운영자·키·QR 요청의 최초 처리가 아직 진행 중이다. 새 방문을 만들지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `QR_ALREADY_CHECKED_IN` | 새로운 `Idempotency-Key`의 QR이 기존 검증을 모두 통과했지만 예약이 이미 `CHECKED_IN`이고 일치하는 방문이 존재한다. 방문을 추가하거나 기존 방문 성공을 반환하지 않으며 공개 메시지는 `이미 체크인된 QR입니다.`다. |
| `409` | `CHECK_IN_CONFLICT` | 활성 회원 연결이 없거나, 예약이 `CANCELLED`·`EXPIRED`이거나, 회차가 `SCHEDULED`가 아니거나, 체크인 창 밖이거나, 취소·노쇼·다른 체크인 전이가 먼저 성공했다. 방문을 만들지 않으며 현재 예약·회차 상태를 다시 확인해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·회차·콘텐츠·방문 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 성공 멱등 기록, 예약, 방문과 성공 감사 이벤트를 변경하지 않으며 일시적 장애라면 같은 키로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "QR_ALREADY_CHECKED_IN",
  "message": "이미 체크인된 QR입니다.",
  "data": null
}
```

### 처리 규칙

1. Access Token의 `ROLE_OPERATOR` authority를 1차로 확인한다. DB에서는 활성 `ORDINARY` 계정, 현재 담당 `region_id`, 대상 콘텐츠 소유 관계와 회차·예약 상태를 확인한다. `REGION_ADMIN`은 체크인할 수 없다.
2. `Idempotency-Key`와 요청 해시는 토큰의 현재 유효성보다 먼저 확인한다. 같은 운영자·키·요청 의미의 완료 기록이 있으면 현재 토큰 만료 여부를 다시 검사하지 않고 저장 결과를 반환한다.
3. 최초 요청이나 새로운 키의 스캔은 토큰 형식, 버전, 키 식별자, HMAC 서명과 만료 시각을 순서대로 검증한다.
4. 서명된 `qr_reference`로 예약을 조회하고 토큰의 `session_id`가 예약 회차와 일치하는지 검증한다. 검증 실패의 공개 응답은 대상 존재 여부를 드러내지 않는 `QR_VERIFICATION_FAILED`로 통일한다.
5. 토큰 검증 뒤 예약·회차·콘텐츠의 지역이 인증 운영자의 담당 지역과 일치하고 콘텐츠의 `operator_id`가 인증 운영자와 일치하는지 검증한다.
6. 예약은 활성 회원 연결이 있어야 하고 회차는 `SCHEDULED`여야 하며 MySQL 기준 현재 시각이 `checkin_open_at <= now < checkin_close_at`이어야 한다.
7. 방문이 없으면 예약이 `CONFIRMED`인 경우에만 `CONFIRMED → CHECKED_IN` 조건부 전이를 적용하고 `visit`을 한 건 생성한다. `checkin_method = QR`, `checked_in_by_user_id = 인증 운영자`, `checked_at = MySQL 현재 시각`으로 기록한다.
8. 방문이 이미 있고 예약이 `CHECKED_IN`이며 방문의 예약·회차·콘텐츠·지역 연결이 모두 일치하면, 새로운 키의 재스캔을 `409 QR_ALREADY_CHECKED_IN`으로 거부한다. 기존 방문 결과를 성공으로 반환하지 않고 방문도 추가하지 않는다.
9. 만료 토큰, 권한 없는 운영자, 활성 회원 연결이 제거된 예약과 현재 상태가 유효하지 않은 새로운 스캔은 기존 방문이 있어도 실패한다.
10. `CHECKED_IN` 예약에 방문이 없거나 방문 연결이 다르면 정상 재스캔으로 대체하지 않고 정합성 오류로 처리한다.
11. `visit.reservation_id` 유일 제약으로 예약당 방문을 최대 한 건만 허용한다. 체크인·취소·노쇼가 경합하면 예약 상태 조건부 전이에 먼저 성공한 처리만 반영한다.
12. 체크인은 정원을 차감하거나 복구하지 않으며 홀드, 회차 정원과 기존 방문 이외의 예약을 변경하지 않는다.
13. `QR_VERIFICATION_FAILED`, `QR_ALREADY_CHECKED_IN`, `CHECK_IN_CONFLICT`처럼 같은 토큰·현재 상태에서 결과가 바뀌지 않는 실패는 `idempotency_record.status = FAILED`와 `result_code`로 기록하고 같은 키·같은 요청에 저장 결과를 반환한다. 이미 체크인된 QR 재스캔은 `result_code = QR_ALREADY_CHECKED_IN`이며 결과 방문을 참조하지 않는다.
14. 입력·인증·인가 오류와 트랜잭션 롤백이 필요한 일시적 서버 오류는 완료 멱등 결과로 기록하지 않는다.

### 감사 및 정합성

#### 감사 사유 코드

공개 오류는 위·변조 판별 정보와 내부 상태를 일반화하지만 `audit_event.reason_code`는 다음 코드로 실제 실패
원인을 구분한다.

| 실패 구분 | `reason_code` | 공개 오류 코드 |
| --- | --- | --- |
| 토큰 형식 오류 | `QR_CHECK_IN_MALFORMED` | `QR_VERIFICATION_FAILED` |
| 지원하지 않는 토큰 버전 | `QR_CHECK_IN_VERSION_UNSUPPORTED` | `QR_VERIFICATION_FAILED` |
| 알 수 없거나 폐기된 키 식별자 | `QR_CHECK_IN_KEY_UNKNOWN` | `QR_VERIFICATION_FAILED` |
| HMAC 서명 불일치 | `QR_CHECK_IN_SIGNATURE_INVALID` | `QR_VERIFICATION_FAILED` |
| 토큰 만료 | `QR_CHECK_IN_EXPIRED` | `QR_VERIFICATION_FAILED` |
| 예약 참조 부재 | `QR_CHECK_IN_REFERENCE_INVALID` | `QR_VERIFICATION_FAILED` |
| 토큰 회차와 예약 회차 불일치 | `QR_CHECK_IN_SESSION_MISMATCH` | `QR_VERIFICATION_FAILED` |
| 승인된 운영자 역할 없음 | `QR_CHECK_IN_OPERATOR_ROLE_FORBIDDEN` | `FORBIDDEN` |
| 운영자 담당 지역 불일치 | `QR_CHECK_IN_REGION_FORBIDDEN` | `FORBIDDEN` |
| 콘텐츠 소유 운영자 불일치 | `QR_CHECK_IN_OWNER_FORBIDDEN` | `FORBIDDEN` |
| 예약의 활성 회원 연결 없음 | `QR_CHECK_IN_MEMBER_UNLINKED` | `CHECK_IN_CONFLICT` |
| 취소된 예약 | `QR_CHECK_IN_RESERVATION_CANCELLED` | `CHECK_IN_CONFLICT` |
| 만료·노쇼 예약 | `QR_CHECK_IN_RESERVATION_EXPIRED` | `CHECK_IN_CONFLICT` |
| 취소된 회차 | `QR_CHECK_IN_SESSION_CANCELLED` | `CHECK_IN_CONFLICT` |
| 완료된 회차 | `QR_CHECK_IN_SESSION_COMPLETED` | `CHECK_IN_CONFLICT` |
| 체크인 창 시작 전 | `QR_CHECK_IN_WINDOW_NOT_OPEN` | `CHECK_IN_CONFLICT` |
| 체크인 창 종료 후 | `QR_CHECK_IN_WINDOW_CLOSED` | `CHECK_IN_CONFLICT` |
| 조건부 상태 전이 경합 패배 | `QR_CHECK_IN_STATE_TRANSITION_CONFLICT` | `CHECK_IN_CONFLICT` |
| 새로운 키로 이미 체크인된 예약 재스캔 | `QR_CHECK_IN_RESERVATION_ALREADY_CHECKED_IN` | `QR_ALREADY_CHECKED_IN` |
| 예약·회차·콘텐츠·지역 연결 불일치 | `QR_CHECK_IN_RELATION_INCONSISTENT` | `INTERNAL_SERVER_ERROR` |
| `CHECKED_IN` 예약과 방문 부재·연결 불일치 | `QR_CHECK_IN_VISIT_INCONSISTENT` | `INTERNAL_SERVER_ERROR` |

- 멱등 키 점유, 예약의 `CONFIRMED → CHECKED_IN` 전이, 방문 생성, 성공 멱등 결과와 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 커밋한다.
- 새로운 키로 이미 체크인된 예약을 재스캔한 실패 감사 이벤트는 `target_type = RESERVATION`, `target_id = reservation_id`, `previous_state = CHECKED_IN`, `next_state = null`, `result = FAILURE`, `reason_code = QR_CHECK_IN_RESERVATION_ALREADY_CHECKED_IN`으로 기록한다.
- 인증된 사용자 이벤트는 `actor_kind = USER`, 인증 주체의 현재 역할을 `actor_role`로 기록하고 `audit_event_actor_link`로 처리자를 연결한다. 성공 이벤트의 actor 연결은 체크인 트랜잭션에서 함께 커밋하고, 실패 이벤트의 actor 연결은 롤백 완료 뒤 실패 감사 이벤트와 같은 독립 트랜잭션에서 커밋한다.
- QR 검증·권한·상태 실패는 롤백 완료 뒤 비개인 실패 `audit_event`로 기록한다. 안전하게 예약을 확인했으면 `target_type = RESERVATION`, `target_id = reservation_id`, `region_id = reservation.region_id`로 기록한다. 회차·콘텐츠는 검증된 예약 관계에서 조회한다.
- 예약을 식별하지 못한 실패 이벤트는 `target_type = RESERVATION`, `target_id = null`로 둔다. 인가를 통과한 인증 주체에게 DB상 현재 담당 지역이 있으면 그 지역을 `region_id`로 사용하고, 확인할 수 없으면 `region_id = null`로 기록한다. 검증되지 않은 토큰 payload의 지역은 사용하지 않는다.
- QR 토큰 원문, HMAC 키, `qr_reference`, 사용자 식별자와 이름·연락처는 멱등 기록, 감사 이벤트와 로그에 저장하지 않는다.
- 체크인 명령의 상세한 재시도·재스캔 판정은 [체크인 요청 멱등성](check-in-idempotency.md)을 따른다.
