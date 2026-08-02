## 5. 예약·노출 종료

담당 지역 관리자가 공개 콘텐츠의 예약 접수와 공개 노출을 정상 종료한다.
성공하면 콘텐츠는 `PUBLISHED`에서 `ENDED`로 전환되고, 신규 홀드 생성과 예약 확정 및 공개 노출이 종료된다.
모든 회차 종결 뒤 시스템이 수행하는 처리는
[모든 회차 종결 콘텐츠 자동 종료](../content-catalog/end-completed-contents.md)를 따르며, 이 API는 같은 조건에서
스케줄러 실행을 기다리지 않고 동일한 종료 결과를 만든다.

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
| `409` | `CONTENT_END_CONFLICT` | 콘텐츠가 `PUBLISHED`도 `ENDED`도 아니거나 `SCHEDULED` 회차가 남아 있거나, 다른 상태 전이가 먼저 성공했다. 이미 `ENDED`인 콘텐츠는 재시도해도 기존 성공 결과를 반환하며, 그 외 충돌은 종료 조건을 충족한 뒤 재시도할 수 있다. |
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
