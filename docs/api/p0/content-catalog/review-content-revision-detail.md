# 심사 대기 콘텐츠 수정본 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 승인 또는 반려 전에 심사 대기 수정본의 모든 후보 필드와 기존 회차를 확인한다. 수정본 후보는
공개 콘텐츠 원본 또는 공개 전 승인본의 현재 값과 분리돼 있으며, 이 조회는 심사 결과를 변경하지 않는다.

## 2. 심사 대기 수정본 상세

### Request

```http
GET /api/v1/region-admin/content-revisions/{revisionId}
```

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `Authorization` | Header | Y | `Bearer <accessToken>` 형식의 지역 관리자 Access Token |
| `revisionId` | Path | Y | 양의 10진 문자열인 수정본 식별자 |

### Response

```http
200 OK
```

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "심사 대기 콘텐츠 수정본 상세 조회에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "reviewType": "PRE_PUBLIC_REVISION",
    "contentStatus": "PENDING",
    "title": "김해 가야문화 체험 일정 변경",
    "description": "가야 문화를 체험하는 행사입니다.",
    "representativeImageUrl": "https://example.invalid/presigned-image",
    "representativeImageUrlExpiresAt": "2026-07-30T02:30:00Z",
    "locationText": "김해문화의전당",
    "operatingHoursText": "매주 토요일 10:00~16:00",
    "contactText": "055-000-0000",
    "precautions": "편한 복장으로 참여해 주세요.",
    "ageRequirement": "초등학생 이상",
    "materials": "필기도구",
    "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다.",
    "candidatePublishAt": "2026-08-20T09:00:00+09:00",
    "sessions": [
      {
        "sessionId": "701",
        "status": "SCHEDULED",
        "startsAt": "2026-08-21T10:00:00+09:00",
        "endsAt": "2026-08-21T12:00:00+09:00",
        "checkinOpenAt": "2026-08-21T09:30:00+09:00",
        "checkinCloseAt": "2026-08-21T12:00:00+09:00",
        "capacity": 20,
        "remainingCapacity": 20
      }
    ],
    "submittedAt": "2026-07-30T02:00:00Z"
  }
}
```

| Name | Type | Description |
| --- | --- | --- |
| `data.revisionId` | String | 수정본 식별자 |
| `data.contentId` | String | 원본 콘텐츠 식별자 |
| `data.reviewType` | String | `PUBLISHED_REVISION` 또는 `PRE_PUBLIC_REVISION` |
| `data.contentStatus` | String | 현재 원본 상태. 각각 `PUBLISHED` 또는 `PENDING` |
| `data.title`~`data.cancellationPolicyText` | String | 수정본의 모든 후보 표시 필드 |
| `data.representativeImageUrl` | String | 후보 대표 이미지의 단기 presigned GET URL |
| `data.representativeImageUrlExpiresAt` | String | 후보 대표 이미지 URL의 UTC 만료 시각 |
| `data.candidatePublishAt` | String or null | 공개 전 수정본의 후보 공개 예정 시각. 공개 콘텐츠 수정본에서는 `null` |
| `data.sessions` | Array | 원본 콘텐츠의 현재 회차. 수정본으로 회차·정원·체크인 창은 변경하지 않는다. |
| `data.submittedAt` | String | 수정본 제출 시각. UTC ISO 8601 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양의 10진 문자열이 아니다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 지역 관리자 역할이 없거나 원본 콘텐츠가 담당 지역에 속하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 `EDIT_REQUESTED`가 아니거나 원본이 소프트 삭제됐다. |
| `500` | `INTERNAL_SERVER_ERROR` | 수정본과 원본 상태·후보 `publish_at` 조합 또는 후보 이미지 연결이 정책과 일치하지 않는다. |

### 처리 규칙

1. 서버는 수정본의 원본 콘텐츠를 기준으로 지역 권한을 검증한다.
2. `EDIT_REQUESTED` 수정본만 조회한다. `EDIT_APPROVED`, `EDIT_REJECTED`, `EDIT_WITHDRAWN` 수정본은 반환하지 않는다.
3. 공개 콘텐츠 수정본은 `candidatePublishAt = null`, 원본 `PUBLISHED`여야 한다.
4. 공개 전 수정본은 `candidatePublishAt`이 있고 원본 `PENDING`이며 최신 `PENDING` 상태 로그의 직전 상태 로그가 `APPROVED`여야 한다.
5. 후보 이미지의 `ACTIVE` 상태와 후보 이미지 연결을 검증한 뒤에만 presigned URL을 발급한다.
6. 회차는 원본의 현재 값만 반환하며 수정본 승인으로 변경되지 않는다.
