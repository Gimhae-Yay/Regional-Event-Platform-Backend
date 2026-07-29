## 5. 담당 지역 승인 대기 목록 조회

지역 관리자가 자신의 담당 지역에서 승인 심사를 기다리는 콘텐츠 목록을 조회한다.
조회 결과는 승인·반려 처리를 수행하지 않으며 콘텐츠 상태와 감사 이력을 변경하지 않는다.

### Request

```http
GET /region-admin/contents?status=PENDING
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/region-admin/contents?status=PENDING
```

#### Request Example

```http
GET /api/v1/region-admin/contents?status=PENDING HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 상태의 지역 관리자여야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | 조회할 콘텐츠 상태. 이 API에서는 항상 `PENDING` |

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
  "message": "담당 지역 승인 대기 콘텐츠 목록 조회에 성공했습니다.",
  "data": {
    "contents": [
      {
        "contentId": 101,
        "contentType": "EVENT_EXPERIENCE",
        "title": "김해 가야문화 체험",
        "status": "PENDING",
        "publishAt": "2026-08-05T09:00:00+09:00",
        "submittedAt": "2026-07-29T14:30:00+09:00",
        "operator": {
          "operatorId": 20,
          "name": "김운영"
        },
        "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/101/image?X-Amz-Signature=...",
        "representativeImageUrlExpiresAt": "2026-07-29T15:00:00+09:00"
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
| `data.contents` | Array | 담당 지역의 승인 대기 콘텐츠 목록. 결과가 없으면 빈 배열 `[]` |
| `data.contents[].contentId` | Long | 콘텐츠 식별자 |
| `data.contents[].contentType` | String | 콘텐츠 유형. P0에서는 항상 `EVENT_EXPERIENCE` |
| `data.contents[].title` | String | 콘텐츠 제목 |
| `data.contents[].status` | String | 콘텐츠 상태. 항상 `PENDING` |
| `data.contents[].publishAt` | String | 운영자가 제출한 공개 예정 시각 |
| `data.contents[].submittedAt` | String | 가장 최근 `PENDING` 상태 로그의 시각 |
| `data.contents[].operator.operatorId` | Long | 콘텐츠 소유 운영자 식별자 |
| `data.contents[].operator.name` | String | 콘텐츠 소유 운영자 이름 |
| `data.contents[].representativeImageUrl` | String | 담당 지역 권한 확인 후 발급한 대표 이미지의 단기 presigned GET URL |
| `data.contents[].representativeImageUrlExpiresAt` | String | 대표 이미지 조회 URL 만료 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 누락됐거나 `PENDING`이 아니다. 조회 대상과 상태를 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 상태의 지역 관리자가 아니거나 담당 지역이 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠·소유 운영자·상태 로그·대표 이미지 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있지만 정합성 오류는 해결 전까지 재시도해도 성공하지 않는다. |

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

1. 인증 주체는 `ACTIVE` 상태이고 `REGION_ADMIN` 역할과 담당 `region_id`를 가진 회원이어야 한다.
2. 서버는 인증 주체의 역할 배정에서 담당 지역을 결정하며 클라이언트가 지역을 지정하거나 변경할 수 없다.
3. `content.region_id = 인증 지역 관리자의 담당 region_id`, `content.status = PENDING`, `content.deleted_at IS NULL`인 콘텐츠만 반환한다.
4. 다른 지역 콘텐츠의 존재 여부와 개수는 응답과 오류로 노출하지 않는다.
5. `status`가 누락되거나 `PENDING` 이외의 값이면 빈 목록으로 대체하지 않고 `INVALID_INPUT`으로 거부한다.
6. `submittedAt`은 해당 콘텐츠의 가장 최근 `status = PENDING`인 `content_log.date`를 사용한다.
7. 목록은 `submittedAt` 오름차순, 같은 시각이면 `contentId` 오름차순으로 정렬해 오래 기다린 요청을 먼저 표시한다.
8. 승인 대기 콘텐츠가 없으면 `404`가 아닌 `200 OK`와 `data.contents = []`을 반환한다.
9. P0에서는 페이지네이션, 추가 상태 필터, 검색과 사용자 지정 정렬을 제공하지 않는다.
10. 대표 이미지는 콘텐츠에 현재 연결된 `ACTIVE` 이미지 객체를 사용한다. 콘텐츠 상태 로그, 소유 운영자 또는 대표 이미지 연결이 없거나 서로 일치하지 않으면 정상 항목으로 대체하지 않고 정합성 오류로 처리한다.
11. 서버는 콘텐츠가 인증 지역 관리자의 담당 지역에 속하고 여전히 `PENDING`인지 확인한 뒤 비공개 S3 객체의 단기 presigned GET URL과 정확한 만료 시각을 함께 발급한다.
12. presigned URL과 만료 시각은 DB나 Redis에 저장하지 않고 응답을 조립할 때마다 새로 생성한다. `representativeImageUrlExpiresAt` 이후에는 기존 URL을 재사용하지 않고 API를 다시 조회한다.
13. 응답에는 대표 이미지 조회 URL과 만료 시각만 제공하며 `imageObjectId`, S3 `object_key`, 원본 파일명과 사용자 식별정보를 별도 필드로 노출하지 않는다.
14. 조회 시 콘텐츠, 회차, 이미지, 상태 로그와 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 담당 지역 식별자, 결과 건수와 결과 코드만 구조화 로그로 남긴다.
- 운영자 이름, 대표 이미지 객체 키와 다른 개인정보를 구조화 로그에 남기지 않는다.
- 담당 지역 조건은 조회 쿼리와 응답 조립 과정 모두에 적용하며, 다른 지역 데이터로 누락값을 보완하지 않는다.
