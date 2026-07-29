## 6. 작성자 후기 삭제

작성자 연결이 유지되는 활성 회원이 자신의 후기를 기간 제한 없이 삭제한다.
성공하면 후기는 `PUBLISHED`에서 `DELETED`로 전환돼 즉시 공개 조회에서 제외되며 복구할 수 없다.

### Request

```http
DELETE /reviews/{reviewId}
```

실제 요청 경로는 다음과 같다.

```http
DELETE /api/v1/reviews/{reviewId}
```

#### Request Example

```http
DELETE /api/v1/reviews/901 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reviewId` | Long | Y | 삭제할 후기 식별자. 양의 정수여야 한다. |

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
  "message": "후기 삭제에 성공했습니다.",
  "data": {
    "reviewId": 901,
    "status": "DELETED",
    "deletedAt": "2026-07-29T12:00:00+09:00",
    "sourcePurgeAt": "2026-08-28T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reviewId` | Long | 삭제된 후기 식별자 |
| `data.status` | String | 후기 상태. 항상 `DELETED` |
| `data.deletedAt` | String | 최초 삭제 성공 시각 |
| `data.sourcePurgeAt` | String | 별점·후기 원문 파기 예정 시각. `deletedAt + 30일` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `reviewId`를 `Long`으로 변환할 수 없다. 후기는 변경되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `reviewId`가 양의 정수가 아니다. 후기는 변경되지 않으며 올바른 값으로 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 후기는 변경되지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 후기 작성자가 아니거나 작성자 연결이 제거됐다. 후기는 변경되지 않으며 같은 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 후기를 찾을 수 없다. 후기는 변경되지 않으며 후기 식별자를 확인한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 후기 삭제 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 후기는 변경되지 않으며 서버가 롤백한 것을 확인한 경우에만 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 대상 후기의 `user_id`가 인증 주체와 일치하고 작성자 연결이 유지돼야 한다.
3. `PUBLISHED` 후기만 `DELETED`로 조건부 전이하고 `deleted_at`을 MySQL 현재 시각으로 기록한다.
4. 최초 삭제 성공 시 `updated_at`도 `deleted_at`과 같은 시각으로 갱신한다.
5. 후기 생성 후 경과 시간과 관계없이 삭제할 수 있다.
6. 삭제 즉시 공개 목록과 콘텐츠 상세에서 제외하며 사용자와 관리자 모두 복구할 수 없다.
7. 삭제 시점에는 `rating`과 `review_text`를 유지하고 `deleted_at + 30일` 이후 스케줄러가 원문을 파기한다.
8. 원문 파기 작업은 후기 행, `visit_id` 유일 연결, `content_id`, `region_id`와 현재 작성자 연결 상태를 변경하지 않는다.
9. 삭제 전후에 회원 탈퇴가 성공하면 탈퇴 흐름은 `DELETED` 후기의 `user_id`도 제거하고 `author_unlinked_at`을 기록하되, `deleted_at`과 원문 파기 시계는 변경하지 않는다.
10. 작성자 연결이 유지되는 같은 사용자가 이미 삭제된 후기에 다시 요청하면 최초 `deletedAt`과 `sourcePurgeAt`을 반환하며 감사 이벤트와 파기 시계를 중복 생성하거나 변경하지 않는다.
11. 회원 탈퇴와 삭제가 경합하면 활성 회원, 작성자 연결과 `PUBLISHED` 상태를 조건으로 먼저 성공한 처리만 반영한다.
12. 삭제와 성공 감사 이벤트는 하나의 트랜잭션에서 함께 커밋한다.
13. 오류가 발생하면 후기 상태, 삭제 시각, 원문과 감사 이벤트를 변경하지 않는다.

### 감사 및 정합성

- 성공 감사 이벤트는 대상 유형 `REVIEW`, `review_id`, `PUBLISHED → DELETED`, 처리자와 최초 삭제 시각을 재현할 수 있어야 한다.
- 감사 이벤트에 별점, 후기 원문과 사용자 개인정보를 복사하지 않는다.
- 삭제 실패 사유는 후기 원문 없이 공개 오류 코드와 상태·권한 범주로 추적한다.
- 삭제 재요청은 기존 결과만 반환하며 상태 전이와 성공 감사 이벤트를 다시 만들지 않는다.
