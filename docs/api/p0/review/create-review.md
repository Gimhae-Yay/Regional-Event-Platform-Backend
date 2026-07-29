# 인증 후기 작성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08 인증 후기](../../../p0/review.md#fr-08-인증-후기), [REV-01](../../../p0/review.md#rev-01), [REV-02](../../../p0/review.md#rev-02), [PRV-02](../../../p0/auth-profile.md#prv-02) |
| 소유 도메인 | 후기 |
| 기준 문서 | [인증 후기](../../../p0/review.md), [제품 PRD](../../../local-stamp-platform-prd.md#85-후기-정책), [ERD](../../../erd.md#5-홀드예약체크인후기-erd), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 체크인 완료 방문 기록에 인증 후기 한 건을 등록하는 HTTP API 계약을 정의한다. 활성 회원이
본인에게 연결된 방문에만 작성할 수 있으며, 같은 방문에는 삭제된 후기를 포함해 새 후기를 다시 만들 수 없다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-08, REV-01, REV-02, PRV-02 | `POST /api/v1/visits/{visitId}/reviews` | `visit`, `review`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. `visitId`는 양의 `Long`이며 응답 시각은 ISO 8601 `+09:00` 오프셋 문자열이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 회원의 Access Token과 `visit.user_id` 소유권 검증이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태는 `201 Created`이며, 도메인 상태 충돌은 `409 REVIEW_CREATE_CONFLICT`로 응답한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 인증 후기 작성

서버는 인증 주체와 연결된 체크인 완료 방문에만 `PUBLISHED` 후기를 생성한다. 생성한 후기의 콘텐츠와
지역 연결은 요청 본문이 아닌 방문 기록에서 결정한다.

### Request

```http
POST /api/v1/visits/{visitId}/reviews
```

#### Request Example

```http
POST /api/v1/visits/321/reviews HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "rating": 5,
  "reviewText": "아이와 함께 즐겁게 체험했습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이며 대상 방문의 작성자 연결과 같아야 한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `visitId` | Long | Y | 후기 작성 자격을 검증할 방문 식별자. 양의 정수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "rating": 5,
  "reviewText": "아이와 함께 즐겁게 체험했습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rating` | Integer | Y | `1` 이상 `5` 이하의 정수. `null`은 허용하지 않는다. |
| `reviewText` | String | Y | 공백만으로 구성할 수 없고 `1`자 이상 `1,000`자 이하여야 한다. `null`은 허용하지 않는다. |

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
  "message": "후기 작성에 성공했습니다.",
  "data": {
    "reviewId": 901,
    "rating": 5,
    "reviewText": "아이와 함께 즐겁게 체험했습니다.",
    "status": "PUBLISHED",
    "createdAt": "2026-07-30T14:20:00+09:00",
    "updatedAt": "2026-07-30T14:20:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지. 항상 `후기 작성에 성공했습니다.` |
| `data.reviewId` | Long | 새로 생성한 후기 식별자 |
| `data.rating` | Integer | 등록한 평점. `1` 이상 `5` 이하 |
| `data.reviewText` | String | 등록한 후기 텍스트 |
| `data.status` | String | 후기 상태. 항상 `PUBLISHED` |
| `data.createdAt` | String | MySQL 기준 후기 등록 시각의 ISO 8601 `+09:00` 오프셋 문자열 |
| `data.updatedAt` | String | 최초 등록 시각과 같은 후기 수정 시각의 ISO 8601 `+09:00` 오프셋 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `visitId`가 양수가 아니거나 `rating`, `reviewText`가 필수 여부·형식·범위 규칙을 위반했다. 후기와 감사 성공 기록을 생성하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 JSON으로 역직렬화할 수 없다. 후기와 감사 성공 기록을 생성하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `visitId`를 `Long`으로 변환할 수 없다. 후기와 감사 성공 기록을 생성하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 후기와 감사 성공 기록을 생성하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 방문의 활성 작성자 연결과 일치하지 않는다. 후기와 감사 성공 기록을 생성하지 않으며 동일 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 방문을 찾을 수 없다. 후기와 감사 성공 기록을 생성하지 않으며 방문 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `REVIEW_CREATE_CONFLICT` | 대상 방문에 이미 후기 행이 연결돼 있거나 회원 탈퇴와 작성 요청의 경합에서 탈퇴가 먼저 성공했다. 삭제된 후기의 방문 연결도 유지되므로 새 후기를 만들지 않으며, 같은 방문으로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 방문·콘텐츠·지역 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 후기와 감사 성공 기록을 생성하지 않으며 일시적 장애라면 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REVIEW_CREATE_CONFLICT",
  "message": "후기를 작성할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. `visitId`는 체크인 성공으로 생성된 방문을 식별해야 하며, 방문의 활성 `user_id`가 인증 주체와 같아야 한다.
3. 서버는 `rating`과 `reviewText`를 검증한 뒤 후기의 `content_id`, `region_id`, `user_id`를 대상 방문에서 설정한다.
4. 생성된 후기의 상태는 `PUBLISHED`이며 `createdAt`, `updatedAt`은 같은 MySQL 기준 현재 시각으로 기록한다.
5. `review.visit_id` 유일 제약으로 방문당 후기 한 건만 허용한다. `DELETED` 후기의 행과 방문 연결도 유지하므로 삭제 뒤 같은 방문으로 다시 작성할 수 없다.
6. 회원 탈퇴와 후기 작성이 경합하면 회원 행과 방문·후기 조건을 먼저 성공적으로 변경한 처리만 적용한다. 탈퇴가 먼저 시작되면 작성 요청은 상태를 바꾸지 않는다.
7. 이 API는 멱등 키를 받지 않는다. 네트워크 재전송으로 중복 요청이 발생해도 방문당 후기 유일 제약과 조건부 생성으로 한 건만 생성한다.

### 감사 및 정합성

- 후기 생성과 `PUBLISHED` 상태 전이의 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 함께 커밋한다.
- 후기 생성 상태 전이의 실패·거부는 원 트랜잭션을 롤백한 뒤, 별도 MySQL 트랜잭션으로 실패 감사 이벤트를 기록한다. 이 이벤트에는 서버가 검증한 `requestId`, 대상 유형(`VISIT`), 안전하게 확인된 대상 ID·지역 ID, 결과(`FAILURE`), 실패 코드와 서버가 확인한 상태만 기록한다. 대상 또는 지역을 안전하게 확인할 수 없으면 해당 값은 `null`로 기록한다.
- 별도 실패 감사 이벤트 기록이 실패하면 구조화 로그로만 관찰하며, 감사 기록 실패가 후기 생성의 성공·실패 결과를 바꾸지 않는다.
- 실패 감사 이벤트와 구조화 로그에는 후기 원문, 평점, 사용자 식별자 또는 원시 요청 값을 포함하지 않는다.
- 성공 응답과 감사 기록에는 `visit.user_id`, `review.user_id` 또는 그 밖의 개인 연결을 포함하지 않는다.
