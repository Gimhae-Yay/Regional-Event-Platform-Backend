# 내 미션 목록 조회 API 명세서

## 1. 개요

콘텐츠 운영자가 담당 지역의 미션 목록을 조회한다.

### Request

```http
GET /api/v1/operator/missions
```

#### Request Example

```http
GET /api/v1/operator/missions?status=DRAFT&page=0&size=20 HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | N | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 중 하나 |
| `page` | Integer | N | 0부터 시작하는 페이지 번호. 기본값 `0`, 음수 불가 |
| `size` | Integer | N | 페이지 크기. 기본값 `20`, 허용 범위 `1~100` |

사용자 지정 정렬은 제공하지 않는다. `missionId` 내림차순으로 고정한다.

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
  "message": "내 미션 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "missionId": "701",
        "status": "DRAFT",
        "conditionType": "CONTENT_SET",
        "endsAt": "2026-09-30T23:59:59+09:00"
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
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 운영자 미션 목록 조회 성공 메시지 |
| `data.content` | Array | 담당 지역 미션 목록. 없으면 빈 배열이며 `null`이 아님 |
| `data.content[].missionId` | String | 미션 식별자 |
| `data.content[].status` | String | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 중 하나 |
| `data.content[].conditionType` | String | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `data.content[].endsAt` | String | 예정 종료 시각. ISO 8601 `+09:00` 오프셋 형식 |
| `data.page` | Integer | 0부터 시작하는 현재 페이지 번호 |
| `data.size` | Integer | 요청에 적용된 페이지 크기 |
| `data.totalElements` | Long | 조건에 맞는 전체 미션 수 |
| `data.totalPages` | Integer | 전체 페이지 수. 결과가 없으면 `0` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 쿼리 파라미터가 유효하지 않다. |
| `400` | `INVALID_TYPE` | 쿼리 파라미터를 선언된 타입으로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_OPERATOR` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 현재 담당 지역이 없다. |

### 처리 규칙

1. Access Token의 `ROLE_OPERATOR` authority를 1차로 확인하고, DB에서 활성 `ORDINARY` 계정의 현재 담당 지역 미션만 반환한다.
2. 빈 결과는 `200 OK`, 빈 `content` 배열, `totalElements = 0`, `totalPages = 0`으로 반환한다.
