## 4. 인증 후기 목록 조회

공개 조회가 허용된 콘텐츠의 `PUBLISHED` 인증 후기를 조회한다.
삭제된 후기는 즉시 제외하며 P0에서는 페이지네이션, 필터와 사용자 지정 정렬을 제공하지 않는다.

### Request

```http
GET /contents/{contentId}/reviews
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/contents/{contentId}/reviews
```

#### Request Example

```http
GET /api/v1/contents/123/reviews HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 공개 API이므로 인증 정보를 요구하지 않는다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 후기를 조회할 콘텐츠 식별자. 양의 정수여야 한다. |

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
  "message": "인증 후기 목록 조회에 성공했습니다.",
  "data": {
    "contentId": 123,
    "reviews": [
      {
        "reviewId": 901,
        "rating": 5,
        "reviewText": "현장에서 안내를 잘 받아 즐겁게 참여했습니다.",
        "authorDisplayName": "인증 방문자",
        "createdAt": "2026-07-29T12:00:00+09:00",
        "updatedAt": "2026-07-29T12:00:00+09:00"
      },
      {
        "reviewId": 850,
        "rating": 4,
        "reviewText": "다시 참여하고 싶은 행사였습니다.",
        "authorDisplayName": "탈퇴한 사용자",
        "createdAt": "2026-07-20T10:00:00+09:00",
        "updatedAt": "2026-07-20T10:00:00+09:00"
      }
    ]
  }
}
```

후기가 없으면 `200 OK`와 빈 컬렉션을 반환한다.

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "인증 후기 목록 조회에 성공했습니다.",
  "data": {
    "contentId": 123,
    "reviews": []
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
| `data.reviews` | Array | 공개 후기 목록. 결과가 없으면 빈 컬렉션 |
| `data.reviews[].reviewId` | Long | 후기 식별자 |
| `data.reviews[].rating` | Integer | 별점 |
| `data.reviews[].reviewText` | String | 후기 원문 |
| `data.reviews[].authorDisplayName` | String | 작성자 연결이 유지되면 `인증 방문자`, 탈퇴로 연결이 제거되면 `탈퇴한 사용자` |
| `data.reviews[].createdAt` | String | 후기 생성 시각 |
| `data.reviews[].updatedAt` | String | 후기 최종 수정 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `contentId`를 `Long`으로 변환할 수 없다. 조회 상태는 변경되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `contentId`가 양의 정수가 아니다. 조회 상태는 변경되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `404` | `NOT_FOUND` | 콘텐츠를 찾을 수 없거나 공개 조회가 허용되지 않는다. 상태는 변경되지 않으며 공개 상태가 달라지지 않으면 같은 요청으로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 목록 조회 중 예상하지 못한 서버 오류가 발생했다. 상태는 변경되지 않으며 일시적 장애가 해소된 뒤 재시도할 수 있다. |

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

1. 이 API는 인증을 요구하지 않는 공개 조회 API다.
2. 콘텐츠가 존재하고 콘텐츠 카탈로그 정책상 공개 조회가 허용되는지 검증한다.
3. `content_id`가 대상 콘텐츠와 일치하고 `status = PUBLISHED`인 후기만 반환한다.
4. `DELETED` 후기는 원문 파기 여부와 관계없이 조회 결과에서 제외한다.
5. 목록은 `created_at` 내림차순, 같은 시각이면 `review_id` 내림차순으로 정렬한다.
6. P0에서는 페이지네이션, 필터와 사용자 지정 정렬을 제공하지 않는다.
7. 공개 응답에는 `user_id`, `visit_id`, `region_id`, 회원 이름과 연락처를 포함하지 않는다.
8. 작성자 연결이 유지되는 후기는 `인증 방문자`, 탈퇴로 연결이 제거된 후기는 공통 `탈퇴한 사용자`로 표시한다.
9. 조회는 후기, 방문, 콘텐츠와 감사 데이터를 변경하지 않는다.

### 감사 및 정합성

- 공개 목록 조회 성공은 상태 전이가 아니므로 성공 감사 이벤트를 생성하지 않는다.
- 회원 탈퇴 처리와 조회가 경합하면 커밋된 작성자 연결 상태를 기준으로 표시 문자열을 파생한다.
- `DELETED` 전이가 커밋된 후에는 캐시를 포함한 모든 공개 조회에서 해당 후기를 제외해야 한다.
