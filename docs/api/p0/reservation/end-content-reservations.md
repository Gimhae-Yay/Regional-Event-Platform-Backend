## 5. 예약·노출 종료

담당 지역 관리자가 공개 콘텐츠의 예약 접수와 공개 노출을 정상 종료한다.
성공하면 콘텐츠는 `PUBLISHED`에서 `ENDED`로 전환되고, 신규 홀드 생성과 예약 확정 및 공개 노출이 종료된다.
모든 회차 종결 뒤 시스템이 수행하는 처리는
[모든 회차 종결 콘텐츠 자동 종료](../content-catalog/end-completed-contents.md)를 따르며, 이 API는 같은 조건에서
스케줄러 실행을 기다리지 않고 동일한 종료 결과를 만든다.

`EndContentReservationsController`는 `EndContentReservationsUseCase.endByRegionAdmin`만 호출한다. 이 UseCase가
권한을 확인하고 콘텐츠·회차·로그·홀드 담당 Service와 감사 기록 담당 객체를 조정하며, 자동 종료도 같은
UseCase의 `endBySystem`을 사용한다.

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
| `contentId` | String | Y | 종료할 콘텐츠 식별자. 양수여야 한다. |

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
    "contentId": "123",
    "status": "ENDED",
    "endedAt": "2026-07-29T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.contentId` | String | 종료된 콘텐츠 식별자 |
| `data.status` | String | 콘텐츠 상태. 항상 `ENDED` |
| `data.endedAt` | String | `content_log.status = ENDED`인 로그의 `date`를 공통 시각 형식으로 표현한 종료 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 없거나 형식·범위가 올바르지 않다. 상태 변경은 발생하지 않으며 재시도 전 요청 값을 수정해야 한다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 상태 변경은 발생하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 지역 관리자가 아니거나, 대상 콘텐츠의 `region_id`가 인증 주체의 담당 지역과 일치하지 않는다. 상태 변경은 발생하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없다. 상태 변경은 발생하지 않으며 콘텐츠 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `CONTENT_END_CONFLICT` | 콘텐츠가 `PUBLISHED`도 `ENDED`도 아니거나 종결되지 않은 회차가 남아 있거나, 다른 상태 전이가 먼저 성공했다. 이미 `ENDED`인 콘텐츠는 재시도해도 기존 성공 결과를 반환하며, 그 외 충돌은 종료 조건을 충족한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 종료 처리 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 상태 변경은 발생하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

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
5. 종료 쓰기 트랜잭션에서 대상 `content` 행을 `PESSIMISTIC_WRITE`(`SELECT ... FOR UPDATE`)로 먼저 잠근다. 잠금을 얻은 뒤 현재 상태가 `PUBLISHED`이고 연결된 회차가 하나 이상이며 모든 회차가 `COMPLETED`, `CANCELLED`, `REJECTED` 중 하나인지 다시 확인하고, 조건을 만족할 때만 `PUBLISHED → ENDED` 전이를 적용한다.
6. `PENDING`, `SCHEDULED` 회차가 하나라도 남아 있으면 종료할 수 없다. `COMPLETED`, `CANCELLED`, `REJECTED`는 종료 판정의 종결 상태다.
7. 기존 `CONFIRMED` 예약을 취소해야 하면 이 API 호출 전에 해당 회차를 명시적으로 취소해야 한다.
8. 종료 성공 후 신규 홀드 생성과 예약 확정을 차단한다.
9. 종료 성공 후 공개 콘텐츠 조회 경로에서 대상 콘텐츠를 노출하지 않는다.
10. 종료 성공 시 남아 있는 `ACTIVE` 홀드는 `INVALIDATED`로 전환하고 정원을 한 번만 복구한다.
11. 콘텐츠 행 잠금 뒤 `PENDING` 전체 철회 요청 행을 잠근다. 있으면 종료 관리자·종료 시각과 `CONTENT_ENDED` 사유를 가진 `INVALIDATED`로 종결하고 요청 상태 전이 감사를 추가한다.
12. 철회 요청 행 뒤 활성 `EDIT_REQUESTED` 수정본 행을 잠가 `EDIT_INVALIDATED`로 전이한다. 무효화 시각과 종료를 처리한 지역 관리자, `CONTENT_ENDED` 사유를 보관하고 원본 콘텐츠를 대상으로 이전·다음 수정본 상태와 사유 코드를 가진 성공 `audit_event`를 추가한다. 활성 수정본이 없으면 수정본 행과 수정본 감사는 추가하지 않는다.
13. 기존 `CONFIRMED`·`CHECKED_IN` 예약, 방문 기록과 후기는 유지한다.
14. 이미 `ENDED`인 콘텐츠에 대한 종료 재요청은 기존 종료 결과를 반환한다.
15. 종료 재요청은 상태 로그, 철회 요청·수정본 무효화와 감사, 정원 복구를 중복 생성하지 않는다.
16. 자동 종료와 추가 회차 생성도 같은 `content` 행을 먼저 잠근다. 회차 생성이 잠금을 먼저 얻으면 새 `PENDING` 회차를 확인하고 `409 CONTENT_END_CONFLICT`로 응답한다. 이 종료가 먼저 `ENDED`를 커밋하면 뒤의 회차 생성은 `ENDED`를 확인하고 회차를 만들지 않는다. 전체 철회 승인과 경합하면 콘텐츠 `PUBLISHED`를 먼저 전이한 하나만 성공한다.
17. 오류가 발생하면 콘텐츠 상태, 철회 요청·수정본 상태와 이력, `content_log`, 성공 `audit_event`, 홀드 상태와 정원을 변경하지 않는다. 수동 종료의 상태 조건 거부나 처리 실패는 종료 트랜잭션이 롤백된 뒤 [ADR-0015](../../../adr/0015-store-audit-events-in-mysql-with-withdrawn-actor-anonymization.md#결정)에 따라 실패 `audit_event`만 별도 트랜잭션으로 기록한다.

### 감사 및 정합성

- `EndContentReservationsUseCase.endByRegionAdmin`이 콘텐츠 한 건의 쓰기 트랜잭션을 소유한다. 성공한 종료는 이 트랜잭션에서 콘텐츠 상태 갱신, 대기 전체 철회 요청·활성 수정본 무효화와 감사, `content_log` 추가, 성공 `audit_event` 기록을 함께 커밋한다.
- `content_log.status`는 `ENDED`로 기록한다.
- `content_log.actor_id`는 처리한 지역 관리자 식별자로 기록한다.
- `content_log.reason`은 `null`로 기록한다.
- `content_log.date`는 응답의 `endedAt`과 동일한 시각으로 기록한다.
- `audit_event`에는 처리자, 처리 시각, 대상 콘텐츠 식별자와 상태 전이 결과를 재현할 수 있는 정보를 기록한다.
- 수동 종료가 `CONTENT_END_CONFLICT`로 거부되거나 처리 중 실패하면 종료 트랜잭션을 먼저 롤백한다. 그 뒤 같은
  `requestId`, 확인된 처리자·대상과 비개인 실패 코드를 가진 `result = FAILURE` 감사 이벤트를 별도 트랜잭션으로
  기록한다. 실패 감사 기록도 실패하면 구조화 로그로 관찰한다.
- `ENDED` 전이, 대기 전체 철회 요청 `INVALIDATED`, 활성 수정본 `EDIT_INVALIDATED`와 각 감사, `ACTIVE` 홀드 무효화 및 정원 복구는 같은 트랜잭션에서 원자적으로 처리한다. 잠금 순서는 `content → content_withdrawal_request → content_revision → content_session → capacity_hold`다.
- 정원 복구는 홀드별 최초 성공 전이에서만 한 번 수행한다.
