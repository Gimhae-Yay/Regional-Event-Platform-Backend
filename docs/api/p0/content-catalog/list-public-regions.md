## 3. 공개 지역 목록 조회

공개 상태인 지역을 조회해 사용자가 탐색할 지역을 선택할 수 있게 한다.
인증이 필요하지 않은 공개 조회 API이며 지역, 콘텐츠와 예약 상태를 변경하지 않는다.

### Request

```http
GET /regions
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/regions
```

#### Request Example

```http
GET /api/v1/regions HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 인증이 필요하지 않은 공개 API |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

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
  "message": "공개 지역 목록 조회에 성공했습니다.",
  "data": {
    "regions": [
      {
        "regionId": "1",
        "regionCode": "GIMHAE",
        "name": "김해시"
      },
      {
        "regionId": "2",
        "regionCode": "DONGHAE",
        "name": "동해시"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.regions` | Array | 공개 지역 목록. 결과가 없으면 빈 배열 `[]` |
| `data.regions[].regionId` | String | 지역 식별자 |
| `data.regions[].regionCode` | String | 시스템에서 사용하는 지역 코드 |
| `data.regions[].name` | String | 사용자에게 표시할 지역명 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `500` | `INTERNAL_SERVER_ERROR` | 공개 지역 목록 조회 중 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 500,
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 오류가 발생했습니다.",
  "data": null
}
```

### 처리 규칙

1. `region.is_public = true`인 지역만 반환한다.
2. 공개 지역이 없으면 `404`가 아닌 `200 OK`와 `data.regions = []`을 반환한다.
3. 목록은 `region.name` 오름차순, 같은 이름이면 `region_id` 오름차순으로 정렬한다.
4. P0에서는 페이지네이션, 검색, 사용자 지정 정렬을 제공하지 않는다.
5. 비공개 지역의 식별자, 이름과 존재 여부를 응답에 포함하지 않는다.
6. 조회 시 지역, 콘텐츠, 회차, 예약과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 결과 건수와 결과 코드만 구조화 로그로 남긴다.
- 비공개 지역 정보와 사용자 식별정보를 로그에 남기지 않는다.
