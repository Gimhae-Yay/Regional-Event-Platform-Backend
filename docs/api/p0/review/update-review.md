# 후기 수정 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08](../../../p0/review.md#fr-08-인증-후기), `REV-03` |
| 소유 도메인 | 인증 후기 |
| 기준 문서 | [인증 후기 API](review.md), [인증 후기](../../../p0/review.md), [ERD](../../../erd.md#체크인후기-정규화-규칙), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 작성자는 자신의 `PUBLISHED` 후기를 생성 시각부터 30일 안에 수정할 수 있다. 서버는 DB 현재 시각이
`createdAt + 30일`보다 엄격히 이른 경우에만 수정하며, 작성자 연결이 제거된 후기와 삭제 후기는 수정할 수 없다.

## 2. 공통 계약 참조

수정·인증·응답·오류의 공통 규칙은 [인증 후기 API](review.md#2-공통-계약-참조)를 따른다.

## 3. 후기 수정

### Request

```http
PATCH /api/v1/reviews/{reviewId}
```

#### Request Example

```http
PATCH /api/v1/reviews/501 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "rating": 4,
  "reviewText": "해설이 친절했고 다음에도 참여하고 싶습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | 후기 작성자를 확인하는 `Bearer <accessToken>`이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reviewId` | String | Y | 양의 10진 정수 문자열인 후기 식별자다. signed 64비트 `Long` 범위를 함께 만족해야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "rating": 4,
  "reviewText": "해설이 친절했고 다음에도 참여하고 싶습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rating` | Integer | N | 포함한 경우에만 검증하며 `1~5` 정수만 허용한다. `null`은 허용하지 않는다. |
| `reviewText` | String | N | 포함한 경우에만 검증하며, 앞뒤 공백을 제거한 값이 `1~2,000`자여야 한다. `null`, 빈 문자열, 공백만으로 된 값은 허용하지 않는다. |

`rating`, `reviewText` 중 하나 이상을 포함해야 한다. 둘 다 포함하면 각 필드에 위 검증을 각각 적용하며, 포함하지 않은 필드는 변경하지 않는다.

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
    "reviewId": "501",
    "rating": 4,
    "reviewText": "해설이 친절했고 다음에도 참여하고 싶습니다.",
    "createdAt": "2026-07-29T08:20:00Z",
    "updatedAt": "2026-07-30T08:20:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.reviewId` | String | 양의 10진 정수 문자열인 수정한 후기 식별자다. |
| `data.rating` | Integer | 수정 뒤 별점이다. |
| `data.reviewText` | String | 수정 뒤 후기 본문이다. |
| `data.createdAt` | String | UTC ISO 8601 형식의 최초 생성 시각이다. |
| `data.updatedAt` | String | UTC ISO 8601 형식의 수정 시각이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_TYPE` | `reviewId`를 양의 10진 정수 문자열로 처리할 수 없다. 상태를 변경하지 않는다. |
| 400 | `INVALID_INPUT` | `rating`, `reviewText`를 모두 생략했거나, 경로 식별자 또는 포함한 별점·본문이 형식·범위를 만족하지 않는다. 상태를 변경하지 않는다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니다. 상태를 변경하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| 403 | `FORBIDDEN` | 인증 주체가 작성자가 아니거나 활성 작성자 연결이 없거나, 수정 기한이 지났다. 상태를 변경하지 않는다. |
| 404 | `NOT_FOUND` | 후기가 없거나 이미 `DELETED` 상태다. 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리·감사 규칙

- 수정 가능 여부는 애플리케이션 서버가 아닌 DB 현재 시각으로 판정한다. 활성 회원·작성자 연결·소유권·`PUBLISHED` 상태와 `created_at + 30일` 미만 조건을 갱신 조건에 함께 포함한다.
- 삭제 또는 회원 탈퇴와 경합하면 먼저 조건을 만족해 커밋한 처리만 적용한다. 조건부 갱신이 0건이면 후기·원문·시각·감사 이벤트를 변경하지 않고 기존 오류 계약에 따라 응답한다.
- 성공한 수정은 대상 유형 `REVIEW`의 감사 이벤트와 같은 트랜잭션으로 커밋한다. 성공 감사 이벤트 저장에 실패하면 트랜잭션을 롤백해 후기 수정도 성공시키지 않는다. 감사 이벤트에는 수정 전후 후기 원문·별점·개인정보를 복사하지 않고, 활성 작성자에 대해서만 행위자 연결을 만든다.
- 거부·실패는 후기 및 성공 감사 이벤트와 별도 트랜잭션에서 서버가 확인한 `requestId`, 행위자 역할·식별값, 대상 지역, 이전·이후 상태와 결과 코드를 남긴다. 탈퇴하거나 연결이 제거된 작성자에는 행위자 연결을 만들지 않는다.
- 거부·실패 감사 기록 저장 자체에 실패하면 후기 수정의 거부·실패 결과를 바꾸지 않고 `requestId`와 결과 코드만 구조화 로그로 남긴다. 구조화 로그와 감사 기록에 후기 원문·별점·개인정보·원본 요청 본문을 남기지 않는다.
