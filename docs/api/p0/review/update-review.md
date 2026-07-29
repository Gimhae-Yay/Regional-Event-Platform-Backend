# 인증 후기 수정 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08 인증 후기](../../../p0/review.md#fr-08-인증-후기), [REV-01](../../../p0/review.md#rev-01), [REV-03](../../../p0/review.md#rev-03), [PRV-02](../../../p0/auth-profile.md#prv-02) |
| 소유 도메인 | 후기 |
| 기준 문서 | [인증 후기](../../../p0/review.md), [제품 PRD](../../../local-stamp-platform-prd.md#85-후기-정책), [ERD](../../../erd.md#5-홀드예약체크인후기-erd), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 작성자 연결을 검증할 수 있는 활성 회원이 등록 후 30일 이내에 자신의 `PUBLISHED` 후기를 부분 수정하는
HTTP API 계약을 정의한다. `rating`, `reviewText` 중 하나 이상을 요청해야 하며, 포함한 값은 후기 작성과 같은
검증 규칙을 만족해야 한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-08, REV-01, REV-03, PRV-02 | `PATCH /api/v1/reviews/{reviewId}` | `review`, `visit`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. `reviewId`는 양의 `Long`이며 응답 시각은 ISO 8601 `+09:00` 오프셋 문자열이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 회원의 Access Token과 `review.user_id` 소유권 검증이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태는 `200 OK`이며, 수정 기간·상태 충돌은 `409 REVIEW_UPDATE_CONFLICT`로 응답한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 인증 후기 수정

서버는 인증 주체가 작성한 `PUBLISHED` 후기만 수정한다. 수정 가능 시각은 MySQL 기준으로
`현재 시각 < created_at + 30일`을 만족할 때다.

### Request

```http
PATCH /api/v1/reviews/{reviewId}
```

#### Request Example

```http
PATCH /api/v1/reviews/901 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "rating": 4,
  "reviewText": "체험 시간이 조금 짧았지만 즐거웠습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이며 대상 후기의 활성 작성자 연결과 같아야 한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reviewId` | Long | Y | 수정할 후기 식별자. 양의 정수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "rating": 4,
  "reviewText": "체험 시간이 조금 짧았지만 즐거웠습니다."
}
```

#### Request Field

`rating`, `reviewText` 중 하나 이상을 포함해야 한다. 포함하지 않은 필드는 기존 값을 유지한다.

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rating` | Integer | N | 포함하면 `1` 이상 `5` 이하의 정수여야 한다. `null`은 허용하지 않는다. |
| `reviewText` | String | N | 포함하면 공백만으로 구성할 수 없고 `1`자 이상 `1,000`자 이하여야 한다. `null`은 허용하지 않는다. |

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
  "message": "후기 수정에 성공했습니다.",
  "data": {
    "reviewId": 901,
    "rating": 4,
    "reviewText": "체험 시간이 조금 짧았지만 즐거웠습니다.",
    "updatedAt": "2026-07-30T15:10:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지. 항상 `후기 수정에 성공했습니다.` |
| `data.reviewId` | Long | 수정한 후기 식별자 |
| `data.rating` | Integer | 수정 후 평점. `1` 이상 `5` 이하 |
| `data.reviewText` | String | 수정 후 후기 텍스트 |
| `data.updatedAt` | String | MySQL 기준 수정 시각의 ISO 8601 `+09:00` 오프셋 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reviewId`가 양수가 아니거나, `rating`·`reviewText`가 모두 없거나, 포함한 필드가 형식·범위 규칙을 위반했다. 후기와 감사 성공 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 JSON으로 역직렬화할 수 없다. 후기와 감사 성공 기록을 변경하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `reviewId`를 `Long`으로 변환할 수 없다. 후기와 감사 성공 기록을 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 후기와 감사 성공 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 후기의 활성 작성자 연결과 일치하지 않는다. 회원 탈퇴로 작성자 연결이 제거된 후기도 포함한다. 후기와 감사 성공 기록을 변경하지 않으며 동일 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 후기를 찾을 수 없다. 후기와 감사 성공 기록을 변경하지 않으며 후기 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `REVIEW_UPDATE_CONFLICT` | 후기가 `DELETED`이거나 MySQL 기준 현재 시각이 `created_at + 30일` 이상이거나, 삭제·탈퇴와의 경합에서 다른 처리가 먼저 성공했다. 후기와 감사 성공 기록을 변경하지 않으며 조건이 변하지 않는 한 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 후기·방문·콘텐츠·지역 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 후기와 감사 성공 기록을 변경하지 않으며 일시적 장애라면 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REVIEW_UPDATE_CONFLICT",
  "message": "후기를 수정할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 하며 `review.user_id`와 같아야 한다.
2. 회원 탈퇴로 `review.user_id`가 제거된 후기에는 기존 계정과 재가입 계정 모두 수정 권한을 얻지 못한다.
3. 대상 후기는 `PUBLISHED` 상태이고 MySQL 기준 `현재 시각 < created_at + 30일`일 때만 수정할 수 있다.
4. `rating`, `reviewText` 중 요청에 포함한 필드만 변경하며, 포함하지 않은 필드는 기존 값을 유지한다.
5. 수정 성공 시 `updated_at`을 MySQL 기준 현재 시각으로 기록한다. `created_at`, `visit_id`, `content_id`, `region_id`, `user_id`, 후기 상태는 변경하지 않는다.
6. 후기 삭제와 회원 탈퇴가 경합하면 활성 회원·작성자 연결·후기 상태 조건을 먼저 성공적으로 변경한 처리만 적용한다.

### 감사 및 정합성

- 후기 값 변경과 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 함께 커밋한다.
- 후기 수정 상태 변경의 실패·거부는 원 트랜잭션을 롤백한 뒤, 별도 MySQL 트랜잭션으로 실패 감사 이벤트를 기록한다. 이 이벤트에는 서버가 검증한 `requestId`, 대상 유형(`REVIEW`), 안전하게 확인된 대상 ID·지역 ID, 결과(`FAILURE`), 실패 코드와 서버가 확인한 상태만 기록한다. 대상 또는 지역을 안전하게 확인할 수 없으면 해당 값은 `null`로 기록한다.
- 별도 실패 감사 이벤트 기록이 실패하면 구조화 로그로만 관찰하며, 감사 기록 실패가 후기 수정의 성공·실패 결과를 바꾸지 않는다.
- 수정 요청은 방문 연결, 콘텐츠·지역 연결과 후기 상태를 변경하지 않는다.
- 실패 감사 이벤트와 구조화 로그에는 후기 원문, 평점, 사용자 식별자, 개인 연결 또는 원시 요청 값을 남기지 않는다.
