# 체크인 방문당 후기 작성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08](../../../p0/review.md#fr-08-인증-후기), `REV-01` |
| 소유 도메인 | 인증 후기 |
| 기준 문서 | [인증 후기 API](review.md), [인증 후기](../../../p0/review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 회원은 본인에게 연결된 체크인 완료 방문 기록에 별점과 본문을 가진 후기 한 건을 작성할 수 있다. 서버는
경로의 방문 기록에서 콘텐츠를 결정하며 요청 본문으로 콘텐츠·작성자 식별자를 받지 않는다.

## 2. 공통 계약 참조

작성·인증·응답·오류의 공통 규칙은 [인증 후기 API](review.md#2-공통-계약-참조)를 따른다.

## 3. 체크인 방문당 후기 작성

### Request

```http
POST /api/v1/visits/{visitId}/reviews
```

#### Request Example

```http
POST /api/v1/visits/101/reviews HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "rating": 5,
  "reviewText": "지역의 이야기를 직접 들을 수 있어 좋았습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | 본인 방문 기록과 활성 회원 상태를 확인하는 `Bearer <accessToken>`이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `visitId` | Long | Y | 양의 정수인 체크인 완료 방문 기록 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "rating": 5,
  "reviewText": "지역의 이야기를 직접 들을 수 있어 좋았습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rating` | Integer | Y | `1~5` 정수만 허용하며 `null`은 허용하지 않는다. |
| `reviewText` | String | Y | 앞뒤 공백 제거 후 `1~2,000`자여야 한다. `null`, 빈 문자열, 공백만으로 된 값은 허용하지 않는다. |

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
    "reviewId": 501,
    "visitId": 101,
    "contentId": 41,
    "rating": 5,
    "reviewText": "지역의 이야기를 직접 들을 수 있어 좋았습니다.",
    "createdAt": "공통 시각 형식"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.reviewId` | Long | 생성된 후기 식별자다. |
| `data.visitId` | Long | 경로로 지정한 방문 기록 식별자다. |
| `data.contentId` | Long | 방문 기록에서 결정한 콘텐츠 식별자다. |
| `data.rating` | Integer | 저장된 별점이다. |
| `data.reviewText` | String | 공백 정리 뒤 저장된 후기 본문이다. |
| `data.createdAt` | String | 생성 시각이며 공통 시각 형식을 따른다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_TYPE` | `visitId`를 정수로 변환할 수 없다. 상태를 변경하지 않는다. |
| 400 | `INVALID_INPUT` | 경로 식별자 또는 별점·본문이 형식·범위를 만족하지 않거나, 같은 방문에 이미 후기가 있다. 상태를 변경하지 않으며 기존 후기는 수정 API로만 변경할 수 있다. |
| 400 | `INVALID_JSON` | 본문을 JSON으로 역직렬화할 수 없다. 상태를 변경하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| 403 | `FORBIDDEN` | 활성 회원이 아니거나 방문 기록의 작성자 연결이 인증 주체와 다르다. 상태를 변경하지 않는다. |
| 404 | `NOT_FOUND` | 방문 기록이 없거나 체크인 완료 방문으로 사용할 수 없다. 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

오류 응답에는 방문·회원의 내부 연결 정보나 후기 원문을 포함하지 않는다.

### 처리·감사 규칙

- 서버는 `visitId`의 방문 기록에서 `content_id`·`region_id`를, 인증 주체에서 작성자 식별을 파생한다. 이 값들은 요청 본문으로 받지 않는다.
- 활성 회원, 방문의 작성자 연결, 체크인 완료 상태와 `review.visit_id` 유일 제약을 함께 확인하는 조건부 생성으로 방문당 후기 한 건을 보장한다. 이미 `DELETED`이거나 원문이 파기된 후기라도 같은 방문으로 다시 작성할 수 없다.
- 생성 시 후기는 `PUBLISHED` 상태가 되며, 후기 생성과 대상 유형 `REVIEW`의 성공 감사 이벤트는 하나의 트랜잭션으로 커밋한다. 감사 이벤트에는 후기 원문·별점·개인정보를 복사하지 않는다.
- 회원 탈퇴 또는 동시 작성과 경합하면 먼저 조건을 만족해 커밋한 처리만 적용한다. 실패한 처리는 후기와 성공 감사 이벤트를 만들지 않는다.
