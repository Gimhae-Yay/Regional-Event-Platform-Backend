# 콘텐츠 수정본 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 공개 콘텐츠 또는 공개 전 승인 콘텐츠의 완전한 후보 정보를 제출해 즉시 심사 중인
`EDIT_REQUESTED` 수정본을 만든다. 공개 콘텐츠 수정본은 현재 공개본을 유지하며, 공개 전 승인 콘텐츠의
수정본은 원본을 `APPROVED → PENDING`으로 전이해 자동 공개를 차단한다. 새 대표 이미지를 지정하지 않으면
현재 원본의 대표 이미지를 수정본에 스냅샷으로 연결한다.

이 API는 원본 콘텐츠에서 새 수정 후보를 작성하는 경로다. 이미 연결된 후보 대표 이미지를 포함한 `EDIT_REJECTED`
수정본의 작업을 이어서 다시 심사 요청할 때는 [반려 콘텐츠 수정본 재제출](resubmit-content-revision.md)을 사용한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `POST /operator/contents/{contentId}/revisions` | `content`, `content_revision`, `image_object` |
| `CON-05` | `POST /operator/contents/{contentId}/revisions` | `content.status`, `content.version_no`, `content_revision.status` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}/revisions`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 심사 요청 수정본 식별자를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 콘텐츠 수정본 생성

### Request

```http
POST /api/v1/operator/contents/{contentId}/revisions
```

#### Request Example

```http
POST /api/v1/operator/contents/101/revisions HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "title": "김해 가야문화 체험",
  "description": "가야 문화를 체험하는 행사입니다.",
  "locationText": "김해시 가야의길 190",
  "operatingHoursText": "매주 토요일 10:00~16:00",
  "contactText": "055-000-0000",
  "precautions": "편한 복장으로 참여해 주세요.",
  "ageRequirement": "초등학생 이상",
  "materials": "필기도구",
  "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다.",
  "reservationPrice": 20000,
  "publishAt": "2026-08-20T09:00:00+09:00",
  "representativeImageObjectId": "301"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 양의 10진 문자열인 수정본을 만들 원본 콘텐츠 식별자다. |

#### Query Parameter

없음.

#### Request Body

요청 예시의 JSON 객체를 사용한다. `representativeImageObjectId`를 생략하면 현재 원본의 대표 이미지를 후보 이미지로 복사한다.
원본이 `PUBLISHED`이면 `publishAt`은 생략하며 수정본에는 `NULL`로 저장한다. 원본이 `APPROVED`이거나
앞선 공개 전 수정본 종결 뒤 재보완 대기인 `PENDING`이면 `publishAt`은 필수다.

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | String | Y | 비어 있지 않은 콘텐츠 제목 |
| `description` | String | Y | 비어 있지 않은 콘텐츠 소개 |
| `locationText` | String | Y | 비어 있지 않은 위치 안내 |
| `operatingHoursText` | String | Y | 비어 있지 않은 운영 시간 안내 |
| `contactText` | String | Y | 비어 있지 않은 연락처 안내 |
| `precautions` | String | Y | 비어 있지 않은 유의사항 |
| `ageRequirement` | String | Y | 비어 있지 않은 연령 조건 |
| `materials` | String | Y | 비어 있지 않은 준비물 |
| `cancellationPolicyText` | String | Y | P0 무료 예약 취소 정책을 안내하는 비어 있지 않은 문구 |
| `reservationPrice` | Integer | Y | 원본 콘텐츠 가격 변경 후보를 포함한 모든 회차의 예약 기본 금액이다. 정수 KRW이며 0 이상이다. |
| `publishAt` | String | 조건부 | `APPROVED` 원본 또는 공개 전 수정 심사로 `PENDING`인 원본에는 필수인 새 공개 예정 시각이다. `PUBLISHED` 원본에서는 제공할 수 없으며 수정본에 `NULL`로 저장한다. |
| `representativeImageObjectId` | String | N | 이미지 변경 시에만 제공하는 양의 10진 문자열인 이미 존재하는 `ACTIVE` 이미지 객체 식별자. 연결 전 대표 이미지 업로드 URL 발급 API의 연결 검증 조건을 모두 확인한다. |

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
  "message": "콘텐츠 수정본 생성과 승인 요청에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "status": "EDIT_REQUESTED",
    "baseContentVersion": 3,
    "submittedAt": "2026-07-30T02:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.revisionId` | String | 양의 10진 문자열인 생성한 수정본 식별자 |
| `data.contentId` | String | 양의 10진 문자열인 원본 콘텐츠 식별자 |
| `data.status` | String | 생성 직후 심사 요청 상태 `EDIT_REQUESTED` |
| `data.baseContentVersion` | Integer | 수정본 생성 시점 원본의 버전 |
| `data.submittedAt` | String | 심사 요청 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 식별자·후보 필드가 유효하지 않거나 지정한 대표 이미지 객체 연결 검증에 실패한다. 수정본을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 수정본을 생성하지 않는다. |
| `400` | `INVALID_TYPE` | 이미지 객체 식별자가 JSON 문자열이 아니다. 수정본을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 수정본을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐다. 수정본을 생성하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 콘텐츠가 `PUBLISHED`·`APPROVED`가 아니거나 공개 전 수정 심사에 따른 재보완 대기 `PENDING`이 아니거나, 심사 중인 수정본이 이미 있거나, 원본 상태와 `publishAt` 조건이 맞지 않는다. 원본과 수정본을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_STATE_CONFLICT",
  "message": "콘텐츠 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 원본은 `deleted_at IS NULL`이어야 하며 콘텐츠별 `EDIT_REQUESTED` 수정본은 동시에 하나만 허용한다.
2. `PUBLISHED` 원본에는 `publishAt`을 제공할 수 없으며 수정본의 후보 `publish_at`은 `NULL`이다. 수정본 생성은 원본의 상태·내용·대표 이미지 연결과 공개 조회 결과를 변경하지 않는다.
3. `APPROVED` 원본에는 `publishAt`이 필수다. 서버는 수정본 생성, 원본 `APPROVED → PENDING` 전이, `PENDING` 상태 로그와 성공 감사 기록을 하나의 트랜잭션으로 처리한다.
4. `PENDING` 원본은 활성 수정본이 없고 직전 공개 전 수정 요청의 `APPROVED → PENDING` 이력이 있을 때만 재보완 후보를 만들 수 있으며, `publishAt`이 필수다. 최초 등록 후의 일반 `PENDING` 콘텐츠는 이 API로 수정본을 만들 수 없다.
5. 서버는 모든 후보 필드와 0 이상 정수 KRW `reservationPrice`를 검증해 수정본에 저장한다. 수정본 승인 전 원본 가격은 바뀌지 않고, 승인된 후보 가격만 원본에 반영한다. 이미 생성된 결제 가격 스냅샷은 가격 변경으로 수정하지 않는다. `representativeImageObjectId`가 있으면 [대표 이미지 S3 업로드 URL 발급](upload-representative-image.md)의 연결 검증 조건을 모두 만족하는 이미지 객체만 후보 대표 이미지로 연결한다.
6. 이미지 객체 ID를 생략하면 현재 원본 대표 이미지 객체와 연결 시각을 수정본에 스냅샷으로 저장한다.
7. `EDIT_REJECTED` 수정본의 후보를 복구해 다시 제출하는 용도로 이 API를 사용하지 않는다. 해당 흐름은 기존 수정본의
   후보 대표 이미지까지 서버에서 복제하는 [반려 콘텐츠 수정본 재제출](resubmit-content-revision.md)을 사용한다.
