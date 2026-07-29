# 인증 후기 삭제 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08 인증 후기](../../../p0/review.md#fr-08-인증-후기), [REV-02](../../../p0/review.md#rev-02), [REV-04](../../../p0/review.md#rev-04), [PRV-02](../../../p0/auth-profile.md#prv-02) |
| 소유 도메인 | 후기 |
| 기준 문서 | [인증 후기](../../../p0/review.md), [제품 PRD](../../../local-stamp-platform-prd.md#85-후기-정책), [ERD](../../../erd.md#5-홀드예약체크인후기-erd), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 작성자 연결을 검증할 수 있는 활성 회원이 자신의 `PUBLISHED` 후기를 삭제하는 HTTP API 계약을
정의한다. 삭제는 `PUBLISHED → DELETED` 단방향 전이이며, 삭제 즉시 공개 목록에서 제외하고 사용자와
관리자 모두 복구할 수 없다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-08, REV-02, REV-04, PRV-02 | `DELETE /api/v1/reviews/{reviewId}` | `review`, `visit`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. `reviewId`는 양의 `Long`이며 삭제 시각은 MySQL 기준으로 기록한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 회원의 Access Token과 `review.user_id` 소유권 검증이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태는 `200 OK`, `data`는 `null`이다. 이미 삭제된 후기와 상태 경합은 `409 REVIEW_DELETE_CONFLICT`로 응답한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 인증 후기 삭제

서버는 인증 주체가 작성한 `PUBLISHED` 후기만 삭제한다. 삭제 행과 `visit_id` 유일 연결은 보존하므로 같은
방문으로 후기 재작성이나 삭제 후기 복구를 허용하지 않는다.

### Request

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
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이며 대상 후기의 활성 작성자 연결과 같아야 한다. |
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
  "data": null
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지. 항상 `후기 삭제에 성공했습니다.` |
| `data` | null | 추가 응답 데이터는 없다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reviewId`가 양수가 아니다. 후기와 감사 성공 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `reviewId`를 `Long`으로 변환할 수 없다. 후기와 감사 성공 기록을 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 후기와 감사 성공 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 후기의 활성 작성자 연결과 일치하지 않는다. 회원 탈퇴로 작성자 연결이 제거된 후기도 포함한다. 후기와 감사 성공 기록을 변경하지 않으며 동일 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 후기를 찾을 수 없다. 후기와 감사 성공 기록을 변경하지 않으며 후기 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `REVIEW_DELETE_CONFLICT` | 후기가 이미 `DELETED`이거나 회원 탈퇴와의 경합에서 탈퇴가 먼저 성공했다. 후기와 감사 성공 기록을 변경하지 않으며 같은 상태에서 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 후기·방문·콘텐츠·지역 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 후기와 감사 성공 기록을 변경하지 않으며 일시적 장애라면 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REVIEW_DELETE_CONFLICT",
  "message": "후기를 삭제할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 하며 `review.user_id`와 같아야 한다.
2. 회원 탈퇴로 `review.user_id`가 제거된 후기에는 기존 계정과 재가입 계정 모두 삭제 권한을 얻지 못한다.
3. 대상 후기가 `PUBLISHED`일 때만 `PUBLISHED → DELETED` 전이를 적용한다. 삭제 가능 기간에는 제한이 없다.
4. 삭제 성공 시 `deleted_at`을 MySQL 기준 현재 시각으로 기록하고, 즉시 공개 후기 목록의 조회 대상에서 제외한다.
5. 삭제 행과 `visit_id` 유일 연결은 유지한다. 이 전이를 되돌리거나 같은 방문에 새 후기를 만들 수 없다.
6. 삭제 시점부터 30일이 지나면 별점과 후기 텍스트 원문만 영구 파기한다. `deleted_at`과 삭제 상태는 유지하며 파기 시계를 다시 시작하지 않는다.
7. 회원 탈퇴와 삭제가 경합하면 활성 회원·작성자 연결·후기 상태 조건을 먼저 성공적으로 변경한 처리만 적용한다. 삭제가 먼저 성공하면 기존 30일 파기 수명주기를 유지한다.

### 감사 및 정합성

- `PUBLISHED → DELETED` 상태 전이와 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 함께 커밋한다.
- 후기 삭제 상태 변경의 실패·거부는 원 트랜잭션을 롤백한 뒤, 별도 MySQL 트랜잭션으로 실패 감사 이벤트를 기록한다. 이 이벤트에는 서버가 검증한 `requestId`, 대상 유형(`REVIEW`), 안전하게 확인된 대상 ID·지역 ID, 결과(`FAILURE`), 실패 코드와 서버가 확인한 상태만 기록한다. 대상 또는 지역을 안전하게 확인할 수 없으면 해당 값은 `null`로 기록한다.
- 별도 실패 감사 이벤트 기록이 실패하면 구조화 로그로만 관찰하며, 감사 기록 실패가 후기 삭제의 성공·실패 결과를 바꾸지 않는다.
- 삭제 후에는 성공 응답, 감사 기록, 구조화 로그에 후기 원문·평점·개인 연결을 포함하지 않는다.
- 실패 감사 이벤트와 구조화 로그에는 사용자 식별자 또는 원시 요청 값을 포함하지 않는다.
- 원문 파기 작업은 `status = DELETED`와 `deleted_at + 30일`을 기준으로 수행하며, 파기 작업은 후기를 공개 상태로 바꾸지 않는다.
