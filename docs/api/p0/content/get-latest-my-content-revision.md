# 내 최신 콘텐츠 수정본 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0014](../../../adr/0014-store-published-content-edits-in-relational-revision-tables.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 콘텐츠 식별자로 가장 최근 수정본의 저장된 후보 필드, 후보 대표 이미지와 심사 상태를 조회한다. 반려 뒤
새로고침하거나 다른 브라우저에서 별도 수정본 식별자를 보관하지 않아도 작업을 이어 갈 수 있도록 가장 큰 `revision_no`를
가진 수정본을 반환한다. 현재 원본 콘텐츠와 후보 수정본을 혼합하지 않으며 이미지 객체 식별자와 객체 키는 노출하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `GET /operator/contents/{contentId}/revisions/latest` | `content_revision`, `content`, `image_object` |
| `AUTH-01` | `GET /operator/contents/{contentId}/revisions/latest` | 운영자 역할, `content.operator_id`, `content.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}/revisions/latest`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 원본 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 저장된 수정본 후보 스냅샷을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 내 최신 콘텐츠 수정본 상세 조회

### Request

```http
GET /api/v1/operator/contents/{contentId}/revisions/latest
```

#### Request Example

```http
GET /api/v1/operator/contents/101/revisions/latest HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 양의 10진 문자열인 최신 수정본을 조회할 원본 콘텐츠 식별자다. |

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
  "message": "내 최신 콘텐츠 수정본 상세 조회에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "revisionNo": 2,
    "baseContentVersion": 3,
    "status": "EDIT_REJECTED",
    "title": "김해 가야문화 체험",
    "description": "가야 문화를 체험하는 행사입니다.",
    "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/...",
    "representativeImageUrlExpiresAt": "2026-08-18T04:00:00Z",
    "locationText": "김해시 가야의길 190",
    "operatingHoursText": "매주 토요일 10:00~16:00",
    "contactText": "055-000-0000",
    "precautions": "편한 복장으로 참여해 주세요.",
    "ageRequirement": "초등학생 이상",
    "materials": "필기도구",
    "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다.",
    "reservationPrice": 20000,
    "publishAt": null,
    "reviewReason": "후보 대표 이미지의 행사명이 잘못되었습니다.",
    "submittedAt": "2026-08-18T01:00:00Z",
    "reviewedAt": "2026-08-18T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.revisionId` | String | 조회한 수정본 식별자다. |
| `data.contentId` | String | 수정본의 소프트 삭제되지 않은 원본 콘텐츠 식별자다. |
| `data.revisionNo` | Integer | 콘텐츠 안에서 1부터 증가하는 수정본 번호다. |
| `data.baseContentVersion` | Integer | 수정본을 처음 만들었을 때의 원본 콘텐츠 버전이다. |
| `data.status` | String | `EDIT_REQUESTED`, `EDIT_APPROVED`, `EDIT_REJECTED`, `EDIT_WITHDRAWN`, `EDIT_INVALIDATED` 중 하나다. |
| `data.title` | String | 저장된 후보 콘텐츠 제목이다. |
| `data.description` | String | 저장된 후보 콘텐츠 소개다. |
| `data.representativeImageUrl` | String | 후보 대표 이미지의 짧은 유효기간 presigned GET URL이다. |
| `data.representativeImageUrlExpiresAt` | String | 후보 대표 이미지 URL의 UTC 만료 시각이다. |
| `data.locationText` | String | 저장된 후보 위치 안내다. |
| `data.operatingHoursText` | String | 저장된 후보 운영 시간 안내다. |
| `data.contactText` | String | 저장된 후보 연락처 안내다. |
| `data.precautions` | String | 저장된 후보 유의사항이다. |
| `data.ageRequirement` | String | 저장된 후보 연령 조건이다. |
| `data.materials` | String | 저장된 후보 준비물이다. |
| `data.cancellationPolicyText` | String | 저장된 후보 취소 정책 안내 문구다. |
| `data.reservationPrice` | Integer | 저장된 후보 예약 기본 금액이다. 정수 KRW이며 0 이상이다. |
| `data.publishAt` | String or null | 공개 전 수정본의 후보 공개 예정 시각이다. `Asia/Seoul` 기준 `+09:00` 오프셋 형식이며 공개 콘텐츠 수정본이면 `null`이다. |
| `data.reviewReason` | String or null | `EDIT_REJECTED`이면 지역 관리자가 저장한 비어 있지 않은 반려 사유이고, 그 외 상태이면 `null`이다. |
| `data.submittedAt` | String | 최초 심사 요청 시각이다. UTC ISO 8601 형식이다. |
| `data.reviewedAt` | String or null | `EDIT_APPROVED` 또는 `EDIT_REJECTED` 처리 시각이고, 그 외 상태이면 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양의 10진 문자열 형식이 아니거나 signed 64비트 `Long` 범위를 벗어난다. 수정본을 반환하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 반환하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 원본 콘텐츠 소유 관계가 없다. 수정본을 반환하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐거나 해당 콘텐츠에 수정본이 없다. 수정본을 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 후보 이미지·상태별 처리 정보 정합성 오류가 발생했다. 수정본을 반환하지 않는다. |

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

1. 서버는 원본 콘텐츠를 찾은 뒤 인증된 운영자의 소유 관계와 현재 담당 지역을 검증하고, 해당 콘텐츠에서 가장 큰
   `revision_no`를 가진 수정본 한 건을 조회한다. 클라이언트가 소유자, 지역, 콘텐츠 유형 또는 수정본 식별자를 지정할 수
   없으며 소프트 삭제된 원본의 수정본은 반환하지 않는다.
2. 후보 필드는 수정본에 저장된 스냅샷을 반환한다. 현재 원본 콘텐츠의 값으로 대체하거나 병합하지 않는다.
3. 후보 대표 이미지의 객체 키와 이미지 객체 식별자는 노출하지 않는다. 소유권 검증 뒤 후보 이미지에 대한 짧은 유효기간의
   presigned GET URL과 만료 시각만 발급한다.
4. `reviewReason`은 `EDIT_REJECTED`일 때만, `reviewedAt`은 `EDIT_APPROVED` 또는 `EDIT_REJECTED`일 때만 반환한다.
   상태별 필수 값이 없거나 금지된 값이 존재하면 정합성 오류로 처리한다.
5. 조회 결과가 `EDIT_REJECTED`이면 응답의 `revisionId`로 [콘텐츠 수정본 편집](update-content-revision.md)을 호출해
   후보 필드를 보완하고, [반려 콘텐츠 수정본 재제출](resubmit-content-revision.md)로 새 심사를 요청할 수 있다.
   조회 자체는 수정본, 원본, 이미지 연결과 감사 기록을 변경하지 않는다.
