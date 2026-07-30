# 작성자 후기 삭제 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08](../../../p0/review.md#fr-08-인증-후기), `REV-04` |
| 소유 도메인 | 인증 후기 |
| 기준 문서 | [인증 후기 API](review.md), [인증 후기](../../../p0/review.md), [ERD](../../../erd.md#체크인후기-정규화-규칙), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 작성자는 자신의 `PUBLISHED` 후기를 기간 제한 없이 삭제할 수 있다. 삭제는 행·방문 연결을 보존한
`PUBLISHED → DELETED` 상태 전이이며, 성공 즉시 공개 목록에서 제외한다. `deletedAt + 30일` 후 별점과
본문 원문만 영구 파기하고 삭제·복구·같은 방문의 재작성을 허용하지 않는다.

## 2. 공통 계약 참조

삭제·인증·응답·오류의 공통 규칙은 [인증 후기 API](review.md#2-공통-계약-참조)를 따른다.

## 3. 작성자 후기 삭제

### Request

```http
DELETE /api/v1/reviews/{reviewId}
```

#### Request Example

```http
DELETE /api/v1/reviews/501 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | 후기 작성자를 확인하는 `Bearer <accessToken>`이다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reviewId` | String | Y | 양의 10진 정수 문자열인 후기 식별자다. signed 64비트 `Long` 범위를 함께 만족해야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
204 No Content
```

성공 응답은 본문과 JSON 응답 헤더를 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_TYPE` | `reviewId`를 양의 10진 정수 문자열로 처리할 수 없다. 상태를 변경하지 않는다. |
| 400 | `INVALID_INPUT` | `reviewId`가 양의 정수 형식을 만족하지 않는다. 상태를 변경하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| 403 | `FORBIDDEN` | 인증 주체가 작성자가 아니거나 활성 작성자 연결이 없다. 상태를 변경하지 않는다. |
| 404 | `NOT_FOUND` | 후기가 없거나 이미 `DELETED` 상태다. 삭제 시계와 원문을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

삭제 요청과 회원 탈퇴가 경합하면 활성 회원·작성자 연결·후기 상태 조건을 먼저 만족해 커밋한 처리만 적용한다.
삭제가 먼저 성공하면 기존 삭제 수명주기를 유지하고, 탈퇴가 먼저 시작되면 이 API는 `FORBIDDEN`으로 거부한다.

후기 삭제와 대상 유형 `REVIEW`의 `PUBLISHED → DELETED` 성공 감사 이벤트는 하나의 트랜잭션으로 커밋한다. 성공 감사 이벤트 저장에 실패하면 트랜잭션을 롤백해 후기 삭제도 성공시키지 않는다.
감사 이벤트에는 후기 원문·별점·개인정보를 복사하지 않고, 활성 작성자에 대해서만 행위자 연결을 만든다. 이미 `DELETED`인 후기는 현 API 계약대로 `NOT_FOUND`를 반환하고 새 감사 이벤트를 만들지 않는다.

거부·실패는 후기 및 성공 감사 이벤트와 별도 트랜잭션에서 서버가 확인한 `requestId`, 행위자 역할·식별값, 대상 지역, 이전·이후 상태와 결과 코드를 남긴다. 탈퇴하거나 연결이 제거된 작성자에는 행위자 연결을 만들지 않는다.
거부·실패 감사 기록 저장 자체에 실패하면 후기 삭제의 거부·실패 결과를 바꾸지 않고 `requestId`와 결과 코드만 구조화 로그로 남긴다. 구조화 로그와 감사 기록에 후기 원문·별점·개인정보·원본 요청 본문을 남기지 않는다.
