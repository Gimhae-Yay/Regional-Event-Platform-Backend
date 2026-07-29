# 콘텐츠 승인 재요청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `AUTH-01`, `CON-01`, `CON-02`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 반려된 콘텐츠를 지역 관리자 심사에 재요청한다. 서버는 필수 콘텐츠 필드, 현재 대표 이미지와
유효 회차를 다시 검증한 뒤 `REJECTED → PENDING`으로 전이한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03` | `POST /operator/contents/{contentId}/submit` | `content`, `content_session`, `image_object`, `content_log` |
| `CON-01` | `POST /operator/contents/{contentId}/submit` | `content.status`, `content_log.status` |
| `CON-02` | `POST /operator/contents/{contentId}/submit` | 콘텐츠 필수 필드, 대표 이미지, 회차 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}/submit`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 제출 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 상태 전이 명령이므로 적용하지 않는다. |

## 3. 콘텐츠 승인 재요청

### Request

```http
POST /api/v1/operator/contents/{contentId}/submit
```

#### Request Example

```http
POST /api/v1/operator/contents/101/submit HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 양의 10진 문자열인 제출할 콘텐츠 식별자다. |

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
  "message": "콘텐츠 승인 재요청에 성공했습니다.",
  "data": {
    "contentId": "101",
    "status": "PENDING",
    "submittedAt": "2026-07-30T05:10:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.contentId` | String | 양의 10진 문자열인 제출한 콘텐츠 식별자 |
| `data.status` | String | 제출 후 상태 `PENDING` |
| `data.submittedAt` | String | 심사 요청 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양수가 아니거나 필수 콘텐츠 필드·현재 대표 이미지·유효 회차가 제출 조건을 만족하지 않는다. 콘텐츠를 전이하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 콘텐츠를 전이하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 콘텐츠를 전이하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐다. 콘텐츠를 전이하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 콘텐츠가 `REJECTED`가 아니다. 콘텐츠를 전이하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. `REJECTED` 상태에서만 `PENDING`으로 전이한다. 그 외 상태의 반복 제출은 기존 결과를 반환하지 않고 충돌로 거부한다.
2. 서버는 제목·소개·위치·운영 시간·연락처·유의사항·연령 조건·준비물·취소 정책 안내 문구·공개 예정 시각, 현재 대표 이미지 한 개, 유효 회차 한 개 이상을 모두 확인한다.
3. 성공 시 콘텐츠 상태 전이와 `PENDING` 상태 로그을 같은 트랜잭션에서 기록한다.
4. `PENDING` 콘텐츠는 심사 결과가 나올 때까지 이 API로 다시 제출하거나 직접 편집할 수 없다.
