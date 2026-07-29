## 3. 체크인 방문당 후기 1건 작성

활성 회원이 자신과 연결된 체크인 완료 방문에 별점과 텍스트 후기를 한 건 작성한다.
성공하면 방문, 콘텐츠, 지역과 작성자 연결을 서버가 방문 기록에서 파생해 `PUBLISHED` 후기를 생성한다.

### Request

```http
POST /visits/{visitId}/reviews
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/visits/{visitId}/reviews
```

#### Request Example

```http
POST /api/v1/visits/321/reviews HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "rating": 5,
  "reviewText": "현장에서 안내를 잘 받아 즐겁게 참여했습니다."
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
| `visitId` | Long | Y | 후기 작성 자격의 기준이 되는 방문 식별자. 양의 정수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "rating": 5,
  "reviewText": "현장에서 안내를 잘 받아 즐겁게 참여했습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rating` | Integer | Y | 별점. `1` 이상 `5` 이하의 정수여야 한다. |
| `reviewText` | String | Y | 후기 원문. `null` 또는 공백만으로 구성할 수 없고 `1`자 이상 `1000`자 이하여야 한다. |

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
    "visitId": 321,
    "contentId": 123,
    "rating": 5,
    "reviewText": "현장에서 안내를 잘 받아 즐겁게 참여했습니다.",
    "status": "PUBLISHED",
    "createdAt": "2026-07-29T12:00:00+09:00",
    "updatedAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reviewId` | Long | 생성된 후기 식별자 |
| `data.visitId` | Long | 후기 작성 자격의 기준이 된 방문 식별자 |
| `data.contentId` | Long | 방문 기록에서 파생한 콘텐츠 식별자 |
| `data.rating` | Integer | 등록한 별점 |
| `data.reviewText` | String | 등록한 후기 원문 |
| `data.status` | String | 후기 상태. 항상 `PUBLISHED` |
| `data.createdAt` | String | 후기 생성 시각 |
| `data.updatedAt` | String | 후기 최종 수정 시각. 생성 직후에는 `createdAt`과 같다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `visitId`를 `Long`으로 변환할 수 없다. 후기는 생성되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | 경로 변수나 요청 필드가 없거나 형식·확정된 범위가 올바르지 않다. 후기는 생성되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 후기는 생성되지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 후기는 생성되지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 방문의 연결 사용자와 일치하지 않는다. 후기는 생성되지 않으며 같은 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 방문을 찾을 수 없다. 후기는 생성되지 않으며 방문 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `REVIEW_CREATE_CONFLICT` | 같은 방문의 후기가 이미 존재하거나 동시 작성이 먼저 성공했다. 새 후기는 생성되지 않으며 같은 방문으로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 후기 작성 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 후기는 생성되지 않으며 서버가 롤백한 것을 확인한 경우에만 재시도할 수 있다. |

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
2. 서버는 `visitId`에 해당하는 방문의 `user_id`가 인증 주체와 일치하고 작성자 연결이 유지되는지 검증한다.
3. `region_id`, `content_id`, `visit_id`, `user_id`는 방문 기록과 인증 주체에서 파생하며 요청 본문으로 받지 않는다.
4. `review.visit_id` 유일 제약과 조건부 생성을 함께 적용해 방문 한 건당 후기를 최대 한 건만 허용한다.
5. 과거 후기가 `DELETED` 상태이거나 원문이 파기됐더라도 후기 행과 방문 연결을 유지하므로 같은 방문으로 다시 작성할 수 없다.
6. 회원 탈퇴로 `visit.user_id`가 제거된 방문은 새 후기 작성 자격으로 사용할 수 없다.
7. 생성된 후기는 `PUBLISHED` 상태이며 `created_at`과 `updated_at`은 동일한 MySQL 현재 시각으로 기록한다.
8. 후기 생성과 성공 감사 이벤트는 하나의 트랜잭션에서 함께 커밋한다.
9. 오류가 발생하면 후기와 감사 이벤트를 생성하지 않는다.

### 감사 및 정합성

- 성공 감사 이벤트는 대상 유형 `REVIEW`, 생성된 `review_id`, 처리자, 처리 시각과 작성 성공을 재현할 수 있어야 한다.
- 감사 이벤트에 후기 원문과 사용자 개인정보를 복사하지 않는다.
- 방문 자격·작성자 연결·중복 검증 실패는 후기 원문 없이 공개 오류 코드와 실패 범주로 추적한다.
- 방문 작성 자격 검증과 후기 생성 사이에 회원 탈퇴 또는 동시 작성이 경합하면 먼저 성공한 조건부 처리만 반영한다.
