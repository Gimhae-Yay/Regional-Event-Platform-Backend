## 4. 지역 홈·진행/임박 콘텐츠 조회

공개 지역의 홈에서 현재 진행 중인 콘텐츠와 다음 회차가 예정된 콘텐츠를 조회한다.
공개된 콘텐츠와 회차의 현재 운영 상태만 사용하며 콘텐츠, 회차, 홀드와 예약 상태를 변경하지 않는다.

### Request

```http
GET /regions/{regionId}/home
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/regions/{regionId}/home
```

#### Request Example

```http
GET /api/v1/regions/1/home HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 인증이 필요하지 않은 공개 API |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `regionId` | String | Y | 홈을 조회할 공개 지역 식별자. 양수여야 한다. |

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
  "message": "지역 홈 조회에 성공했습니다.",
  "data": {
    "region": {
      "regionId": "1",
      "regionCode": "GIMHAE",
      "name": "김해시"
    },
    "ongoingContents": [
      {
        "contentId": "101",
        "contentType": "EVENT_EXPERIENCE",
        "title": "김해 가야문화 체험",
        "locationText": "김해시 가야의길 190",
        "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/101/image?X-Amz-Signature=...",
        "representativeImageUrlExpiresAt": "2026-07-29T03:05:00Z",
        "reservationAvailable": true,
        "displaySession": {
          "sessionId": "1001",
          "startsAt": "2026-07-29T10:00:00+09:00",
          "endsAt": "2026-07-29T12:00:00+09:00",
          "remainingCapacity": 4
        }
      }
    ],
    "upcomingContents": [
      {
        "contentId": "102",
        "contentType": "EVENT_EXPERIENCE",
        "title": "낙동강 생태 체험",
        "locationText": "김해시 생림면 일대",
        "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/102/image?X-Amz-Signature=...",
        "representativeImageUrlExpiresAt": "2026-07-29T03:05:00Z",
        "reservationAvailable": true,
        "displaySession": {
          "sessionId": "1002",
          "startsAt": "2026-08-02T14:00:00+09:00",
          "endsAt": "2026-08-02T16:00:00+09:00",
          "remainingCapacity": 12
        }
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
| `data.region.regionId` | String | 조회한 공개 지역 식별자 |
| `data.region.regionCode` | String | 시스템에서 사용하는 지역 코드 |
| `data.region.name` | String | 사용자에게 표시할 지역명 |
| `data.ongoingContents` | Array | 현재 진행 중인 회차가 있는 공개 콘텐츠 목록. 결과가 없으면 빈 배열 `[]` |
| `data.upcomingContents` | Array | 진행 중인 회차가 없고 향후 회차가 있는 공개 콘텐츠 목록. 결과가 없으면 빈 배열 `[]` |
| `data.ongoingContents[].contentId` | String | 콘텐츠 식별자 |
| `data.ongoingContents[].contentType` | String | 콘텐츠 유형. P0에서는 항상 `EVENT_EXPERIENCE` |
| `data.ongoingContents[].title` | String | 콘텐츠 제목 |
| `data.ongoingContents[].locationText` | String | 콘텐츠 위치 안내 |
| `data.ongoingContents[].representativeImageUrl` | String | 권한·공개 상태 확인 후 발급한 현재 대표 이미지의 단기 presigned GET URL |
| `data.ongoingContents[].representativeImageUrlExpiresAt` | String | 대표 이미지 조회 URL 만료 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.ongoingContents[].reservationAvailable` | Boolean | 예약 가능한 향후 회차가 하나 이상 존재하는지 여부 |
| `data.ongoingContents[].displaySession.sessionId` | String | 홈에 표시할 진행 중인 회차 식별자 |
| `data.ongoingContents[].displaySession.startsAt` | String | 표시 회차 시작 시각 |
| `data.ongoingContents[].displaySession.endsAt` | String | 표시 회차 종료 시각 |
| `data.ongoingContents[].displaySession.remainingCapacity` | Integer | 표시 회차 잔여 정원. 0 이상 |
| `data.upcomingContents[].contentId` | String | 콘텐츠 식별자 |
| `data.upcomingContents[].contentType` | String | 콘텐츠 유형. P0에서는 항상 `EVENT_EXPERIENCE` |
| `data.upcomingContents[].title` | String | 콘텐츠 제목 |
| `data.upcomingContents[].locationText` | String | 콘텐츠 위치 안내 |
| `data.upcomingContents[].representativeImageUrl` | String | 권한·공개 상태 확인 후 발급한 현재 대표 이미지의 단기 presigned GET URL |
| `data.upcomingContents[].representativeImageUrlExpiresAt` | String | 대표 이미지 조회 URL 만료 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.upcomingContents[].reservationAvailable` | Boolean | 예약 가능한 향후 회차가 하나 이상 존재하는지 여부 |
| `data.upcomingContents[].displaySession.sessionId` | String | 홈에 표시할 가장 가까운 향후 회차 식별자 |
| `data.upcomingContents[].displaySession.startsAt` | String | 표시 회차 시작 시각 |
| `data.upcomingContents[].displaySession.endsAt` | String | 표시 회차 종료 시각 |
| `data.upcomingContents[].displaySession.remainingCapacity` | Integer | 표시 회차 잔여 정원. 0 이상 |

`upcomingContents`의 필드 구조는 `ongoingContents`와 동일하다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `regionId`가 양수가 아니다. 조회 대상과 상태를 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `regionId`를 `Long`으로 변환할 수 없다. 조회 대상과 상태를 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `404` | `NOT_FOUND` | 지역을 찾을 수 없거나 공개되지 않은 지역이다. 조회 대상과 상태를 변경하지 않으며 지역 식별자와 공개 상태를 확인한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠·회차·대표 이미지 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있지만 정합성 오류는 해결 전까지 재시도해도 성공하지 않는다. |

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

1. `regionId`로 조회한 지역이 존재하고 `region.is_public = true`여야 한다. 비공개 지역은 대상 부재와 동일하게 처리한다.
2. 콘텐츠는 요청 지역과 같은 `region_id`, `status = PUBLISHED`, `deleted_at IS NULL`인 경우에만 공개한다.
3. 시간 비교에는 서버 애플리케이션 시각이 아닌 MySQL 기준 현재 시각을 사용한다.
4. 진행 중 콘텐츠는 `status = SCHEDULED`이고 `starts_at <= 현재 시각 < ends_at`인 회차가 하나 이상 있는 콘텐츠다.
5. 한 콘텐츠에 진행 중 회차가 여러 개이면 `ends_at`이 가장 빠르고, 같은 시각이면 `session_id`가 가장 작은 회차를 `displaySession`으로 반환한다.
6. 임박 콘텐츠는 진행 중 회차가 없고 `status = SCHEDULED`, `starts_at > 현재 시각`인 향후 회차가 하나 이상 있는 콘텐츠다.
7. 한 콘텐츠에 향후 회차가 여러 개이면 `starts_at`이 가장 빠르고, 같은 시각이면 `session_id`가 가장 작은 회차를 `displaySession`으로 반환한다.
8. 하나의 콘텐츠는 `ongoingContents`와 `upcomingContents` 중 한 목록에만 포함한다. 진행 중 조건을 먼저 적용한다.
9. `reservationAvailable`은 콘텐츠가 `PUBLISHED`이고, `status = SCHEDULED`, `starts_at > 현재 시각`, `remaining_capacity > 0`인 회차가 하나 이상 있을 때 `true`다.
10. 진행 중 목록은 `displaySession.endsAt` 오름차순, 같은 시각이면 `contentId` 오름차순으로 정렬하고 최대 10건을 반환한다.
11. 임박 목록은 별도 날짜 임계값 대신 현재 시각 이후 가장 가까운 `displaySession.startsAt` 순으로 정렬하며, 같은 시각이면 `contentId` 오름차순으로 정렬하고 최대 10건을 반환한다.
12. P0에서는 페이지네이션, 유형 필터와 사용자 지정 정렬을 적용하지 않는다.
13. Redis에는 지역과 콘텐츠의 정적 표시 정보만 캐시한다. 목록 구분과 `displaySession`, `remainingCapacity`, `reservationAvailable`은 요청마다 MySQL의 현재 회차·정원 상태로 계산하며 전체 API 응답을 캐시하지 않는다.
14. 서버는 캐시 사용 여부와 관계없이 MySQL의 현재 `PUBLISHED` 상태와 `version_no`를 확인하며, 이전 버전의 공개본을 정상 응답으로 반환하지 않는다.
15. 캐시 키·버전 검증·무효화 실패 처리·TTL과 Redis 장애 시 MySQL 우회 정책은 [ADR-0029](../../../adr/0029-use-version-validated-cache-aside-for-public-content.md)를 따른다.
16. 공개 콘텐츠는 `ACTIVE` 상태의 대표 이미지 객체와 현재 대표 이미지 연결이 있어야 한다. 연결이 없거나 삭제 대기 객체가 연결돼 있으면 정상 콘텐츠로 대체하지 않고 정합성 오류로 처리한다.
17. 서버는 콘텐츠가 여전히 `PUBLISHED`이고 현재 대표 이미지가 유효한지 확인한 뒤 비공개 S3 객체의 단기 presigned GET URL과 정확한 만료 시각을 함께 발급한다.
18. presigned URL과 만료 시각은 DB나 Redis에 저장하지 않고 응답을 조립할 때마다 새로 생성한다. `representativeImageUrlExpiresAt` 이후에는 기존 URL을 재사용하지 않고 API를 다시 조회한다.
19. 응답에는 대표 이미지 조회 URL과 만료 시각만 제공하며 `imageObjectId`, S3 `object_key`, 원본 파일명과 사용자 식별정보를 별도 필드로 노출하지 않는다.
20. 조회 시 지역, 콘텐츠, 회차, 이미지, 홀드, 예약과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 지역 식별자, 진행·임박 결과 건수와 결과 코드만 구조화 로그로 남긴다.
- 대표 이미지 객체 키, 사용자 식별정보와 개인정보를 로그에 남기지 않는다.
- 정적 표시 정보 캐시가 없거나 만료되거나 Redis를 사용할 수 없어도 MySQL 조회로 같은 공개 범위와 정렬 결과를 만들 수 있어야 한다.
