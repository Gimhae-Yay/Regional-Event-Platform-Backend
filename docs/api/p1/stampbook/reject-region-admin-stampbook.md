# 스탬프북 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 같은 지역의 `PENDING_REVIEW` 스탬프북을 반려하고 `DRAFT`로 되돌린다. 반려 사유는
필수이며, 운영자는 사유를 보완한 뒤 스탬프북을 수정하고 새 심사를 요청할 수 있다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/region-admin/stampbooks/{stampbookId}/reject` | `stampbook`, `stampbook_content`, `content`, `coupon_policy`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

반려·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 상태 변경 명령이므로
페이지네이션과 멱등성 헤더를 적용하지 않는다.

## 3. 스탬프북 반려

### Request

```http
POST /api/v1/region-admin/stampbooks/{stampbookId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/stampbooks/101/reject HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "완료 보상 쿠폰 정책을 공개 상태로 전환한 뒤 다시 요청해 주세요."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `stampbookId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 반려 대상 스탬프북 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "완료 보상 쿠폰 정책을 공개 상태로 전환한 뒤 다시 요청해 주세요."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `reason` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 반려 사유다. 빈 문자열 또는 공백만으로 된 값은 허용하지 않으며 성공 감사 이력에 기록한다. |

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
  "message": "스탬프북 반려에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "status": "DRAFT",
    "rejectedAt": "2026-08-14T03:05:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 반려한 스탬프북 식별자다. |
| `data.status` | String | 반려 뒤 상태인 `DRAFT`다. |
| `data.rejectedAt` | String | 스탬프북 상태 전이와 성공 감사 이벤트에 같은 값으로 기록한 반려 처리 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 상태와 감사 이력을 변경하지 않는다. |
| `400` | `INVALID_INPUT` | `stampbookId`가 범위를 벗어나거나 반려 사유가 누락·공백·500자 초과다. 상태와 감사 이력을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 상태와 감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태와 감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성·승인된 담당 지역의 `REGION_ADMIN`이 아니거나 스탬프북 지역이 다르다. 상태와 감사 이력을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 스탬프북이 없다. 상태와 감사 이력을 변경하지 않는다. |
| `409` | `STAMPBOOK_STATE_CONFLICT` | 잠금 뒤 스탬프북이 `PENDING_REVIEW`가 아니다. 상태와 감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 감사 기록 실패가 발생했다. 트랜잭션이 커밋되지 않은 경우 상태와 감사 이력을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "STAMPBOOK_STATE_CONFLICT",
  "message": "스탬프북 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증 주체가 활성·승인된 `REGION_ADMIN`이고 담당 `region_id`를 보유하는지 확인한다. 스탬프북의 `region_id`와 담당 지역이 다르면 `403 FORBIDDEN`으로 거부한다.
2. 승인과 같은 잠금 순서를 사용한다. 현재 연결된 완료 보상 쿠폰 정책 행을 `PESSIMISTIC_WRITE`로 먼저 잠그고 스탬프북 행을 잠근 뒤, 모든 대상 콘텐츠 행을 `contentId` 오름차순으로 잠근다.
3. 잠금 뒤 완료 보상 정책의 현재 연결·지역·발급 경로·상태와 대상 콘텐츠의 지역·소유 관계를 다시 조회한다. 이 검증 결과는 반려 사유를 판단하는 심사 근거이며, 보상 정책이 아직 `PUBLISHED`가 아니거나 대상 콘텐츠 조건이 맞지 않아도 반려 자체는 막지 않는다. 반려는 운영자가 그 조건을 보완할 수 있도록 `DRAFT`로 되돌리는 처리다.
4. 잠금 뒤 스탬프북이 `PENDING_REVIEW`인 경우에만 DB 현재 시각을 한 번 읽어 `rejectedAt`으로 고정하고 `PENDING_REVIEW → DRAFT`로 전이한다. 같은 스탬프북에 대한 승인·반려 또는 보상 정책 종료 요청은 같은 잠금 순서로 직렬화되므로 최초 하나만 성공하고, 뒤 요청은 잠금 뒤 현재 상태를 다시 확인해 `409 STAMPBOOK_STATE_CONFLICT`를 반환한다.
5. 상태 전이, 대상 `STAMPBOOK`, 처리자, 이전 상태 `PENDING_REVIEW`, 이후 상태 `DRAFT`, 앞뒤 공백을 제거한 반려 사유, 시각 및 서버가 부여한 `requestId`를 가진 성공 `audit_event`와 활성 처리자의 `audit_event_actor_link`를 하나의 트랜잭션으로 기록한다. 하나라도 실패하면 모두 롤백한다.
6. 인증 주체와 대상을 서버 데이터로 안전하게 식별한 뒤 권한·상태 조건 거부 또는 처리 예외가 발생하면 원래 트랜잭션을 먼저 롤백한다. 이어 같은 `requestId`, 확인된 이전 상태, `next_state = null`, 공개 오류 코드와 같은 비개인 `reason_code`를 가진 실패 `audit_event`와 필요한 `audit_event_actor_link`를 독립 트랜잭션으로 기록한다. 잘못된 JSON·경로 식별자·미인증 요청 또는 대상을 안전하게 식별하지 못한 요청에는 실패 감사 이벤트를 만들지 않는다.
7. 실패 감사 기록도 실패하면 원래 HTTP 실패 결과를 바꾸지 않고 `requestId`, 대상 식별자와 비개인 오류 코드만 구조화 로그로 남긴다. 반려 사유 원문과 사용자 식별자는 구조화 로그에 남기지 않는다.
