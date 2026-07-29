## 5. 후기 수정

작성자 연결이 유지되는 활성 회원이 후기 등록 후 30일 안에 자신의 `PUBLISHED` 후기를 수정한다.
이 API는 별점과 후기 원문 전체를 새 요청 값으로 교체한다.

### Request

```http
PUT /reviews/{reviewId}
```

실제 요청 경로는 다음과 같다.

```http
PUT /api/v1/reviews/{reviewId}
```

#### Request Example

```http
PUT /api/v1/reviews/901 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "rating": 4,
  "reviewText": "안내가 친절했고 다음에도 참여하고 싶습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | Y | `application/json` |
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
  "reviewText": "안내가 친절했고 다음에도 참여하고 싶습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rating` | Integer | Y | 교체할 별점. `1` 이상 `5` 이하의 정수여야 한다. |
| `reviewText` | String | Y | 교체할 후기 원문. `null` 또는 공백만으로 구성할 수 없고 `1`자 이상 `1000`자 이하여야 한다. |

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
    "contentId": 123,
    "rating": 4,
    "reviewText": "안내가 친절했고 다음에도 참여하고 싶습니다.",
    "status": "PUBLISHED",
    "createdAt": "2026-07-01T12:00:00+09:00",
    "updatedAt": "2026-07-29T12:00:00+09:00",
    "editableUntil": "2026-07-31T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reviewId` | Long | 수정된 후기 식별자 |
| `data.contentId` | Long | 후기의 콘텐츠 식별자 |
| `data.rating` | Integer | 수정된 별점 |
| `data.reviewText` | String | 수정된 후기 원문 |
| `data.status` | String | 후기 상태. 항상 `PUBLISHED` |
| `data.createdAt` | String | 후기 생성 시각 |
| `data.updatedAt` | String | 이번 수정이 반영된 시각 |
| `data.editableUntil` | String | 후기 수정 마감 시각. `createdAt + 30일` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `reviewId`를 `Long`으로 변환할 수 없다. 후기는 변경되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | 경로 변수나 요청 필드가 없거나 형식·확정된 범위가 올바르지 않다. 후기는 변경되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 후기는 변경되지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 후기는 변경되지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 후기 작성자가 아니거나 작성자 연결이 제거됐다. 후기는 변경되지 않으며 같은 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 후기를 찾을 수 없다. 후기는 변경되지 않으며 후기 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `REVIEW_UPDATE_CONFLICT` | 후기가 `PUBLISHED`가 아니거나 수정 가능 기간이 지났거나 삭제와의 경합에서 삭제가 먼저 성공했다. 후기는 변경되지 않으며 상태나 수정 마감이 달라지지 않으면 같은 요청으로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 후기 수정 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 후기는 변경되지 않으며 서버가 롤백한 것을 확인한 경우에만 재시도할 수 있다. |

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

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 대상 후기의 `user_id`가 인증 주체와 일치하고 작성자 연결이 유지돼야 한다.
3. 후기 상태가 `PUBLISHED`이고 `MySQL 현재 시각 < created_at + 30일`인 경우에만 수정한다.
4. 수정 가능 기간의 기준은 서버 시계가 아니라 MySQL 현재 시각이다.
5. `rating`과 `review_text`를 함께 교체하고 `updated_at`을 같은 MySQL 현재 시각으로 갱신한다.
6. `visit_id`, `content_id`, `region_id`, `user_id`, `status`, `created_at`은 변경하지 않는다.
7. 수정과 성공 감사 이벤트는 하나의 트랜잭션에서 함께 커밋한다.
8. 삭제와 경합해 삭제가 먼저 성공하면 `REVIEW_UPDATE_CONFLICT`를 반환하고, 수정이 먼저 성공하면 삭제가 수정 결과를 포함한 후기를 `DELETED`로 전환한다.
9. 회원 탈퇴가 먼저 `WITHDRAWING`으로 전환되거나 작성자 연결을 제거하면 `FORBIDDEN`을 반환한다. 수정이 먼저 성공하면 탈퇴 흐름이 수정 결과를 보존한 채 작성자 연결을 제거한다.
10. 조건부 갱신이 실패하면 대상 부재는 `NOT_FOUND`, 활성 회원·작성자 연결·소유권 실패는 `FORBIDDEN`, 후기 상태·수정 기간 실패는 `REVIEW_UPDATE_CONFLICT` 순서로 판정한다.
11. 오류가 발생하면 후기 원문, 시각과 감사 이벤트를 변경하지 않는다.

### 감사 및 정합성

- 성공 감사 이벤트는 대상 유형 `REVIEW`, `review_id`, 처리자, 처리 시각과 수정 성공을 재현할 수 있어야 한다.
- 감사 이벤트에 수정 전후 후기 원문을 복사하지 않는다.
- 수정 실패 사유는 원문이나 개인정보 없이 공개 오류 코드와 상태·기간·권한 범주로 추적한다.
