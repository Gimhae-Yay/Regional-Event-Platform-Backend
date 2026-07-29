# 콘텐츠 인증 후기 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08 인증 후기](../../../p0/review.md#fr-08-인증-후기), [REV-02](../../../p0/review.md#rev-02), [REV-04](../../../p0/review.md#rev-04), [PRV-02](../../../p0/auth-profile.md#prv-02) |
| 소유 도메인 | 후기 |
| 기준 문서 | [인증 후기](../../../p0/review.md), [제품 PRD](../../../local-stamp-platform-prd.md#85-후기-정책), [ERD](../../../erd.md#5-홀드예약체크인후기-erd), [ADR-0012](../../../adr/0012-retain-author-unlinked-reviews-and-visits-after-withdrawal.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 콘텐츠 상세에 공개할 인증 후기 목록을 조회하는 HTTP API 계약을 정의한다. `PUBLISHED` 후기만
반환하며, 삭제된 후기는 즉시 제외한다. 탈퇴로 작성자 연결이 제거된 공개 후기는 원문과 공개 상태를 유지하되
작성자 표시를 공통 `탈퇴한 사용자`로 반환하고 개인 연결은 노출하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-08, REV-02, REV-04, PRV-02 | `GET /api/v1/contents/{contentId}/reviews` | `content`, `review`, `app_user` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. `contentId`는 양의 `Long`이며 `createdAt`은 ISO 8601 `+09:00` 오프셋 문자열이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 인증이 필요하지 않은 공개 API다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태는 `200 OK`이며, 빈 결과도 빈 배열을 포함한 `200 OK`로 응답한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 페이지네이션·필터·사용자 지정 정렬을 제공하지 않는 단순 목록이다. |

## 3. 콘텐츠 인증 후기 목록 조회

서버는 대상 콘텐츠에 연결된 `PUBLISHED` 후기만 `createdAt` 내림차순, 같은 시각이면 `reviewId` 내림차순으로
반환한다. 인증 정보, 방문 식별자와 작성자의 사용자 식별자는 응답에 포함하지 않는다.

### Request

```http
GET /api/v1/contents/{contentId}/reviews
```

#### Request Example

```http
GET /api/v1/contents/101/reviews HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 인증이 필요하지 않은 공개 API |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 공개 후기를 조회할 콘텐츠 식별자. 양의 정수여야 한다. |

#### Query Parameter

없음. P0에서는 페이지네이션, 필터와 사용자 지정 정렬을 제공하지 않는다.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

후기가 없으면 `404`가 아닌 `200 OK`와 `data.reviews = []`를 반환한다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "인증 후기 목록 조회에 성공했습니다.",
  "data": {
    "reviews": [
      {
        "reviewId": 901,
        "rating": 5,
        "reviewText": "아이와 함께 즐겁게 체험했습니다.",
        "authorName": "홍길동",
        "createdAt": "2026-07-30T14:20:00+09:00"
      },
      {
        "reviewId": 899,
        "rating": 4,
        "reviewText": "추천합니다.",
        "authorName": "탈퇴한 사용자",
        "createdAt": "2026-07-29T11:00:00+09:00"
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
| `message` | String | 공개 성공 메시지. 항상 `인증 후기 목록 조회에 성공했습니다.` |
| `data.reviews` | Array | 대상 콘텐츠의 `PUBLISHED` 후기 목록. 결과가 없으면 빈 배열 `[]` |
| `data.reviews[].reviewId` | Long | 후기 식별자 |
| `data.reviews[].rating` | Integer | 후기 평점. `1` 이상 `5` 이하 |
| `data.reviews[].reviewText` | String | 공개 후기 텍스트 |
| `data.reviews[].authorName` | String | 활성 작성자의 이름. `author_unlinked_at`이 존재해 작성자 연결이 제거된 후기는 항상 `탈퇴한 사용자` |
| `data.reviews[].createdAt` | String | 후기 등록 시각의 ISO 8601 `+09:00` 오프셋 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양수가 아니다. 조회 대상과 상태를 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `contentId`를 `Long`으로 변환할 수 없다. 조회 대상과 상태를 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없거나 공개 조회가 허용되지 않는 콘텐츠다. 조회 대상과 상태를 변경하지 않으며 콘텐츠 식별자와 공개 상태를 확인한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠·후기·작성자 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

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

1. 대상 콘텐츠는 공개 조회가 허용되는 콘텐츠여야 한다.
2. `review.content_id`가 대상 콘텐츠와 같고 `review.status = PUBLISHED`인 후기만 반환한다.
3. `DELETED` 후기와 삭제 후 30일이 지나 원문이 파기된 후기는 반환하지 않는다.
4. 목록은 `created_at` 내림차순, 같은 시각이면 `review_id` 내림차순으로 정렬한다.
5. P0에서는 페이지네이션, 필터, 검색과 사용자 지정 정렬을 제공하지 않는다.
6. 활성 작성자가 연결된 후기는 작성자의 이름을 반환한다. `review.user_id` 또는 `visit.user_id`를 반환하지 않는다.
7. `review.author_unlinked_at`이 존재하는 후기는 작성자 표시를 공통 `탈퇴한 사용자`로 파생한다. 탈퇴 전 사용자 식별자, 재식별 매핑과 사용자별 안정 가명은 반환하지 않는다.
8. 빈 목록은 `200 OK`와 빈 배열로 반환하며, 조회는 콘텐츠·후기·방문·감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 결과 건수와 결과 코드만 구조화 로그로 남긴다. 후기 원문, 평점, 작성자 이름과 사용자 식별자는 로그에 남기지 않는다.
- 공개 응답의 작성자 표시는 관계를 저장한 값이 아니라 활성 작성자 이름 또는 `author_unlinked_at`에 따른 공통 표시로 조립한다.
