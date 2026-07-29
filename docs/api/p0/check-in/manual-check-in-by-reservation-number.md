## 3. 예약번호 보조 조회 후 체크인

QR을 표시할 수 없거나 검증에 실패한 현장에서 소유 운영자가 예약번호 보조 조회 결과를 이용해 체크인을 완료한다.
성공하면 예약을 `CHECKED_IN`으로 전환하고 `RESERVATION_NUMBER` 방식의 방문 기록을 한 건 생성한다.

예약번호 조회 응답의 `canCheckIn`은 안내 값일 뿐 권한이나 상태를 고정하지 않는다. 이 명령은 호출 시점의
운영자 소유·지역, 예약·회차·회원 상태와 체크인 창을 모두 다시 검증한다.

### Request

```http
POST /operator/check-ins/manual
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/operator/check-ins/manual
```

#### Request Example

```http
POST /api/v1/operator/check-ins/manual HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
Accept: application/json

{
  "reservationNo": "R202607290001",
  "reason": "QR_SCAN_FAILED"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 해당 콘텐츠의 승인된 소유 운영자여야 한다. |
| `Idempotency-Key` | Y | 한 번의 체크인 시도에 대해 클라이언트가 생성한 비어 있지 않은 `checkInRequestId`. 같은 명령의 네트워크 재시도에는 같은 값을 사용하고 새 시도에는 새 값을 사용한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "reservationNo": "R202607290001",
  "reason": "QR_SCAN_FAILED"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationNo` | String | Y | 보조 조회로 확인한 시스템 전체 유일 예약 번호. 공백만으로 구성할 수 없다. |
| `reason` | String | Y | 현장 보조 체크인 사유. `QR_NOT_AVAILABLE`, `QR_SCAN_FAILED` 중 하나 |

### Response

#### Status

```http
201 Created
```

같은 `Idempotency-Key`, 운영자, 예약번호와 사유로 완료된 요청을 재시도하면 방문과 예약 상태를 다시 변경하지 않고
최초 성공과 동일한 `201 Created`와 저장된 결과를 반환한다.

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "예약번호 보조 체크인에 성공했습니다.",
  "data": {
    "visitId": "321",
    "reservationId": "123",
    "sessionId": "456",
    "reservationStatus": "CHECKED_IN",
    "checkInMethod": "RESERVATION_NUMBER",
    "checkedAt": "2026-08-01T01:05:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.visitId` | String | 생성되었거나 멱등 재사용한 방문 식별자 |
| `data.reservationId` | String | 체크인된 예약 식별자 |
| `data.sessionId` | String | 체크인된 회차 식별자 |
| `data.reservationStatus` | String | 항상 `CHECKED_IN` |
| `data.checkInMethod` | String | 이 명령으로 새 방문을 생성한 경우 항상 `RESERVATION_NUMBER` |
| `data.checkedAt` | String | MySQL 기준 체크인 처리 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `Idempotency-Key`가 없거나 비어 있거나, `reservationNo`가 없거나 공백이거나, `reason`이 허용 값이 아니다. 멱등 기록, 예약, 방문과 감사 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 멱등 기록, 예약, 방문과 감사 기록을 변경하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 멱등 기록, 예약, 방문과 감사 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 예약의 콘텐츠 소유·지역 범위와 일치하지 않는다. 멱등 기록, 예약과 방문을 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | `reservationNo`와 일치하는 예약이 없다. 멱등 기록, 예약과 방문을 변경하지 않으며 예약번호를 확인한 뒤 재시도할 수 있다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 운영자의 `CHECK_IN` 명령에서 이미 다른 예약·방식·사유에 사용한 `Idempotency-Key`다. 방문을 만들지 않으며 같은 키로 재시도할 수 없고 새 명령에는 새 키를 사용해야 한다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 운영자·키·예약번호·방식·사유의 최초 요청이 아직 처리 중이다. 새 방문을 만들지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `CHECK_IN_CONFLICT` | 예약이 `CONFIRMED`가 아니거나 활성 회원 연결이 없거나, 회차가 `SCHEDULED`가 아니거나, 체크인 창 밖이거나, 취소·노쇼·다른 체크인 전이가 먼저 성공했다. 방문을 만들지 않으며 동일 상태에서 재시도해도 성공하지 않으므로 현재 예약·회차 상태를 다시 조회해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·회차·콘텐츠·방문 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 멱등 성공 기록, 예약, 방문과 성공 감사 이벤트를 변경하지 않으며 일시적 장애라면 같은 키로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CHECK_IN_CONFLICT",
  "message": "체크인할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태이며 승인된 `OPERATOR` 역할과 담당 `region_id`를 가진 회원이어야 한다. `REGION_ADMIN`은 이 명령을 호출할 수 없다.
2. `reservation_no`로 예약을 조회한 뒤 예약·회차·콘텐츠의 지역이 인증 운영자의 담당 지역과 일치하고, 콘텐츠의 `operator_id`가 인증 운영자와 일치하는지 검증한다.
3. 보조 조회의 `canCheckIn` 값이나 이전 조회 시각을 체크인 승인 근거로 사용하지 않는다. 이 명령의 MySQL 트랜잭션에서 현재 상태를 다시 검증한다.
4. `reason`은 `QR_NOT_AVAILABLE` 또는 `QR_SCAN_FAILED`여야 하며 감사 기록에는 선택한 사유를 그대로 기록한다.
5. `Idempotency-Key` 헤더 값을 `checkInRequestId`로 사용하며 논리 유일 범위는 `(actor_user_id, operation = CHECK_IN, idempotency_key_hash)`다.
6. 같은 키와 같은 운영자·예약·방식·사유의 `SUCCEEDED` 기록이 있으면 `result_visit_id`로 최초 성공 응답을 재구성한다. 예약 상태 전이와 방문 생성을 다시 실행하지 않는다.
7. 같은 키를 다른 예약·체크인 방식·사유에 재사용하면 `409 IDEMPOTENCY_KEY_CONFLICT`로 거부한다.
8. 같은 키와 같은 요청의 최초 처리가 `PROCESSING`이면 `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`로 응답하고 새 도메인 작업을 실행하지 않는다.
9. 예약은 활성 회원 연결이 있는 `CONFIRMED`, 회차는 `SCHEDULED`여야 하며 MySQL 기준 현재 시각이 `checkin_open_at <= now < checkin_close_at`이어야 한다.
10. 예약·회차·콘텐츠·지역 연결이 일치해야 한다. `CHECKED_IN` 예약에 방문이 없거나 기존 방문의 예약·회차·콘텐츠·지역 연결이 다르면 정상 결과로 대체하지 않고 정합성 오류로 처리한다.
11. 방문이 없으면 예약을 `CONFIRMED → CHECKED_IN`으로 조건부 전이하고 같은 트랜잭션에서 `visit`을 한 건 생성한다. `checkin_method = RESERVATION_NUMBER`, `checked_in_by_user_id = 인증 운영자`, `checked_at = MySQL 현재 시각`으로 기록한다.
12. `visit.reservation_id` 유일 제약으로 같은 예약의 방문을 최대 한 건만 허용한다. 체크인·취소·노쇼가 경합하면 예약 상태 조건부 전이에 먼저 성공한 처리만 반영한다.
13. 방문 생성과 예약 상태 전이 중 하나만 커밋하지 않는다. 성공 `idempotency_record`, 성공 감사 이벤트와 함께 하나의 MySQL 트랜잭션에서 커밋한다.
14. 체크인은 정원을 차감하거나 복구하지 않으며 홀드, 회차 정원과 기존 방문 이외의 예약을 변경하지 않는다.
15. 상태 충돌처럼 재시도해도 같은 현재 상태에서 결과가 바뀌지 않는 실패는 `idempotency_record.status = FAILED`와 `result_code`로 기록하고 같은 키·같은 요청에 저장된 실패 결과를 반환한다.
16. 검증·인증·인가·대상 부재 오류와 트랜잭션 롤백이 필요한 일시적 서버 오류는 성공 멱등 결과로 기록하지 않는다.

### 감사 및 정합성

- 멱등 키 점유, `reservation`의 `CONFIRMED → CHECKED_IN` 전이, `visit` 생성, 성공 멱등 결과와 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 커밋한다.
- 성공 멱등 기록의 `operation`은 `CHECK_IN`, `status`는 `SUCCEEDED`, `result_visit_id`는 방문 식별자로 기록한다. `result_reservation_id`는 `null`이다.
- `request_hash`는 예약 식별자, `RESERVATION_NUMBER` 방식과 사유를 포함한 정규화된 명령 의미로 계산하며 예약번호·이름·연락처 원문을 저장하지 않는다.
- 성공 감사 이벤트는 처리자, 처리자 역할, 예약·회차·콘텐츠·지역·방문 식별자, 보조 체크인 사유, `CONFIRMED → CHECKED_IN`, 처리 시각을 재현할 수 있어야 한다.
- 감사 이벤트와 구조화 로그에는 `reservationNo`, 사용자 식별자, 이름·연락처 원문을 저장하지 않는다.
