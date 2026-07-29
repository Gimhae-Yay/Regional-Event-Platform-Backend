## 8. QR 검증·체크인과 방문 자동 생성

소유 운영자가 방문자의 단기 예약 QR을 검증하고 체크인을 완료한다.
성공하면 예약을 `CHECKED_IN`으로 전환하고 `QR` 방식의 방문 기록을 최대 한 건 생성한다.

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
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 해당 콘텐츠의 승인된 소유 운영자여야 한다. |
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
    "visitId": 321,
    "reservationId": 123,
    "sessionId": 456,
    "reservationStatus": "CHECKED_IN",
    "checkInMethod": "QR",
    "checkedAt": "2026-08-01T10:05:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.visitId` | Long | 생성했거나 재사용한 방문 식별자 |
| `data.reservationId` | Long | 체크인된 예약 식별자 |
| `data.sessionId` | Long | 체크인된 회차 식별자 |
| `data.reservationStatus` | String | 항상 `CHECKED_IN` |
| `data.checkInMethod` | String | 새 방문을 생성하면 `QR`. 유효한 새 스캔이 기존 방문을 재사용하면 기존 방문의 `QR` 또는 `RESERVATION_NUMBER` |
| `data.checkedAt` | String | 최초 방문의 MySQL 기준 체크인 처리 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `Idempotency-Key`가 없거나 비어 있거나 `qrToken`이 없거나 공백이다. 멱등 기록, 예약, 방문과 성공 감사 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 멱등 기록, 예약, 방문과 성공 감사 기록을 변경하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `400` | `QR_VERIFICATION_FAILED` | QR 형식·버전·키 식별자·서명·만료 또는 서명된 예약·회차 참조를 검증할 수 없다. 예약과 방문을 변경하지 않으며 새 QR을 발급받아 새 `Idempotency-Key`로 다시 스캔해야 한다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 멱등 기록, 예약과 방문을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 서명 검증된 예약의 콘텐츠 소유·지역 범위와 일치하지 않는다. 예약과 방문을 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 운영자의 `CHECK_IN` 명령에서 이미 다른 QR·예약·회차·방식에 사용한 `Idempotency-Key`다. 새 방문을 만들지 않으며 새 스캔에는 새 키를 사용해야 한다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 운영자·키·QR 요청의 최초 처리가 아직 진행 중이다. 새 방문을 만들지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `CHECK_IN_CONFLICT` | 활성 회원 연결이 없거나, 예약이 `CANCELLED`·`EXPIRED`이거나, 회차가 `SCHEDULED`가 아니거나, 체크인 창 밖이거나, 취소·노쇼·다른 체크인 전이가 먼저 성공했다. `CHECKED_IN` 예약과 일치하는 기존 방문을 반환하는 유효한 재스캔은 이 오류에서 제외한다. 방문을 만들지 않으며 현재 예약·회차 상태를 다시 확인해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·회차·콘텐츠·방문 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 성공 멱등 기록, 예약, 방문과 성공 감사 이벤트를 변경하지 않으며 일시적 장애라면 같은 키로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 400,
  "code": "QR_VERIFICATION_FAILED",
  "message": "QR을 확인할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태이며 승인된 `OPERATOR` 역할과 담당 `region_id`를 가진 회원이어야 한다. `REGION_ADMIN`은 체크인할 수 없다.
2. `Idempotency-Key`와 요청 해시는 토큰의 현재 유효성보다 먼저 확인한다. 같은 운영자·키·요청 의미의 완료 기록이 있으면 현재 토큰 만료 여부를 다시 검사하지 않고 저장 결과를 반환한다.
3. 최초 요청이나 새로운 키의 스캔은 토큰 형식, 버전, 키 식별자, HMAC 서명과 만료 시각을 순서대로 검증한다.
4. 서명된 `qr_reference`로 예약을 조회하고 토큰의 `session_id`가 예약 회차와 일치하는지 검증한다. 검증 실패의 공개 응답은 대상 존재 여부를 드러내지 않는 `QR_VERIFICATION_FAILED`로 통일한다.
5. 토큰 검증 뒤 예약·회차·콘텐츠의 지역이 인증 운영자의 담당 지역과 일치하고 콘텐츠의 `operator_id`가 인증 운영자와 일치하는지 검증한다.
6. 예약은 활성 회원 연결이 있어야 하고 회차는 `SCHEDULED`여야 하며 MySQL 기준 현재 시각이 `checkin_open_at <= now < checkin_close_at`이어야 한다.
7. 방문이 없으면 예약이 `CONFIRMED`인 경우에만 `CONFIRMED → CHECKED_IN` 조건부 전이를 적용하고 `visit`을 한 건 생성한다. `checkin_method = QR`, `checked_in_by_user_id = 인증 운영자`, `checked_at = MySQL 현재 시각`으로 기록한다.
8. 방문이 이미 있으면 예약이 `CHECKED_IN`이고 방문의 예약·회차·콘텐츠·지역 연결이 모두 일치할 때만 유효한 새 재스캔으로 인정해 기존 방문 결과를 반환한다.
9. 만료 토큰, 권한 없는 운영자, 활성 회원 연결이 제거된 예약과 현재 상태가 유효하지 않은 새로운 스캔은 기존 방문이 있어도 실패한다.
10. `CHECKED_IN` 예약에 방문이 없거나 방문 연결이 다르면 정상 재스캔으로 대체하지 않고 정합성 오류로 처리한다.
11. `visit.reservation_id` 유일 제약으로 예약당 방문을 최대 한 건만 허용한다. 체크인·취소·노쇼가 경합하면 예약 상태 조건부 전이에 먼저 성공한 처리만 반영한다.
12. 체크인은 정원을 차감하거나 복구하지 않으며 홀드, 회차 정원과 기존 방문 이외의 예약을 변경하지 않는다.
13. `QR_VERIFICATION_FAILED`와 `CHECK_IN_CONFLICT`처럼 같은 토큰·현재 상태에서 결과가 바뀌지 않는 실패는 `idempotency_record.status = FAILED`와 `result_code`로 기록하고 같은 키·같은 요청에 저장 결과를 반환한다.
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
| 예약·회차·콘텐츠·지역 연결 불일치 | `QR_CHECK_IN_RELATION_INCONSISTENT` | `INTERNAL_SERVER_ERROR` |
| `CHECKED_IN` 예약과 방문 부재·연결 불일치 | `QR_CHECK_IN_VISIT_INCONSISTENT` | `INTERNAL_SERVER_ERROR` |

- 멱등 키 점유, 예약의 `CONFIRMED → CHECKED_IN` 전이, 방문 생성, 성공 멱등 결과와 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 커밋한다.
- 유효한 새 재스캔이 기존 방문을 반환한 경우에도 해당 요청의 성공 멱등 기록은 기존 `visit`을 참조할 수 있다. `result_visit_id`에는 유일 제약을 두지 않는다.
- 인증된 사용자 이벤트는 `actor_kind = USER`, 인증 주체의 현재 역할을 `actor_role`로 기록하고 `audit_event_actor_link`로 처리자를 연결한다. 성공 이벤트의 actor 연결은 체크인 트랜잭션에서 함께 커밋하고, 실패 이벤트의 actor 연결은 롤백 완료 뒤 실패 감사 이벤트와 같은 독립 트랜잭션에서 커밋한다.
- QR 검증·권한·상태 실패는 롤백 완료 뒤 비개인 실패 `audit_event`로 기록한다. 안전하게 예약을 확인했으면 `target_type = RESERVATION`, `target_id = reservation_id`, `region_id = reservation.region_id`로 기록한다. 회차·콘텐츠는 검증된 예약 관계에서 조회한다.
- 예약을 식별하지 못한 실패 이벤트는 `target_type = RESERVATION`, `target_id = null`로 둔다. 인증 주체에게 승인된 `OPERATOR` 담당 지역이 있으면 그 지역을 `region_id`로 사용하고, 확인할 수 없으면 `region_id = null`로 기록한다. 검증되지 않은 토큰 payload의 지역은 사용하지 않는다.
- QR 토큰 원문, HMAC 키, `qr_reference`, 사용자 식별자와 이름·연락처는 멱등 기록, 감사 이벤트와 로그에 저장하지 않는다.
- 체크인 명령의 상세한 재시도·재스캔 판정은 [체크인 요청 멱등성](check-in-idempotency.md)을 따른다.
