# 인증 후기 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08](../../../p0/review.md#fr-08-인증-후기), `REV-02`, `PRV-02` |
| 소유 도메인 | 인증 후기 |
| 기준 문서 | [인증 후기 API](review.md), [인증 후기](../../../p0/review.md), [ADR-0012](../../../adr/0012-retain-author-unlinked-reviews-and-visits-after-withdrawal.md), [ADR-0028](../../../adr/0028-use-page-number-pagination-for-review-list.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 API는 공개 콘텐츠 상세에서 인증 후기 목록을 조회한다. 인증은 필요 없으며 `PUBLISHED` 상태의 후기만 최신 작성순으로
반환한다. 삭제 후기와 작성자·방문 식별자는 반환하지 않는다.

## 2. 공통 계약 참조

조회·응답·오류·페이지 규칙은 [인증 후기 API](review.md#2-공통-계약-참조)를 따른다.

## 3. 인증 후기 목록 조회

### Request

```http
GET /api/v1/contents/{contentId}/reviews
```

#### Request Example

```http
GET /api/v1/contents/41/reviews?page=0&size=20 HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 공개 API이므로 필요하지 않다. 전송해도 목록 범위는 바뀌지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 양의 정수인 공개 콘텐츠 식별자다. |

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `page` | Integer | N | 0부터 시작하는 페이지 번호다. 생략하면 `0`이며 음수는 허용하지 않는다. |
| `size` | Integer | N | 페이지 크기다. 생략하면 `20`이며 `1~100`만 허용한다. |

정렬 파라미터는 받지 않는다. 결과는 `createdAt` 내림차순, 같은 시각이면 `reviewId` 내림차순으로 고정한다.

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
  "message": "인증 후기 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "reviewId": 501,
        "authorDisplayName": "인증 방문자",
        "rating": 5,
        "reviewText": "지역의 이야기를 직접 들을 수 있어 좋았습니다.",
        "createdAt": "공통 시각 형식",
        "updatedAt": "공통 시각 형식"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.content` | Array | 공개 후기 배열이다. 결과가 없으면 빈 배열이다. |
| `data.content[].reviewId` | Long | 공개 후기 식별자다. |
| `data.content[].authorDisplayName` | String | 작성자 연결이 유지되면 공통 `인증 방문자`, 연결이 제거된 후기는 공통 `탈퇴한 사용자`를 반환한다. |
| `data.content[].rating` | Integer | 별점이다. |
| `data.content[].reviewText` | String | 후기 본문이다. |
| `data.content[].createdAt` | String | 생성 시각이며 공통 시각 형식을 따른다. |
| `data.content[].updatedAt` | String | 마지막 수정 시각이며 공통 시각 형식을 따른다. |
| `data.page` | Integer | 현재 페이지 번호다. |
| `data.size` | Integer | 현재 페이지 크기다. |
| `data.totalElements` | Long | 공개 후기 전체 수다. |
| `data.totalPages` | Integer | 전체 페이지 수다. 빈 결과이면 `0`이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `contentId`, `page` 또는 `size`가 허용 형식·범위를 만족하지 않는다. 조회하지 않으며 값을 수정해 재시도할 수 있다. |
| 404 | `NOT_FOUND` | 콘텐츠가 없거나 공개 후기 조회를 허용하는 공개 상태가 아니다. 존재 여부나 비공개 상태를 추가로 공개하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
