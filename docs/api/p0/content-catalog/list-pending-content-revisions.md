# 심사 대기 콘텐츠 수정본 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0037](../../../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 자신의 지역에서 `EDIT_REQUESTED` 상태인 콘텐츠 수정본을 조회한다. 공개 콘텐츠 수정본과
공개 전 수정 심사로 원본이 `PENDING`인 수정본을 함께 반환한다. 이 API는 심사 결과·원본 콘텐츠·수정본을 변경하지 않는다.

## 2. 심사 대기 수정본 목록

### Request

```http
GET /api/v1/region-admin/content-revisions?status=EDIT_REQUESTED
```

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `Authorization` | Header | Y | `Bearer <accessToken>` 형식의 지역 관리자 Access Token |
| `status` | Query | Y | 항상 `EDIT_REQUESTED` |

### Response

```http
200 OK
```

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "담당 지역 심사 대기 수정본 목록 조회에 성공했습니다.",
  "data": {
    "revisions": [
      {
        "revisionId": "501",
        "contentId": "101",
        "reviewType": "PRE_PUBLIC_REVISION",
        "contentStatus": "PENDING",
        "title": "김해 가야문화 체험 일정 변경",
        "candidatePublishAt": "2026-08-20T09:00:00+09:00",
        "submittedAt": "2026-07-30T02:00:00Z",
        "operator": {
          "operatorId": "20",
          "name": "김운영"
        },
        "representativeImageUrl": "https://example.invalid/presigned-image",
        "representativeImageUrlExpiresAt": "2026-07-30T02:30:00Z"
      }
    ]
  }
}
```

| Name | Type | Description |
| --- | --- | --- |
| `data.revisions` | Array | 담당 지역의 심사 대기 수정본 목록. 없으면 빈 배열 `[]` |
| `data.revisions[].revisionId` | String | 수정본 식별자 |
| `data.revisions[].contentId` | String | 원본 콘텐츠 식별자 |
| `data.revisions[].reviewType` | String | `PUBLISHED_REVISION` 또는 `PRE_PUBLIC_REVISION` |
| `data.revisions[].contentStatus` | String | 현재 원본 상태. 각각 `PUBLISHED` 또는 `PENDING` |
| `data.revisions[].title` | String | 수정 후보 제목 |
| `data.revisions[].candidatePublishAt` | String or null | 공개 전 수정본의 후보 공개 예정 시각. `PUBLISHED_REVISION`에서는 `null` |
| `data.revisions[].submittedAt` | String | 수정본 제출 시각. UTC ISO 8601 형식 |
| `data.revisions[].operator` | Object | 원본 콘텐츠 소유 운영자 |
| `data.revisions[].representativeImageUrl` | String | 후보 대표 이미지의 단기 presigned GET URL |
| `data.revisions[].representativeImageUrlExpiresAt` | String | 후보 대표 이미지 URL의 UTC 만료 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 없거나 `EDIT_REQUESTED`가 아니다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 지역 관리자 역할 또는 담당 지역이 없다. |
| `500` | `INTERNAL_SERVER_ERROR` | 수정본 상태와 원본 상태·후보 `publish_at` 조합이 정책과 일치하지 않는다. |

### 처리 규칙

1. 인증 지역 관리자의 담당 `region_id`와 원본 콘텐츠의 `region_id`가 같은 수정본만 반환한다.
2. 수정본은 `EDIT_REQUESTED`, 원본은 소프트 삭제되지 않은 상태여야 한다.
3. `content_revision.publish_at IS NULL`이면 원본은 `PUBLISHED`여야 하며 `PUBLISHED_REVISION`으로 반환한다.
4. `content_revision.publish_at IS NOT NULL`이면 원본은 `PENDING`이고 최신 `PENDING` 상태 로그의 직전 상태 로그는 `APPROVED`여야 하며, `PRE_PUBLIC_REVISION`으로 반환한다.
5. 수정본 제출 시각 오름차순, 같은 시각이면 `revisionId` 오름차순으로 정렬한다.
6. 후보 이미지, 원본 콘텐츠 또는 수정본의 정합성이 깨진 행은 다른 정상 행으로 대체하지 않고 서버 오류로 처리한다.

## 3. 감사 및 정합성

- 조회는 `content`, `content_revision`, `content_log`, 이미지 연결과 감사 기록을 변경하지 않는다.
- 성공·실패 로그에는 `requestId`, 담당 지역, 결과 건수와 결과 코드만 남기며 이미지 객체 키·개인정보는 남기지 않는다.
