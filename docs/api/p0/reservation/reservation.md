# 정원 홀드·무료 예약 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `FR-05`, `FR-06`, `FR-07`, `FR-10`, `FR-11`, `AUTH-01`, `AUTH-03`, `CON-04`, `CON-09`, `SES-01`, `SES-02`, `RSV-01`, `RSV-02`, `RSV-03`, `RSV-04`, `RSV-05`, `RSV-06`, `QR-03`, `QR-05` |
| 소유 도메인 | 예약 |
| 기준 문서 | [정원 홀드·무료 예약](../../../p0/reservation.md), [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 예약 도메인의 요구사항을 HTTP API 계약으로 구체화한다.
요청·응답의 공통 형식, 인증, 페이지네이션, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 해당 API에만 적용되는 값과 규칙만 작성한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-05` | `POST /reservations` | `content_session`, `capacity_hold` |
| `RSV-01` | `POST /reservations` | `capacity_hold.status`, `capacity_hold.expires_at` |
| `RSV-02` | `POST /reservations` | `content_session.remaining_capacity`, `capacity_hold` |
| `FR-06` | `POST /reservation-holds/{holdId}/confirm` | `capacity_hold`, `reservation`, `idempotency_record` |
| `RSV-03` | `POST /reservation-holds/{holdId}/confirm` | `capacity_hold.status`, `reservation`, `idempotency_record` |
| `FR-11` | `POST /reservation-holds/{holdId}/confirm` | `audit_event`, `idempotency_record` |
| `FR-05` | `scheduler` | `capacity_hold.status`, `content_session.remaining_capacity`, `audit_event` |
| `RSV-01` | `scheduler` | `capacity_hold.status`, `capacity_hold.expires_at`, `capacity_hold.capacity_released_at` |
| `FR-06` | `scheduler` | `reservation.status`, `content_session.status`, `audit_event` |
| `RSV-05` | `scheduler` | `reservation.status`, `reservation.expired_at`, `content_session.status` |
| `SES-01` | `scheduler` | `content_session.status`, `content_session.completed_at` |
| `FR-11` | `scheduler` | `audit_event` |
| `FR-06` | `POST /me/reservations/{reservationId}/cancel` | `reservation`, `capacity_hold`, `content_session` |
| `RSV-04` | `POST /me/reservations/{reservationId}/cancel` | `reservation.status`, `reservation.capacity_released_at`, `content_session.remaining_capacity` |
| `FR-06` | `GET /me/reservations/{reservationId}` | `reservation`, `content_session` |
| `FR-07` | `GET /me/reservations/{reservationId}` | `reservation`, `visit`, `content_session` |
| `QR-03` | `GET /me/reservations/{reservationId}` | `reservation.status`, `visit.checked_at` |
| `FR-06` | `GET /me/reservations` | `reservation`, `content_session`, `content` |
| `FR-07` | `GET /me/reservations` | `reservation.status`, `visit.checked_at` |
| `FR-10` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `reservation`, `content_session`, `content`, `audit_event` |
| `AUTH-01` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `AUTH-03` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `app_user.name`, `app_user.phone` |
| `QR-05` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `reservation`, `content_session`, `visit`, `audit_event` |
| `FR-10` | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | `reservation`, `capacity_hold`, `content_session`, `visit` |
| `AUTH-01` | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `AUTH-03` | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | `app_user.name`, `app_user.phone` |
| `FR-04` | `POST /region-admin/contents/{contentId}/end` | `content`, `content_session`, `content_log` |
| `CON-04` | `POST /region-admin/contents/{contentId}/end` | `content.status`, `content_log.status` |
| `CON-09` | `POST /region-admin/contents/{contentId}/end` | `content_log`, `audit_event` |
| `SES-01` | `POST /region-admin/contents/{contentId}/end` | `content_session.status` |
| `SES-02` | `POST /reservations`, `POST /region-admin/contents/{contentId}/end` | `content.status`, `capacity_hold`, `reservation`, `visit`, `review` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1` |
| 인증·인가 | [인증·인가](../../common/authentication.md) | API별 허용 역할, 지역 경계 조건 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 적용하지 않음 |

## 3. 예약 대기 및 정원 홀드 생성

활성 회원이 공개 콘텐츠의 회차와 인원을 선택해 예약 확정 전 정원을 임시 확보한다.
성공하면 회차의 잔여 정원을 조건부로 차감하고 `ACTIVE` 상태의 정원 홀드를 생성한다.

이 API는 예약을 확정하지 않는다. 예약 확정은 생성된 `holdId`를 사용해 별도 API에서 처리한다.

### Request

```http
POST /reservations
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/reservations
```

#### Request Example

```http
POST /api/v1/reservations HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "sessionId": 456,
  "quantity": 2
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "sessionId": 456,
  "quantity": 2
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | Long | Y | 정원을 홀드할 회차 식별자. 양수여야 한다. |
| `quantity` | Integer | Y | 홀드할 인원 수. 1 이상이어야 하며 회차의 남은 정원을 초과할 수 없다. |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "예약 대기 및 정원 홀드 생성에 성공했습니다.",
  "data": {
    "holdId": 789,
    "sessionId": 456,
    "quantity": 2,
    "status": "ACTIVE",
    "expiresAt": "2026-07-29T12:10:00+09:00",
    "createdAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.holdId` | Long | 생성된 정원 홀드 식별자 |
| `data.sessionId` | Long | 홀드 대상 회차 식별자 |
| `data.quantity` | Integer | 홀드한 인원 수 |
| `data.status` | String | 홀드 상태. 항상 `ACTIVE` |
| `data.expiresAt` | String | 홀드 만료 시각. `createdAt + 10분`과 회차 시작 시각 중 더 이른 시각 |
| `data.createdAt` | String | 홀드 생성 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `sessionId` 또는 `quantity`가 없거나 형식·범위가 올바르지 않다. 홀드 생성과 정원 차감은 발생하지 않으며 재시도 전 요청 값을 수정해야 한다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 홀드 생성과 정원 차감은 발생하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 홀드 생성과 정원 차감은 발생하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 예약 생성 권한이 없다. 홀드 생성과 정원 차감은 발생하지 않는다. |
| `404` | `NOT_FOUND` | 대상 회차를 찾을 수 없거나 공개 조회가 허용되지 않는 회차다. 홀드 생성과 정원 차감은 발생하지 않는다. |
| `409` | `RESERVATION_HOLD_CONFLICT` | 콘텐츠가 `PUBLISHED`가 아니거나, 회차가 `SCHEDULED`가 아니거나, 회차 시작 이후이거나, 잔여 정원이 부족하거나, 다른 상태 전이가 먼저 성공했다. 홀드 생성과 정원 차감은 발생하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 홀드 생성 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 홀드 생성과 정원 차감은 발생하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "RESERVATION_HOLD_CONFLICT",
  "message": "예약 대기를 생성할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 회원이어야 한다.
2. 서버는 `sessionId`와 `quantity` 필수 여부, 타입과 범위를 검증한다.
3. 대상 회차가 존재해야 한다.
4. 대상 회차의 콘텐츠가 `PUBLISHED` 상태여야 한다.
5. 대상 회차가 `SCHEDULED` 상태여야 한다.
6. MySQL 기준 현재 시각이 회차 시작 전이어야 한다.
7. `quantity`는 1 이상이어야 하며 대상 회차의 잔여 정원을 초과할 수 없다.
8. 홀드 생성은 `content_session.remaining_capacity >= quantity` 조건부 갱신으로 잔여 정원을 먼저 차감한다.
9. 잔여 정원 차감과 `ACTIVE` 홀드 생성은 하나의 트랜잭션에서 함께 커밋한다.
10. 홀드 만료 시각은 `createdAt + 10분`과 회차 시작 시각 중 더 이른 시각으로 설정한다.
11. 동일 사용자가 같은 회차에 대해 이 API를 반복 호출하면, 잔여 정원이 허용하는 한 별도 홀드를 생성할 수 있다.
12. 이 API는 멱등 키를 받지 않는다. 예약 확정의 멱등성은 별도 예약 확정 API에서 보장한다.
13. 생성된 홀드는 연장하거나 재사용하지 않는다.
14. 오류가 발생하면 홀드 생성과 정원 차감은 모두 반영하지 않는다.

### 감사 및 정합성

- `capacity_hold.status`는 `ACTIVE`로 기록한다.
- `capacity_hold.user_id`는 인증 회원 식별자로 기록한다.
- `capacity_hold.quantity`가 확보 인원의 단일 기준이며, 예약 테이블에 인원을 중복 저장하지 않는다.
- `capacity_hold.expires_at`은 응답의 `expiresAt`과 동일한 시각으로 기록한다.
- 홀드 생성 시 차감된 정원은 `ACTIVE → EXPIRED` 또는 `ACTIVE → INVALIDATED` 최초 전이에서만 한 번 복구한다.
- 홀드 생성 성공은 콘텐츠 상태 로그를 생성하지 않는다.
- 정원 부족, 회차 취소, 콘텐츠 비예약 가능 상태 전이와 경합하면 먼저 성공한 조건부 갱신만 반영한다.

## 4. 활성 홀드의 무료 예약 확정

활성 회원이 자신이 생성한 유효한 `ACTIVE` 홀드를 무료 예약으로 한 번만 확정한다.
성공하면 정원을 추가로 차감하지 않고 홀드를 `CONSUMED`로 전환하며 `CONFIRMED` 예약을 생성한다.

예약 확정은 영속 멱등 처리 대상이다. 동일한 `Idempotency-Key`와 `holdId`를 재전송하면 최초 성공 결과를 반환한다.

### Request

```http
POST /reservation-holds/{holdId}/confirm
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/reservation-holds/{holdId}/confirm
```

#### Request Example

```http
POST /api/v1/reservation-holds/789/confirm HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Idempotency-Key` | Y | 클라이언트가 생성한 비어 있지 않은 멱등 키. 같은 예약 확정의 재시도에는 반드시 같은 값을 사용하고, 새 확정 요청에는 새 값을 사용한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `holdId` | Long | Y | 확정할 정원 홀드 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
201 Created
```

동일한 `Idempotency-Key`와 `holdId`로 완료된 요청을 재시도한 경우에도 최초 성공과 동일하게 `201 Created`와 저장된 결과를 반환한다.

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "무료 예약 확정에 성공했습니다.",
  "data": {
    "reservationId": 123,
    "reservationNo": "R202607290001",
    "holdId": 789,
    "sessionId": 456,
    "status": "CONFIRMED",
    "confirmedAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | Long | 생성된 예약 식별자 |
| `data.reservationNo` | String | 시스템 전체에서 유일한 예약 번호. 형식은 서버가 생성한다. |
| `data.holdId` | Long | 소비된 정원 홀드 식별자 |
| `data.sessionId` | Long | 예약한 회차 식별자 |
| `data.status` | String | 예약 상태. 항상 `CONFIRMED` |
| `data.confirmedAt` | String | 예약 확정 시각 |

예약 QR은 이 API에서 발급하지 않는다. `CONFIRMED` 예약은 체크인 창에서 별도 QR 발급 API를 사용할 수 있다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `holdId`가 양수가 아니거나 `Idempotency-Key`가 없거나 비어 있다. 멱등 기록, 홀드와 예약은 변경하지 않으며 요청 값을 수정해야 한다. |
| `400` | `INVALID_TYPE` | `holdId`의 형식이 올바르지 않다. 멱등 기록, 홀드와 예약은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 멱등 기록, 홀드와 예약은 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 홀드의 소유자가 아니다. 멱등 기록, 홀드와 예약은 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 홀드를 찾을 수 없다. 멱등 기록, 홀드와 예약은 변경하지 않는다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 회원의 `RESERVATION_CONFIRM` 명령에서 이미 다른 `holdId`에 사용한 `Idempotency-Key`다. 새 예약을 만들지 않으며 새 요청에는 새 키를 사용해야 한다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 회원·키·홀드의 최초 요청이 아직 처리 중이다. 새 예약을 만들지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `RESERVATION_CONFIRM_CONFLICT` | 홀드가 유효한 `ACTIVE`가 아니거나, 회원·콘텐츠·회차가 예약 가능하지 않거나, 회차 시작 시각이 도래했거나, 다른 확정·만료·무효화 전이가 먼저 성공했다. 예약은 생성하지 않으며 상태를 확인한 뒤 새 홀드 생성 여부를 판단해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약 확정 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 멱등 성공 기록, 홀드와 예약은 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "RESERVATION_CONFIRM_CONFLICT",
  "message": "예약을 확정할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 홀드의 `user_id`가 일치해야 한다.
3. `holdId`와 `Idempotency-Key`의 필수 여부와 형식을 먼저 검증한다.
4. 멱등 키의 논리 유일 범위는 `(actor_user_id, operation = RESERVATION_CONFIRM, idempotency_key_hash)`다.
5. 같은 멱등 키와 같은 `holdId`의 `SUCCEEDED` 기록이 있으면 해당 `result_reservation_id`로 최초 성공 응답을 재구성해 반환한다. 홀드 소비와 예약 생성을 다시 실행하지 않는다.
6. 같은 멱등 키에 다른 `holdId`를 요청하면 `409 IDEMPOTENCY_KEY_CONFLICT`로 거부한다.
7. 같은 멱등 키와 같은 `holdId`의 최초 요청이 `PROCESSING`이면 `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`로 응답한다. 대기 제한을 넘긴 뒤에도 새 도메인 작업을 실행하지 않는다.
8. 대상 홀드는 유효한 `ACTIVE` 상태여야 하고 `expires_at`이 MySQL 기준 현재 시각보다 미래여야 한다.
9. 대상 콘텐츠는 `PUBLISHED`, 회차는 `SCHEDULED` 상태여야 하며 MySQL 기준 현재 시각이 회차 시작 전이어야 한다.
10. 유효한 홀드는 `ACTIVE → CONSUMED`으로 조건부 전이하고, 같은 트랜잭션에서 `CONFIRMED` 예약을 한 건 생성한다.
11. 예약은 홀드의 `region_id`, `session_id`, `user_id`와 일치시킨다. `reservation.hold_id`의 유일 제약으로 한 홀드당 예약을 최대 한 건만 허용한다.
12. 예약 확정은 홀드 생성 시 이미 확보한 정원을 소비하므로 `content_session.remaining_capacity`를 추가로 차감하거나 복구하지 않는다.
13. `ACTIVE`가 아닌 홀드, 만료·무효화된 홀드, 회차 시작 이후의 홀드는 연장·재사용·재확정할 수 없다.
14. 종결·상태 충돌처럼 재시도해도 결과가 바뀌지 않는 확정 실패는 `idempotency_record.status = FAILED`와 `result_code`로 기록하고, 같은 키·같은 홀드의 재요청에는 저장된 실패 결과를 반환한다.
15. 검증·인증·인가·대상 부재 오류와 트랜잭션 롤백이 필요한 일시적 서버 오류는 성공 멱등 결과로 기록하지 않는다.
16. 서로 다른 멱등 키로 같은 홀드를 동시에 확정하면 `ACTIVE → CONSUMED` 조건부 전이와 `reservation.hold_id` 유일 제약으로 한 요청만 성공한다. 나머지 요청은 `409 RESERVATION_CONFIRM_CONFLICT`로 응답한다.

### 감사 및 정합성

- 멱등 키 점유, `capacity_hold`의 `ACTIVE → CONSUMED` 전이, `reservation` 생성, 성공 `idempotency_record` 기록과 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 커밋한다.
- 성공 멱등 기록의 `operation`은 `RESERVATION_CONFIRM`, `status`는 `SUCCEEDED`, `result_reservation_id`는 생성한 예약 식별자로 기록한다. `result_visit_id`는 `null`이다.
- `request_hash`는 `holdId`를 포함한 정규화된 명령 의미로 계산하며 개인정보 원문을 포함하지 않는다.
- `capacity_hold.terminal_at`과 예약의 `confirmed_at`은 확정 처리 시각으로 기록한다.
- `reservation.qr_reference`는 서버가 생성한 불투명 참조이며, 이름·연락처·`user_id`를 포함하지 않는다.
- 성공 감사 이벤트는 처리자, 홀드·예약 식별자, `ACTIVE → CONSUMED` 및 `CONFIRMED` 결과와 처리 시각을 재현할 수 있어야 한다.
- 확정 실패와 멱등 충돌의 사유는 감사 기록과 구조화 로그로 추적한다.

## 5. 예약·노출 종료

담당 지역 관리자가 공개 콘텐츠의 예약 접수와 공개 노출을 정상 종료한다.
성공하면 콘텐츠는 `PUBLISHED`에서 `ENDED`로 전환되고, 신규 홀드 생성과 예약 확정 및 공개 노출이 종료된다.

### Request

```http
POST /region-admin/contents/{contentId}/end
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/region-admin/contents/{contentId}/end
```

#### Request Example

```http
POST /api/v1/region-admin/contents/123/end HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 지역 관리자여야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 종료할 콘텐츠 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "콘텐츠 예약·노출 종료에 성공했습니다.",
  "data": {
    "contentId": 123,
    "status": "ENDED",
    "endedAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.contentId` | Long | 종료된 콘텐츠 식별자 |
| `data.status` | String | 콘텐츠 상태. 항상 `ENDED` |
| `data.endedAt` | String | `content_log.status = ENDED`인 로그의 `date`를 공통 시각 형식으로 표현한 종료 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 없거나 형식·범위가 올바르지 않다. 상태 변경은 발생하지 않으며 재시도 전 요청 값을 수정해야 한다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 상태 변경은 발생하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 지역 관리자가 아니거나, 대상 콘텐츠의 `region_id`가 인증 주체의 담당 지역과 일치하지 않는다. 상태 변경은 발생하지 않는다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없다. 상태 변경은 발생하지 않는다. |
| `409` | `CONTENT_END_CONFLICT` | 콘텐츠가 `PUBLISHED`도 `ENDED`도 아니거나 `SCHEDULED` 회차가 남아 있거나, 다른 상태 전이가 먼저 성공했다. 이미 `ENDED`인 콘텐츠의 종료 재요청은 기존 성공 결과를 반환한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 종료 처리 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 상태 변경은 발생하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_END_CONFLICT",
  "message": "콘텐츠를 종료할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 지역 관리자여야 한다.
2. 대상 콘텐츠의 `region_id`가 인증 주체의 담당 지역과 일치해야 한다.
3. 콘텐츠가 존재하지 않으면 `404 NOT_FOUND`로 응답한다.
4. 담당 지역이 일치하지 않으면 `403 FORBIDDEN`으로 응답한다.
5. 콘텐츠의 현재 상태가 `PUBLISHED`이고 모든 회차가 `COMPLETED` 또는 `CANCELLED`인 경우에만 `PUBLISHED → ENDED` 전이를 적용한다.
6. `SCHEDULED` 회차가 하나라도 남아 있으면 종료할 수 없다.
7. 기존 `CONFIRMED` 예약을 취소해야 하면 이 API 호출 전에 해당 회차를 명시적으로 취소해야 한다.
8. 종료 성공 후 신규 홀드 생성과 예약 확정을 차단한다.
9. 종료 성공 후 공개 콘텐츠 조회 경로에서 대상 콘텐츠를 노출하지 않는다.
10. 종료 성공 시 남아 있는 `ACTIVE` 홀드는 `INVALIDATED`로 전환하고 정원을 한 번만 복구한다.
11. 기존 `CONFIRMED`·`CHECKED_IN` 예약, 방문 기록과 후기는 유지한다.
12. 이미 `ENDED`인 콘텐츠에 대한 종료 재요청은 기존 종료 결과를 반환한다.
13. 종료 재요청은 상태 로그, 감사 로그, 정원 복구를 중복 생성하지 않는다.
14. 다른 상태 전이와 경합하면 먼저 성공한 조건부 전이만 반영하고 나중 요청은 `409 CONTENT_END_CONFLICT`로 응답한다.
15. 오류가 발생하면 콘텐츠 상태, 콘텐츠 로그, 감사 로그, 홀드 상태와 정원을 변경하지 않는다.

### 감사 및 정합성

- 성공한 종료는 하나의 트랜잭션에서 콘텐츠 상태 갱신, `content_log` 추가, 성공 `audit_event` 기록을 함께 커밋한다.
- `content_log.status`는 `ENDED`로 기록한다.
- `content_log.actor_id`는 처리한 지역 관리자 식별자로 기록한다.
- `content_log.reason`은 `null`로 기록한다.
- `content_log.date`는 응답의 `endedAt`과 동일한 시각으로 기록한다.
- `audit_event`에는 처리자, 처리 시각, 대상 콘텐츠 식별자와 상태 전이 결과를 재현할 수 있는 정보를 기록한다.
- `ENDED` 전이와 `ACTIVE` 홀드 무효화 및 정원 복구는 같은 트랜잭션에서 원자적으로 처리한다.
- 정원 복구는 홀드별 최초 성공 전이에서만 한 번 수행한다.

## 6. 홀드 만료·무효화와 정원 1회 복구

이 기능은 외부 클라이언트가 호출하는 HTTP API가 아닌 내부 스케줄러 작업이다.
실행 경로 식별자는 `scheduler`이며, 외부 URL·인증 헤더·JSON 요청과 응답은 제공하지 않는다.

스케줄러는 만료된 활성 홀드와 회차 시작 시각이 지난 활성 홀드를 종결한다. 콘텐츠의 비예약 가능 전이, 회차 취소,
회원 탈퇴에 따른 무효화는 각 상태 전이를 처리하는 트랜잭션에서 즉시 수행하며, 이 스케줄러가 이를 대체하지 않는다.

### 실행 계약

| 항목 | 계약 |
| --- | --- |
| 실행 경로 | `scheduler` |
| 실행 주체 | 애플리케이션 내부 스케줄러 |
| 외부 HTTP 경로 | 없음 |
| 인증·인가 | 없음. 외부 요청을 받지 않는다. |
| 요청 본문·응답 본문 | 없음 |
| 시간 기준 | 애플리케이션 서버 시계가 아닌 MySQL 현재 시각 |
| 실행 주기 | 운영 설정으로 관리한다. 지연되더라도 예약 확정 경로가 `expires_at`을 함께 검증해 만료 홀드를 확정하지 않는다. |

### 처리 대상과 상태 전이

| 구분 | 대상 조건 | 상태 전이 | 정원 처리 |
| --- | --- | --- | --- |
| 만료 | `status = ACTIVE`이고 `expires_at <= MySQL 현재 시각` | `ACTIVE → EXPIRED` | `remaining_capacity`에 `quantity`를 한 번 더한다. |
| 회차 시작 | `status = ACTIVE`이고 `content_session.starts_at <= MySQL 현재 시각` | `ACTIVE → INVALIDATED` | `remaining_capacity`에 `quantity`를 한 번 더한다. |
| 콘텐츠 비예약 가능 전이 | 콘텐츠가 `SUSPENDED`, `WITHDRAWN`, `ENDED`로 전이 | `ACTIVE → INVALIDATED` | 콘텐츠 상태 전이 트랜잭션에서 한 번 복구한다. |
| 회차 취소 | 회차가 `CANCELLED`로 전이 | `ACTIVE → INVALIDATED` | 회차 취소 트랜잭션에서 한 번 복구한다. |
| 회원 탈퇴 | 회원이 `WITHDRAWING`으로 전이 | `ACTIVE → INVALIDATED` | 회원 탈퇴 트랜잭션에서 한 번 복구한다. |

### 처리 규칙

1. 스케줄러는 `capacity_hold(status, expires_at)` 인덱스를 기준으로 대상 후보를 조회한다.
2. 만료 판정은 `expires_at <= MySQL 현재 시각`과 `status = ACTIVE`를 함께 조건으로 사용한다. 애플리케이션 서버 시계, 스케줄러 실행 시각 또는 캐시 값을 기준으로 판정하지 않는다.
3. 회차 시작 시 남아 있는 활성 홀드는 `content_session.starts_at <= MySQL 현재 시각`과 `status = ACTIVE` 조건으로 `INVALIDATED` 처리한다.
4. 홀드의 종결 상태 전이는 반드시 `ACTIVE`를 조건으로 수행한다. 이미 `CONSUMED`, `EXPIRED`, `INVALIDATED`인 홀드는 변경하지 않는다.
5. `ACTIVE → EXPIRED` 또는 `ACTIVE → INVALIDATED`에 성공한 경우에만 같은 트랜잭션에서 회차의 `remaining_capacity`를 홀드의 `quantity`만큼 복구한다.
6. 정원 복구 시 `0 <= remaining_capacity <= capacity`를 유지해야 한다. 홀드 상태 전이만 또는 정원 갱신만 커밋되는 상태를 허용하지 않는다.
7. 종결 홀드에는 `terminal_at`을 기록한다. `EXPIRED` 또는 `INVALIDATED` 홀드에는 같은 트랜잭션에서 `capacity_released_at`도 기록한다.
8. 만료 처리에서 `invalidation_reason`은 기록하지 않는다. 무효화 처리에서는 회차 시작, 콘텐츠 상태 전이, 회차 취소 또는 회원 탈퇴의 원인을 기록한다.
9. 콘텐츠 비예약 가능 전이, 회차 취소와 회원 탈퇴는 해당 명령이 성공한 트랜잭션에서 활성 홀드를 즉시 `INVALIDATED`로 전환한다. 스케줄러는 이 처리를 지연 보정하는 유일한 경로가 아니다.
10. 스케줄러 실행, 예약 확정, 콘텐츠 상태 전이, 회차 취소와 회원 탈퇴가 경합하면 `status = ACTIVE` 조건부 전이에 먼저 성공한 처리만 상태 변경과 정원 복구를 반영한다.
11. 예약 확정이 먼저 `ACTIVE → CONSUMED`에 성공한 홀드는 정원을 복구하지 않는다. 만료 또는 무효화가 먼저 성공한 홀드는 예약 확정을 허용하지 않는다.
12. 하나의 후보 처리에서 예외가 발생하면 해당 후보의 홀드 종결과 정원 복구를 함께 롤백한다. 다음 스케줄러 실행에서 같은 후보를 재시도할 수 있다.
13. 스케줄러는 외부 API 응답을 만들지 않는다. 처리 건수와 실패 사유는 구조화 로그와 감사 기록으로 관찰한다.

### 감사 및 정합성

- 성공한 만료·무효화는 홀드 식별자, 회차·지역·콘텐츠 식별자, 이전·이후 상태, 복구 인원, 원인과 MySQL 기준 처리 시각을 재현할 수 있도록 감사 기록에 남긴다.
- 시스템이 수행한 만료·회차 시작 무효화의 감사 이벤트에는 사용자 처리자를 연결하지 않는다.
- 만료·무효화로 복구한 정원 수는 해당 홀드의 `quantity`와 같아야 하며, `capacity_released_at`이 이미 있는 홀드에 대해 다시 복구하지 않는다.
- 스케줄러가 중복 실행되거나 다중 인스턴스에서 동시에 실행돼도 `ACTIVE` 조건부 전이와 같은 트랜잭션의 정원 갱신으로 홀드별 복구는 최대 한 번만 발생한다.
- 만료 스케줄러 지연은 물리적 정원 반환을 늦출 수 있지만, 예약 확정 경로의 `expires_at` 검증을 우회하거나 만료 홀드를 확정하게 해서는 안 된다.

## 7. 노쇼 전환과 회차 완료 처리

이 기능은 외부 클라이언트가 호출하는 HTTP API가 아닌 내부 스케줄러 작업이다.
실행 경로 식별자는 `scheduler`이며, 외부 URL·인증 헤더·JSON 요청과 응답은 제공하지 않는다.

스케줄러는 회차가 종료되고 체크인 창이 닫힌 뒤에도 남아 있는 `CONFIRMED` 예약을 노쇼 `EXPIRED`로 전환한다.
해당 회차의 노쇼 처리가 끝나면 회차를 `COMPLETED`로 전환한다.

### 실행 계약

| 항목 | 계약 |
| --- | --- |
| 실행 경로 | `scheduler` |
| 실행 주체 | 애플리케이션 내부 스케줄러 |
| 외부 HTTP 경로 | 없음 |
| 인증·인가 | 없음. 외부 요청을 받지 않는다. |
| 요청 본문·응답 본문 | 없음 |
| 시간 기준 | 애플리케이션 서버 시계가 아닌 MySQL 현재 시각 |
| 실행 주기 | 운영 설정으로 관리한다. 스케줄러 지연은 노쇼 전환을 늦출 수 있지만 체크인 창 종료 전 노쇼 전환을 허용하지 않는다. |

### 처리 대상과 상태 전이

| 구분 | 대상 조건 | 상태 전이 | 정원 처리 |
| --- | --- | --- |
| 노쇼 전환 | 회차가 `SCHEDULED`, `ends_at <= MySQL 현재 시각`, `checkin_close_at <= MySQL 현재 시각`이고 예약이 `CONFIRMED` | `CONFIRMED → EXPIRED` | 정원을 복구하지 않는다. `capacity_released_at`은 `null`로 유지한다. |
| 회차 완료 | 노쇼 처리 후 회차가 `SCHEDULED`이고 남은 `CONFIRMED` 예약이 없음 | `SCHEDULED → COMPLETED` | 정원을 변경하지 않는다. |

### 처리 규칙

1. 노쇼 판정은 `ends_at <= MySQL 현재 시각`과 `checkin_close_at <= MySQL 현재 시각`을 모두 만족할 때만 수행한다.
2. `checkin_close_at`은 `ends_at`보다 이르지 않으므로, 두 조건을 모두 명시적으로 검사해 회차 종료 전 또는 체크인 창 종료 전 노쇼 전환을 막는다.
3. 대상 예약은 `status = CONFIRMED`를 조건으로 `CONFIRMED → EXPIRED`로 전이한다. 이미 `CHECKED_IN`, `CANCELLED`, `EXPIRED`인 예약은 변경하지 않는다.
4. 노쇼 전환에 성공하면 `reservation.expired_at`을 MySQL 기준 처리 시각으로 기록한다. `capacity_released_at`은 기록하지 않으며 회차의 `remaining_capacity`도 변경하지 않는다.
5. 회차의 모든 노쇼 대상 처리가 끝난 뒤, 남아 있는 `CONFIRMED` 예약이 없는 경우에만 `SCHEDULED → COMPLETED`를 조건부로 전이하고 `content_session.completed_at`을 기록한다.
6. 회차 완료는 `CHECKED_IN`, `CANCELLED`, `EXPIRED` 예약과 방문 기록을 변경하지 않는다.
7. 체크인과 노쇼 처리는 MySQL 현재 시각과 예약 상태를 조건으로 수행한다. 체크인 창 종료 경계에서 하나만 먼저 성공하며, 노쇼가 먼저 성공한 예약은 새 체크인을 허용하지 않는다.
8. 회차 취소와 노쇼·완료 처리가 경합하면 `content_session.status = SCHEDULED`와 예약 상태의 조건부 전이에 먼저 성공한 처리만 반영한다. 회차 취소가 먼저 성공하면 스케줄러는 해당 회차를 처리하지 않는다.
9. 노쇼 또는 회차 완료가 먼저 성공하면 회차 취소는 이미 종결된 상태를 다시 전이하지 않는다.
10. 회차 단위로 예약 노쇼 전환과 `COMPLETED` 전이가 중간에 분리되어 커밋되지 않도록 처리한다. 오류가 발생하면 해당 회차의 변경을 롤백하고 다음 스케줄러 실행에서 재시도할 수 있다.
11. 스케줄러가 중복 실행되거나 다중 인스턴스에서 동시에 실행돼도 `CONFIRMED`와 `SCHEDULED` 조건부 전이로 각 예약의 노쇼 처리와 회차 완료는 최대 한 번만 발생한다.
12. 스케줄러는 외부 API 응답을 만들지 않는다. 노쇼·완료 처리 건수와 실패 사유는 구조화 로그와 감사 기록으로 관찰한다.

### 감사 및 정합성

- 성공한 노쇼 전환은 예약·회차·지역·콘텐츠 식별자, 이전·이후 상태, 처리 시각과 노쇼 사유를 재현할 수 있도록 감사 기록에 남긴다.
- 성공한 회차 완료는 회차 식별자, `SCHEDULED → COMPLETED` 전이와 처리 시각을 감사 기록에 남긴다.
- 시스템이 수행한 노쇼·회차 완료 처리의 감사 이벤트에는 사용자 처리자를 연결하지 않는다.
- 노쇼 처리로 `reservation.status = EXPIRED`가 되면 `expired_at`은 존재하고 `capacity_released_at`은 `null`이어야 한다.
- 회차 완료 시 `completed_at`은 존재해야 하며, 같은 회차에 `CONFIRMED` 예약이 남아 있으면 `COMPLETED` 전이를 허용하지 않는다.

## 8. 예약 취소

활성 회원이 본인 소유의 `CONFIRMED` 무료 예약 전체를 회차 시작 전 취소한다.
성공하면 예약을 `CANCELLED`로 전환하고, 홀드가 확보했던 인원만큼 회차 정원을 한 번 복구한다.

부분 취소와 인원 변경은 제공하지 않는다. 인원을 바꾸려면 예약 전체를 취소한 뒤 새 홀드와 예약을 생성해야 한다.

### Request

```http
POST /me/reservations/{reservationId}/cancel
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/me/reservations/{reservationId}/cancel
```

#### Request Example

```http
POST /api/v1/me/reservations/123/cancel HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | Long | Y | 취소할 예약 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음. 사용자 취소 사유는 서버가 `USER_REQUEST`로 기록한다.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

이미 `CANCELLED`인 본인 예약의 취소 재요청은 최초 취소 결과를 반환한다. 기존 취소 사유와 정원 복구 시각은 변경하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "예약 취소에 성공했습니다.",
  "data": {
    "reservationId": 123,
    "sessionId": 456,
    "status": "CANCELLED",
    "cancellationReason": "USER_REQUEST",
    "cancelledAt": "2026-07-29T12:00:00+09:00",
    "capacityReleasedAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | Long | 취소된 예약 식별자 |
| `data.sessionId` | Long | 예약이 속한 회차 식별자 |
| `data.status` | String | 예약 상태. 항상 `CANCELLED` |
| `data.cancellationReason` | String | 최초 취소 사유. 이 API로 최초 취소한 경우 `USER_REQUEST` |
| `data.cancelledAt` | String | 최초 취소 시각 |
| `data.capacityReleasedAt` | String or null | 최초 정원 복구 시각. 회차 시작 이후의 회차 취소 등 정원을 복구하지 않은 취소는 `null` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationId`가 양수가 아니다. 예약과 회차 정원은 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `reservationId`의 형식이 올바르지 않다. 예약과 회차 정원은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 예약과 회차 정원은 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 예약의 소유자가 아니다. 예약과 회차 정원은 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약을 찾을 수 없다. 예약과 회차 정원은 변경하지 않는다. |
| `409` | `RESERVATION_CANCEL_CONFLICT` | 예약이 `CONFIRMED` 또는 `CANCELLED`이 아니거나, `CONFIRMED` 예약에 대해 회차 시작 시각이 도래했거나, 체크인·노쇼가 먼저 성공했다. 이미 `CANCELLED`인 예약은 저장된 최초 취소 결과를 반환한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약 취소 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 예약과 회차 정원은 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "RESERVATION_CANCEL_CONFLICT",
  "message": "예약을 취소할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 예약의 `user_id`가 일치해야 한다.
3. 대상 예약이 이미 `CANCELLED`이면 저장된 `cancellation_reason`, `cancelled_at`, `capacity_released_at`을 포함한 최초 취소 결과를 반환하며 상태, 취소 사유와 정원을 다시 변경하지 않는다.
4. 최초 취소는 예약이 `CONFIRMED` 상태이고 MySQL 기준 현재 시각이 회차 시작 전일 때만 허용한다.
5. 최초 취소는 `reservation.status = CONFIRMED`와 `content_session.starts_at > MySQL 현재 시각`을 조건으로 `CONFIRMED → CANCELLED` 전이를 적용한다.
6. 예약 상태 전이와 같은 트랜잭션에서 회차의 `remaining_capacity`를 연결 홀드의 `quantity`만큼 한 번 복구한다.
7. 취소 성공 시 `cancelled_at`, `cancellation_reason = USER_REQUEST`, `capacity_released_at`을 같은 MySQL 기준 시각으로 기록한다.
8. 정원 복구는 최초 `CONFIRMED → CANCELLED` 전이에만 수행한다. 취소 재요청과 다른 종결 상태 전이는 정원을 다시 변경하지 않는다.
9. 회차 시작 이후 취소, 노쇼와 `CHECKED_IN` 전이는 정원을 복구하지 않는다. 이 API는 회차 시작 이후 취소 자체를 허용하지 않는다.
10. 체크인, 노쇼, 회차 취소, 회원 탈퇴와 취소가 경합하면 예약과 회차 상태의 조건부 전이에 먼저 성공한 처리만 반영한다.
11. `CHECKED_IN` 또는 `EXPIRED` 예약은 이 API로 취소할 수 없다.
12. 취소 성공 후 예약 QR 발급과 새 체크인을 차단한다. 기존 방문 기록과 후기는 변경하지 않는다.

### 감사 및 정합성

- 성공한 취소는 예약 상태 갱신, 회차 정원 복구와 성공 감사 이벤트를 하나의 MySQL 트랜잭션에서 함께 커밋한다.
- 감사 이벤트에는 처리자, 예약·홀드·회차·지역·콘텐츠 식별자, `CONFIRMED → CANCELLED` 전이, `USER_REQUEST` 사유와 처리 시각을 재현할 수 있도록 기록한다.
- 예약의 `capacity_released_at`은 회차 정원 복구가 성공한 최초 취소 시각과 같아야 한다.
- `capacity_hold.quantity`가 확보 인원의 단일 기준이며 예약에 인원을 중복 저장하지 않는다.
- `remaining_capacity`는 복구 뒤에도 `0 <= remaining_capacity <= capacity`를 유지해야 한다.
- 취소 실패와 경합 사유는 감사 기록과 구조화 로그로 추적한다.

## 9. 예약 상세 조회

활성 회원이 본인 소유 예약의 예약 상태, 회차 일정·운영 상태와 체크인 완료 상태를 조회한다.
이 API는 조회 전용이며 예약, 방문, 회차, 홀드와 정원 상태를 변경하지 않는다.

### Request

```http
GET /me/reservations/{reservationId}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/me/reservations/{reservationId}
```

#### Request Example

```http
GET /api/v1/me/reservations/123 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | Long | Y | 조회할 예약 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "예약 상세 조회에 성공했습니다.",
  "data": {
    "reservation": {
      "reservationId": 123,
      "reservationNo": "R202607290001",
      "status": "CONFIRMED",
      "confirmedAt": "2026-07-29T12:00:00+09:00",
      "cancelledAt": null,
      "cancellationReason": null,
      "expiredAt": null
    },
    "session": {
      "sessionId": 456,
      "contentId": 789,
      "status": "SCHEDULED",
      "startsAt": "2026-08-03T14:00:00+09:00",
      "endsAt": "2026-08-03T16:00:00+09:00",
      "checkinOpenAt": "2026-08-03T13:30:00+09:00",
      "checkinCloseAt": "2026-08-03T16:00:00+09:00"
    },
    "checkIn": {
      "checkedIn": false,
      "checkedAt": null
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservation.reservationId` | Long | 예약 식별자 |
| `data.reservation.reservationNo` | String | 시스템 전체에서 유일한 예약 번호. 형식은 서버가 생성한다. |
| `data.reservation.status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservation.confirmedAt` | String | 예약 확정 시각 |
| `data.reservation.cancelledAt` | String or null | `CANCELLED` 상태의 취소 시각. 그 외 상태에서는 `null` |
| `data.reservation.cancellationReason` | String or null | `CANCELLED` 상태의 취소 사유. 그 외 상태에서는 `null` |
| `data.reservation.expiredAt` | String or null | `EXPIRED` 상태의 노쇼 처리 시각. 그 외 상태에서는 `null` |
| `data.session.sessionId` | Long | 예약이 속한 회차 식별자 |
| `data.session.contentId` | Long | 회차가 속한 콘텐츠 식별자 |
| `data.session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.session.startsAt` | String | 회차 시작 시각 |
| `data.session.endsAt` | String | 회차 종료 시각 |
| `data.session.checkinOpenAt` | String | 체크인 창 시작 시각 |
| `data.session.checkinCloseAt` | String | 체크인 창 종료 시각 |
| `data.checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` |

예약 QR 토큰, `qr_reference`, 사용자 식별자와 다른 예약자의 정보는 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationId`가 양수가 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `reservationId`의 형식이 올바르지 않다. 조회 대상과 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 예약의 소유자가 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약을 찾을 수 없다. 조회 대상과 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약 상세 조회 중 예상하지 못한 서버 오류 또는 예약·방문 연결 정합성 오류가 발생했다. 조회 대상과 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 예약의 `user_id`가 일치해야 한다.
3. `reservationId`의 필수 여부, 타입과 양수 범위를 검증한다.
4. 예약은 어느 상태에서나 조회할 수 있다. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 상태에 따라 nullable 시각과 사유 필드를 반환한다.
5. 회차는 예약의 `session_id`와 일치하는 회차만 반환하며, 예약·회차·콘텐츠·지역의 연결 일치 제약을 유지한다.
6. 예약 상태가 `CHECKED_IN`이면 같은 예약과 회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 그 방문의 `checked_at`을 반환한다.
7. 예약 상태가 `CHECKED_IN`이 아니면 `checkedIn = false`, `checkedAt = null`을 반환한다.
8. `CHECKED_IN` 예약에 방문 기록이 없거나 방문의 예약·회차·콘텐츠·지역 연결이 일치하지 않으면 정상 응답을 만들지 않고 정합성 오류로 관찰한다.
9. 조회 시 예약, 방문, 회차, 홀드, 정원, QR과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 예약·회차·방문 식별자와 결과 코드만 구조화 로그로 남기며, 예약 번호·QR 참조·사용자 식별자와 개인정보 원문을 로그에 남기지 않는다.
- `CHECKED_IN` 상태와 방문 기록 연결의 불일치는 데이터 정합성 오류로 관찰하고, 다른 예약의 정보로 대체해 응답하지 않는다.

## 10. 내 예약 목록 조회

활성 회원이 본인 소유의 모든 예약을 예약 확정 시각 내림차순으로 조회한다.
이 API는 단순 목록이며 P0에서는 페이지네이션, 상태 필터와 사용자 지정 정렬을 적용하지 않는다.
조회 결과가 없으면 빈 배열을 반환하며 예약, 방문, 회차, 홀드와 정원 상태를 변경하지 않는다.

### Request

```http
GET /me/reservations
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/me/reservations
```

#### Request Example

```http
GET /api/v1/me/reservations HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "내 예약 목록 조회에 성공했습니다.",
  "data": {
    "reservations": [
      {
        "reservationId": 123,
        "reservationNo": "R202607290001",
        "status": "CONFIRMED",
        "confirmedAt": "2026-07-29T12:00:00+09:00",
        "content": {
          "contentId": 789,
          "title": "김해 가야문화 체험"
        },
        "session": {
          "sessionId": 456,
          "status": "SCHEDULED",
          "startsAt": "2026-08-03T14:00:00+09:00",
          "endsAt": "2026-08-03T16:00:00+09:00"
        },
        "checkIn": {
          "checkedIn": false,
          "checkedAt": null
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservations` | Array | 내 예약 목록. 결과가 없으면 빈 배열 `[]` |
| `data.reservations[].reservationId` | Long | 예약 식별자 |
| `data.reservations[].reservationNo` | String | 시스템 전체에서 유일한 예약 번호. 형식은 서버가 생성한다. |
| `data.reservations[].status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservations[].confirmedAt` | String | 예약 확정 시각 |
| `data.reservations[].content.contentId` | Long | 예약 콘텐츠 식별자 |
| `data.reservations[].content.title` | String | 예약 콘텐츠 제목 |
| `data.reservations[].session.sessionId` | Long | 예약 회차 식별자 |
| `data.reservations[].session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.reservations[].session.startsAt` | String | 회차 시작 시각 |
| `data.reservations[].session.endsAt` | String | 회차 종료 시각 |
| `data.reservations[].checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.reservations[].checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` |

예약 QR 토큰, `qr_reference`, 사용자 식별자와 다른 예약자의 정보는 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 내 예약 목록 조회 중 예상하지 못한 서버 오류 또는 예약·회차·방문 연결 정합성 오류가 발생했다. 조회 대상과 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증 정보가 없거나 유효하지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 서버는 `reservation.user_id = 인증 회원 식별자` 조건을 만족하는 예약만 조회한다.
3. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 상태의 예약을 모두 포함한다. 콘텐츠 종료·중단 또는 회차 완료·취소 뒤에도 본인 예약 이력은 유지한다.
4. 목록은 `confirmed_at` 내림차순, 같은 시각이면 `reservation_id` 내림차순으로 정렬한다.
5. P0에서는 페이지네이션, 상태 필터와 사용자 지정 정렬을 제공하지 않는다.
6. 예약이 없으면 `data.reservations`에 빈 배열 `[]`을 반환한다.
7. 각 항목은 예약의 `session_id`와 일치하는 회차, 회차의 `content_id`와 일치하는 콘텐츠만 반환한다.
8. 예약 상태가 `CHECKED_IN`이면 같은 예약과 회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 그 방문의 `checked_at`을 반환한다.
9. 예약 상태가 `CHECKED_IN`이 아니면 `checkedIn = false`, `checkedAt = null`을 반환한다.
10. `CHECKED_IN` 예약에 방문 기록이 없거나 방문의 예약·회차·콘텐츠·지역 연결이 일치하지 않으면 정상 목록 항목을 만들지 않고 정합성 오류로 관찰한다.
11. 조회 시 예약, 방문, 회차, 콘텐츠, 홀드, 정원, QR과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 결과 건수와 결과 코드만 구조화 로그로 남기며, 예약 번호·QR 참조·사용자 식별자와 개인정보 원문을 로그에 남기지 않는다.
- 목록의 각 `CHECKED_IN` 상태와 방문 기록 연결의 불일치는 데이터 정합성 오류로 관찰하고, 다른 예약의 정보로 대체해 응답하지 않는다.

## 11. QR 실패 시 예약번호 보조 조회

소유 운영자가 QR 검증 실패 상황에서 예약 번호로 담당 콘텐츠의 예약을 보조 조회한다.
예약 번호는 시스템 전체에서 유일하므로 하나의 `reservationNo`는 정확히 한 예약만 식별한다.

이 API는 예약·방문·체크인·정원을 변경하지 않는다. 다만 QR 실패 보조 조회의 사유, 처리자와 처리 시각은
감사 이벤트로 남긴다.

### Request

```http
GET /operator/reservations/search?reservationNo={reservationNo}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/operator/reservations/search?reservationNo={reservationNo}
```

#### Request Example

```http
GET /api/v1/operator/reservations/search?reservationNo=R202607290001 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Header

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 승인된 소유 운영자여야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationNo` | String | Y | 시스템 전체에서 유일한 예약 번호. 공백만으로 구성할 수 없다. |

#### Request Body

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "예약번호 보조 조회에 성공했습니다.",
  "data": {
    "reservationId": 123,
    "reservationNo": "R202607290001",
    "status": "CONFIRMED",
    "content": {
      "contentId": 789,
      "title": "김해 가야문화 체험"
    },
    "session": {
      "sessionId": 456,
      "status": "SCHEDULED",
      "startsAt": "2026-08-01T10:00:00+09:00",
      "endsAt": "2026-08-01T12:00:00+09:00",
      "checkinOpenAt": "2026-08-01T09:30:00+09:00",
      "checkinCloseAt": "2026-08-01T10:30:00+09:00"
    },
    "participant": {
      "name": "김*수",
      "phone": "010-****-1234"
    },
    "checkIn": {
      "checkedIn": false,
      "canCheckIn": true,
      "checkedAt": null
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | Long | 예약 식별자 |
| `data.reservationNo` | String | 시스템 전체에서 유일한 예약 번호 |
| `data.status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.content.contentId` | Long | 예약 콘텐츠 식별자 |
| `data.content.title` | String | 예약 콘텐츠 제목 |
| `data.session.sessionId` | Long | 예약 회차 식별자 |
| `data.session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.session.startsAt` | String | 회차 시작 시각 |
| `data.session.endsAt` | String | 회차 종료 시각 |
| `data.session.checkinOpenAt` | String | 체크인 가능 시작 시각 |
| `data.session.checkinCloseAt` | String | 체크인 가능 종료 시각 |
| `data.participant.name` | String | 예약자 이름. `김*수` 형식으로 마스킹하며, 작성자 연결이 해제된 경우 `탈퇴한 사용자` |
| `data.participant.phone` | String or null | 예약자 연락처. `010-****-1234` 형식으로 마스킹하며, 작성자 연결이 해제된 경우 `null` |
| `data.checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.checkIn.canCheckIn` | Boolean | 현재 소유권·회차 상태·예약 상태·체크인 창을 모두 만족해 체크인을 시작할 수 있으면 `true` |
| `data.checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` |

`qr_reference`, QR 토큰, 사용자 식별자, 원문 이름·연락처와 다른 예약자의 정보는 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationNo`가 없거나 공백만으로 구성됐다. 예약·방문·체크인·정원과 감사 기록을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자가 아니거나, 조회한 예약의 콘텐츠 소유자 또는 담당 지역이 인증 운영자와 일치하지 않는다. 조회 대상과 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | `reservationNo`와 일치하는 예약이 없다. 예약·방문·체크인·정원과 감사 기록을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·회차·콘텐츠·방문 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 롤백되며 성공 감사 이벤트를 남기지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 승인된 담당 지역의 `OPERATOR`여야 한다.
2. 서버는 `reservation_no`로 예약을 조회한 뒤, 예약의 지역과 콘텐츠의 소유 운영자·지역이 인증 운영자와 일치하는지 검증한다.
3. `reservation_no`의 전역 `UNIQUE` 제약으로 조회 결과는 최대 한 건이다. 동일 번호의 복수 예약 중 하나를 임의로 반환하지 않는다.
4. 이 API의 확인 사유는 항상 `QR_VERIFICATION_FAILED`다. 클라이언트는 별도 사유를 입력하지 않는다.
5. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 상태의 예약을 조회할 수 있다. 다만 `CANCELLED`, `EXPIRED`, `CHECKED_IN` 예약은 `canCheckIn = false`다.
6. `canCheckIn = true`는 예약이 `CONFIRMED`, 회차가 `SCHEDULED`, 현재 MySQL 시각이 `checkin_open_at <= now < checkin_close_at`이고 현재 운영자의 소유권·담당 지역 검증을 모두 통과한 경우에만 반환한다.
7. `CHECKED_IN` 예약은 동일 예약·회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 방문의 `checked_at`을 반환한다.
8. `CHECKED_IN`이 아닌 예약은 `checkedIn = false`, `checkedAt = null`을 반환한다.
9. 예약·방문·회차·콘텐츠·지역의 연결이 일치하지 않으면 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
10. 보조 조회 성공은 예약, 방문, 체크인, 홀드와 정원 상태를 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 성공한 보조 조회는 하나의 트랜잭션에서 `QR_VERIFICATION_FAILED` 사유, 처리 운영자, 예약·회차·콘텐츠·지역 식별자와 MySQL 기준 처리 시각을 `audit_event`에 기록한다.
- 감사 이벤트에는 `reservationNo`, `qr_reference`, 사용자 식별자, 이름·연락처 원문을 저장하지 않는다.
- 보조 조회와 성공 감사 이벤트는 함께 커밋한다. 감사 이벤트 기록에 실패하면 성공 응답을 반환하지 않는다.
- 권한 없음, 대상 없음과 정합성 오류는 예약·방문·체크인·정원 상태를 변경하지 않는다. 구조화 로그에는 `requestId`, 결과 코드와 비개인 식별자만 남긴다.

## 12. 회차별 예약자 목록 및 개인정보 마스킹 조회

소유 운영자가 자신의 콘텐츠에 속한 특정 회차의 예약자 목록을 조회한다. 운영자는 현장 운영과 체크인 준비를 위해
예약 상태, 예약 인원과 체크인 상태를 확인할 수 있으며, 예약자 이름과 연락처는 마스킹된 값만 반환한다.

공통 페이지 계약의 페이지 번호·크기·메타데이터가 아직 확정되지 않았으므로, P0에서는 회차 한 건의 단순 목록을
반환한다. 이 API는 예약·방문·체크인·홀드·정원 상태를 변경하지 않는다.

### Request

```http
GET /operator/contents/{contentId}/reservations?sessionId={sessionId}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/operator/contents/{contentId}/reservations?sessionId={sessionId}
```

#### Request Example

```http
GET /api/v1/operator/contents/789/reservations?sessionId=456 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Header

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 승인된 소유 운영자여야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 예약자 목록을 조회할 콘텐츠 식별자. 양수여야 한다. |

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | Long | Y | `contentId`에 속한 회차 식별자. 양수여야 한다. |

#### Request Body

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "회차별 예약자 목록 조회에 성공했습니다.",
  "data": {
    "contentId": 789,
    "session": {
      "sessionId": 456,
      "status": "SCHEDULED",
      "startsAt": "2026-08-01T10:00:00+09:00",
      "endsAt": "2026-08-01T12:00:00+09:00",
      "checkinOpenAt": "2026-08-01T09:30:00+09:00",
      "checkinCloseAt": "2026-08-01T10:30:00+09:00"
    },
    "reservations": [
      {
        "reservationId": 123,
        "reservationNo": "R202607290001",
        "status": "CONFIRMED",
        "quantity": 2,
        "confirmedAt": "2026-07-29T12:00:00+09:00",
        "participant": {
          "name": "김*수",
          "phone": "010-****-1234"
        },
        "checkIn": {
          "checkedIn": false,
          "checkedAt": null
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.contentId` | Long | 조회한 콘텐츠 식별자 |
| `data.session.sessionId` | Long | 조회한 회차 식별자 |
| `data.session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.session.startsAt` | String | 회차 시작 시각 |
| `data.session.endsAt` | String | 회차 종료 시각 |
| `data.session.checkinOpenAt` | String | 체크인 가능 시작 시각 |
| `data.session.checkinCloseAt` | String | 체크인 가능 종료 시각 |
| `data.reservations` | Array | 회차 예약 목록. 예약이 없으면 빈 배열 `[]` |
| `data.reservations[].reservationId` | Long | 예약 식별자 |
| `data.reservations[].reservationNo` | String | 시스템 전체에서 유일한 예약 번호 |
| `data.reservations[].status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservations[].quantity` | Integer | 예약 확정에 사용한 홀드 인원. 항상 양수 |
| `data.reservations[].confirmedAt` | String | 예약 확정 시각 |
| `data.reservations[].participant.name` | String | 예약자 이름. `김*수` 형식으로 마스킹하며, 사용자 연결이 해제된 경우 `탈퇴한 사용자` |
| `data.reservations[].participant.phone` | String or null | 예약자 연락처. `010-****-1234` 형식으로 마스킹하며, 사용자 연결이 해제된 경우 `null` |
| `data.reservations[].checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.reservations[].checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` |

`qr_reference`, QR 토큰, 사용자 식별자와 이름·연락처 원문은 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId` 또는 `sessionId`가 없거나 양수가 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자가 아니거나 콘텐츠의 소유 운영자 또는 담당 지역이 인증 운영자와 일치하지 않는다. 조회 대상과 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠를 찾을 수 없거나, `sessionId`가 해당 콘텐츠와 지역에 속하지 않는다. 조회 대상과 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·홀드·방문·회차 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 승인된 담당 지역의 `OPERATOR`여야 한다.
2. 서버는 `contentId`로 콘텐츠를 찾은 뒤, 콘텐츠의 `operator_id`와 `region_id`가 인증 운영자의 소유·담당 지역과 일치하는지 검증한다.
3. `sessionId`는 조회한 콘텐츠와 같은 `content_id`, 같은 `region_id`를 가진 회차여야 한다. 일치하지 않으면 대상 부재로 처리한다.
4. 해당 회차의 `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 예약을 모두 반환한다. 상태 필터는 P0 범위에 포함하지 않는다.
5. 목록은 `confirmed_at` 오름차순, 같은 시각이면 `reservation_id` 오름차순으로 정렬한다. 페이지네이션과 사용자 지정 정렬은 P0 범위에 포함하지 않는다.
6. 예약이 없으면 `data.reservations`에 빈 배열 `[]`을 반환한다.
7. 각 예약은 해당 예약의 소비된 홀드와 연결된 `quantity`를 반환한다. 예약·홀드·회차·콘텐츠·지역 연결이 일치하지 않으면 정상 항목으로 대체하지 않고 정합성 오류로 처리한다.
8. 예약 상태가 `CHECKED_IN`이면 동일 예약·회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 방문의 `checked_at`을 반환한다. 그 외 상태는 `checkedIn = false`, `checkedAt = null`을 반환한다.
9. 예약자 이름과 연락처는 `AUTH-03`의 고정 형식으로 마스킹한다. 사용자 연결이 해제된 예약은 `탈퇴한 사용자`, `phone = null`을 반환한다.
10. 조회 시 예약, 홀드, 방문, 회차, 콘텐츠, 정원, QR과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 콘텐츠·회차 식별자, 결과 건수와 결과 코드만 구조화 로그로 남긴다. 예약 번호, QR 참조, 사용자 식별자와 개인정보 원문을 로그에 남기지 않는다.
- 예약의 `CHECKED_IN` 상태와 방문 기록, 예약의 소비된 홀드와 회차·지역 연결이 불일치하면 데이터 정합성 오류로 관찰하고, 다른 예약 정보로 대체해 응답하지 않는다.
