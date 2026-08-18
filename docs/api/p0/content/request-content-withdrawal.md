# 전체 콘텐츠 철회 요청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-07`, `CON-09`, `SES-02` |
| 소유 도메인 | 운영자 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0101](../../../adr/0101-store-content-withdrawal-requests-and-serialize-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 자신이 소유한 `PUBLISHED` 콘텐츠의 전체 철회를 사유와 함께 요청한다. 이 API는
`content_withdrawal_request.PENDING`을 만들 뿐 콘텐츠 공개 상태, 수정본, 홀드와 예약 가능 상태를 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14`, `CON-07` | `POST /operator/contents/{contentId}/withdrawal-requests` | `content`, `content_withdrawal_request` |
| `AUTH-01`, `CON-09` | `POST /operator/contents/{contentId}/withdrawal-requests` | `user_role_assignment`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}/withdrawal-requests`; 시각은 UTC `Z`, 식별자는 양의 10진 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_OPERATOR` snapshot, 활성 `ORDINARY` 계정, 현재 담당 지역과 대상 콘텐츠 소유 관계가 모두 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 최초 생성과 같은 의미의 재시도는 `201 Created`; 중복 대기·상태 경합은 `CONTENT_STATE_CONFLICT`, 같은 키의 다른 요청 의미는 `IDEMPOTENCY_KEY_CONFLICT` 사용 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 명령 API이므로 적용하지 않음 |

## 3. 전체 콘텐츠 철회 요청

### Request

```http
POST /operator/contents/{contentId}/withdrawal-requests
```

#### Request Example

```http
POST /api/v1/operator/contents/101/withdrawal-requests HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "운영 계획 변경으로 콘텐츠 전체 철회를 요청합니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. `ROLE_OPERATOR` snapshot을 가져야 하며 DB에서 활성 `ORDINARY` 계정인지 확인한다. |
| `Idempotency-Key` | Y | 클라이언트가 생성한 비어 있지 않은 멱등 키. 같은 요청의 재시도에는 같은 값을, 반려 후 의도적인 새 요청에는 새 값을 사용한다. 원문은 저장·로그하지 않는다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 철회 요청할 콘텐츠 식별자. 양의 10진 문자열이어야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "운영 계획 변경으로 콘텐츠 전체 철회를 요청합니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 전체 철회 요청 사유. 앞뒤 공백을 제거한 값이 비어 있으면 안 되며 승인 시 `WITHDRAWN` 콘텐츠 로그 사유로 사용한다. |

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
  "message": "전체 콘텐츠 철회 요청을 등록했습니다.",
  "data": {
    "withdrawalRequestId": "7001",
    "contentId": "101",
    "status": "PENDING",
    "requestReason": "운영 계획 변경으로 콘텐츠 전체 철회를 요청합니다.",
    "requestedAt": "2026-08-16T04:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | 항상 `201` |
| `code` | String | 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.withdrawalRequestId` | String | 생성되거나 재사용된 전체 철회 요청 식별자 |
| `data.contentId` | String | 대상 콘텐츠 식별자 |
| `data.status` | String | 최초 요청 생성 결과인 `PENDING`. 같은 키 재시도에서는 현재 심사 상태가 종결됐어도 최초 생성 응답을 재현한다. |
| `data.requestReason` | String | 저장된 정규화 요청 사유 |
| `data.requestedAt` | String | 최초 요청 생성 시각. 재시도에서도 바뀌지 않는 UTC `Z` 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양수가 아니거나 `Idempotency-Key`가 없거나 비어 있거나 `reason`이 없거나 공백뿐이다. 아무 상태도 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `contentId`를 signed 64비트 양의 정수로 해석할 수 없다. 아무 상태도 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 아무 상태도 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | `ROLE_OPERATOR` authority가 없거나 활성 `ORDINARY` 계정, 담당 지역 또는 대상 콘텐츠 소유 관계가 없다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 콘텐츠에서 이미 다른 정규화 사유에 사용한 `Idempotency-Key`다. 기존 요청을 변경하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 새 키의 요청인데 콘텐츠가 `PUBLISHED`가 아니거나, 같은 콘텐츠에 다른 키의 `PENDING` 요청이 있거나, 경합 중 다른 상태 전이가 먼저 성공했다. |
| `500` | `INTERNAL_SERVER_ERROR` | 요청·감사 저장 중 예상하지 못한 오류가 발생했다. 트랜잭션이 롤백됐으면 상태 변경은 없다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_STATE_CONFLICT",
  "message": "콘텐츠 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 필수 `Idempotency-Key`, 요청 형식과 사유를 검증하고 키 원문의 해시를 계산한다. 원문은 DB·감사·구조화 로그에 기록하지 않는다.
2. 서버는 `ROLE_OPERATOR` snapshot을 1차 확인하고, DB에서 인증 주체의 활성 `ORDINARY` 계정, 현재 담당 `region_id`, 저장된 `content.operator_id`를 검증한다.
3. 쓰기 트랜잭션에서 `region → content` 순서로 잠근 뒤 같은 `(content_id, idempotency_key_hash)` 요청을 먼저 조회한다.
4. 같은 키의 기존 요청이 있으면 현재 상태가 `APPROVED`, `REJECTED`, `INVALIDATED`여도 정규화 요청 사유가 같을 때 새 요청·감사를 만들지 않고 최초 `201 Created` 생성 결과를 반환한다. 응답의 `status`는 현재 심사 상태가 아니라 최초 생성 결과인 `PENDING`이다.
5. 같은 키의 기존 요청에 다른 정규화 사유를 보내면 기존 사유를 덮어쓰지 않고 `409 IDEMPOTENCY_KEY_CONFLICT`를 반환한다.
6. 같은 키의 기존 요청이 없으면 콘텐츠가 소프트 삭제되지 않은 `PUBLISHED`인지 다시 확인한다. 같은 콘텐츠에 다른 키의 `PENDING` 요청이 있으면 사유가 같아도 `409 CONTENT_STATE_CONFLICT`다.
7. 처리 대기 요청이 없으면 요청자, 키 해시, 정규화 사유와 MySQL 기준 시각을 가진 새 요청을 만든다. 동시 생성은 `(content_id, idempotency_key_hash)`와 `active_request_content_id` 유일 제약으로 최종 방어한다.
8. 성공 시 `CONTENT_WITHDRAWAL_REQUEST` 감사 대상에 `NULL → PENDING`, 사유 코드 `CONTENT_WITHDRAWAL_REQUESTED`와 요청 actor를 기록한다.
9. 요청 생성은 콘텐츠, 수정본, 콘텐츠 로그, 회차, 홀드, 정원, 예약, 가격 스냅샷, 결제와 쿠폰을 변경하지 않는다. 새 홀드 생성과 예약 확정도 계속 허용된다.

### 트랜잭션·경합 및 MySQL 검증 조건

- 새 요청 행과 성공 감사 이벤트·actor link는 하나의 MySQL 트랜잭션으로 함께 커밋하거나 롤백한다.
- 같은 키·같은 사유의 동시 요청은 요청 행·감사를 각각 한 건만 만들고 같은 ID의 최초 생성 결과를 반환한다.
- 같은 키·다른 사유는 `IDEMPOTENCY_KEY_CONFLICT`, 다른 키의 동시 요청은 한 건만 생성되고 나머지는 `CONTENT_STATE_CONFLICT`인지 검증한다.
- 반려 뒤 같은 키 재시도는 기존 요청 ID와 최초 생성 결과를 반환하고, 새 키 재요청만 새 요청·감사를 만드는지 검증한다.
- 요청과 운영 중단·종료가 경합하면 콘텐츠 잠금을 먼저 커밋한 상태를 기준으로 한다. 요청이 먼저 생성돼도 뒤의 중단·종료가 같은 트랜잭션에서 요청을 `INVALIDATED`로 종결한다.
- 요청과 수정본 심사·홀드 생성·예약 확정은 콘텐츠 잠금으로 직렬화하지만 요청 자체가 예약 가능 상태를 바꾸지 않으므로, 각 후속 명령의 기존 조건이 유효하면 모두 순차 성공할 수 있다.
