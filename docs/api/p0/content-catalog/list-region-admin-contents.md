# 담당 지역 콘텐츠 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `FR-10`, `FR-14`, `AUTH-01`, `CON-01`, `CON-03`, `CON-08` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [공개 전 콘텐츠 상태별 삭제](../region-content/delete-content.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md), [ADR-0016](../../../adr/0016-use-private-s3-presigned-urls-and-immediate-image-deletion.md) |

## 1. 개요

담당 지역 관리자가 자신의 담당 지역에서 승인 심사를 기다리는 `PENDING` 콘텐츠 또는 승인됐지만 아직 공개되지 않은
`APPROVED` 콘텐츠를 `status` 파라미터로 구분해 조회한다. `PENDING` 목록은 승인·반려 대상을 제공하고,
`APPROVED` 목록은 공개 전 상태와 삭제 대상을 확인할 수 있게 한다.

이 API는 하나의 경로에서 `PENDING`, `APPROVED` 두 상태를 지원한다. 두 상태를 한 응답에 섞거나 `status`를 생략한
전체 목록은 제공하지 않는다. 조회는 콘텐츠, 이미지, 상태 로그와 감사 기록을 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-04`, `CON-01` | `GET /region-admin/contents?status=PENDING` | `content`, `content_log`, `app_user` |
| `FR-04`, `FR-10`, `FR-14`, `CON-03`, `CON-08` | `GET /region-admin/contents?status=APPROVED` | `content`, `content_log`, `app_user` |
| `AUTH-01` | `GET /region-admin/contents?status={status}` | `content.region_id`, `user_role_assignment.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/contents?status={status}`다. 공개 예정 시각은 `+09:00`, 상태 전이 시각은 UTC `Z`, 식별자는 양의 10진 문자열로 표현한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot과 활성 `ORDINARY` 계정·현재 담당 지역 조건이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 요청한 상태의 콘텐츠 배열을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0 단순 목록으로 페이지네이션을 적용하지 않는다. |

## 3. 담당 지역 콘텐츠 목록 조회

### Request

```http
GET /region-admin/contents?status={status}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/region-admin/contents?status={status}
```

#### Request Example

```http
GET /api/v1/region-admin/contents?status=PENDING HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

```http
GET /api/v1/region-admin/contents?status=APPROVED HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 `ROLE_REGION_ADMIN` snapshot을 가져야 하며 DB에서 활성 `ORDINARY` 계정인지 확인한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | 조회할 콘텐츠 상태. `PENDING`, `APPROVED` 중 하나여야 한다. |

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

#### `status=PENDING` Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "담당 지역 승인 대기 콘텐츠 목록 조회에 성공했습니다.",
  "data": {
    "contents": [
      {
        "contentId": "101",
        "contentType": "EVENT_EXPERIENCE",
        "title": "김해 가야문화 체험",
        "status": "PENDING",
        "publishAt": "2026-08-20T09:00:00+09:00",
        "submittedAt": "2026-08-18T05:00:00Z",
        "approvedAt": null,
        "operator": {
          "operatorId": "20",
          "name": "김운영"
        },
        "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/101/image?X-Amz-Signature=...",
        "representativeImageUrlExpiresAt": "2026-08-18T05:30:00Z"
      }
    ]
  }
}
```

#### `status=APPROVED` Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "담당 지역 승인 완료 콘텐츠 목록 조회에 성공했습니다.",
  "data": {
    "contents": [
      {
        "contentId": "102",
        "contentType": "EVENT_EXPERIENCE",
        "title": "동해 바다 공예 체험",
        "status": "APPROVED",
        "publishAt": "2026-08-21T09:00:00+09:00",
        "submittedAt": null,
        "approvedAt": "2026-08-18T05:30:00Z",
        "operator": {
          "operatorId": "21",
          "name": "이운영"
        },
        "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/102/image?X-Amz-Signature=...",
        "representativeImageUrlExpiresAt": "2026-08-18T06:00:00Z"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | 조건 | Description |
| --- | --- | --- | --- |
| `statusCode` | Number | 항상 | HTTP 상태 코드. 항상 `200` |
| `code` | String | 항상 | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 항상 | 요청한 상태에 대응하는 공개 성공 메시지 |
| `data.contents` | Array | 항상 | 요청한 상태의 담당 지역 콘텐츠 목록. 결과가 없으면 빈 배열 `[]` |
| `data.contents[].contentId` | String | 항상 | 콘텐츠 식별자 |
| `data.contents[].contentType` | String | 항상 | 콘텐츠 유형. P0에서는 항상 `EVENT_EXPERIENCE` |
| `data.contents[].title` | String | 항상 | 콘텐츠 제목 |
| `data.contents[].status` | String | 항상 | 요청한 `status`와 같은 콘텐츠 현재 상태 |
| `data.contents[].publishAt` | String | 항상 | 운영자가 제출하고 승인 대상 또는 승인 결과로 유지되는 공개 예정 시각 |
| `data.contents[].submittedAt` | String \| null | 항상 | `PENDING`이면 가장 최근 `PENDING` 상태 로그의 시각이고, `APPROVED`이면 `null`이다. |
| `data.contents[].approvedAt` | String \| null | 항상 | `APPROVED`이면 현재 상태를 만든 가장 최근 승인 로그의 시각이고, `PENDING`이면 `null`이다. |
| `data.contents[].operator.operatorId` | String | 항상 | 콘텐츠 소유 운영자 식별자 |
| `data.contents[].operator.name` | String | 항상 | 콘텐츠 소유 운영자 이름 |
| `data.contents[].representativeImageUrl` | String | 항상 | 담당 지역과 요청 상태 확인 후 발급한 대표 이미지의 단기 presigned GET URL |
| `data.contents[].representativeImageUrlExpiresAt` | String | 항상 | 대표 이미지 조회 URL 만료 시각. API 공통 규칙에 따른 UTC ISO 8601 일시 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 누락됐거나 `PENDING`, `APPROVED` 중 하나가 아니다. 조회 대상과 상태를 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정 또는 담당 지역 관계가 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
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

1. 인증 주체는 `ROLE_REGION_ADMIN` snapshot을 가지고 `ACTIVE` 상태의 `ORDINARY` 계정이어야 하며, 현재 담당 `region_id`를 가져야 한다.
2. 서버는 인증 주체의 역할 배정에서 담당 지역을 결정하며 클라이언트가 지역을 지정하거나 변경할 수 없다.
3. 모든 조회는 `content.region_id = 인증 지역 관리자의 담당 region_id`, `content.status = 요청 status`, `content.deleted_at IS NULL`을 만족해야 한다.
4. `status=PENDING`이면 최신 `PENDING` 상태 로그의 직전 상태 로그가 `APPROVED`가 아닌 최초 심사 또는 반려 후 재제출 콘텐츠만 반환한다. 공개 전 수정 심사 때문에 `APPROVED → PENDING`이 된 콘텐츠는 [심사 대기 수정본 목록](list-pending-content-revisions.md)에서 반환한다.
5. `status=APPROVED`이면 승인됐지만 아직 `PUBLISHED`로 전이되지 않은 콘텐츠를 반환한다. 공개 예정 시각이 지났더라도 조회 시점의 현재 상태가 `APPROVED`이면 포함하며 `publish_at`만으로 공개 상태로 간주하지 않는다.
6. 다른 지역 콘텐츠의 존재 여부와 개수는 응답과 오류로 노출하지 않는다.
7. `status`가 누락되거나 `PENDING`, `APPROVED` 이외의 값이면 빈 목록으로 대체하지 않고 `INVALID_INPUT`으로 거부한다.
8. `PENDING` 응답은 가장 최근 `status = PENDING`인 `content_log.date`를 `submittedAt`으로 반환하고 `approvedAt = null`로 반환한다. `APPROVED` 응답은 현재 상태를 만든 가장 최근 `status = APPROVED`인 `content_log.date`를 `approvedAt`으로 반환하고 `submittedAt = null`로 반환한다.
9. `PENDING` 목록은 `submittedAt` 오름차순, `APPROVED` 목록은 `publishAt` 오름차순으로 정렬한다. 같은 시각이면 모두 `contentId` 오름차순으로 정렬한다.
10. 조회 결과가 없으면 `404`가 아닌 `200 OK`와 `data.contents = []`을 반환한다.
11. P0에서는 페이지네이션, 추가 상태 필터, 검색과 사용자 지정 정렬을 제공하지 않는다.
12. 대표 이미지는 콘텐츠에 현재 연결된 `ACTIVE` 이미지 객체를 사용한다. 콘텐츠 상태 로그, 소유 운영자 또는 대표 이미지 연결이 없거나 서로 일치하지 않으면 정상 항목으로 대체하지 않고 정합성 오류로 처리한다.
13. 서버는 콘텐츠가 인증 지역 관리자의 담당 지역에 속하고 요청한 상태인지 확인한 뒤 비공개 S3 객체의 단기 presigned GET URL과 정확한 만료 시각을 함께 발급한다.
14. presigned URL과 만료 시각은 DB나 Redis에 저장하지 않고 응답을 조립할 때마다 새로 생성한다. `representativeImageUrlExpiresAt` 이후에는 기존 URL을 재사용하지 않고 API를 다시 조회한다.
15. 응답에는 대표 이미지 조회 URL과 만료 시각만 제공하며 `imageObjectId`, S3 `object_key`, 원본 파일명과 이미지 업로드 요청자 식별정보를 별도 필드로 노출하지 않는다.
16. `APPROVED` 목록 조회 뒤 자동 공개 또는 삭제가 먼저 커밋될 수 있으므로, 삭제 API는 목록 응답을 권한·상태 근거로 신뢰하지 않고 대상 콘텐츠의 담당 지역, 허용 상태와 `deleted_at IS NULL`을 다시 검증한다.
17. 조회 시 콘텐츠, 회차, 이미지, 상태 로그와 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 담당 지역 식별자, 요청 상태, 결과 건수와 결과 코드만 구조화 로그로 남긴다.
- 운영자 이름, 콘텐츠 제목, 대표 이미지 객체 키와 다른 개인정보를 구조화 로그에 남기지 않는다.
- 담당 지역, 요청 상태와 `deleted_at IS NULL` 조건은 조회 쿼리와 응답 조립 과정 모두에 적용하며, 다른 지역이나 다른 상태의 데이터로 누락값을 보완하지 않는다.
