# 콘텐츠 수정본 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0037](../../../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 심사 대기 수정본을 사유와 함께 반려한다. 공개 콘텐츠 수정본은 원본 `PUBLISHED` 상태와
표시 필드를 유지한다. 공개 전 수정본은 원본 후보 필드를 반영하지 않고 `PENDING` 상태를 유지해 자동 공개를 재개하지 않는다.

## 2. 수정본 반려

### Request

```http
POST /api/v1/region-admin/content-revisions/{revisionId}/reject
```

```json
{
  "reason": "공개 예정 시각과 운영 시간의 정합성을 보완해 주세요."
}
```

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `Authorization` | Header | Y | `Bearer <accessToken>` 형식의 지역 관리자 Access Token |
| `revisionId` | Path | Y | 양의 10진 문자열인 수정본 식별자 |
| `reason` | Body String | Y | 앞뒤 공백을 제거한 비어 있지 않은 반려 사유 |

### Response

```http
200 OK
```

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "콘텐츠 수정본 반려에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "revisionStatus": "EDIT_REJECTED",
    "contentStatus": "PENDING",
    "reviewReason": "공개 예정 시각과 운영 시간의 정합성을 보완해 주세요.",
    "reviewedAt": "2026-07-30T02:10:00Z"
  }
}
```

| Name | Type | Description |
| --- | --- | --- |
| `data.revisionId` | String | 반려한 수정본 식별자 |
| `data.contentId` | String | 원본 콘텐츠 식별자 |
| `data.revisionStatus` | String | 반려 뒤 `EDIT_REJECTED` |
| `data.contentStatus` | String | 공개 콘텐츠 수정본이면 `PUBLISHED`, 공개 전 수정본이면 `PENDING` |
| `data.reviewReason` | String | 저장된 반려 사유 |
| `data.reviewedAt` | String | 반려 처리 시각. UTC ISO 8601 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양의 10진 문자열이 아니거나 `reason`이 누락·공백이다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 지역 관리자 역할이 없거나 원본 콘텐츠가 담당 지역에 속하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 원본 콘텐츠가 소프트 삭제됐다. |
| `409` | `CONTENT_STATE_CONFLICT` | 수정본이 `EDIT_REQUESTED`가 아니거나 원본 상태·후보 `publish_at` 조건이 맞지 않거나, 승인·철회·자동 공개가 먼저 종결됐다. |

### 처리 규칙

1. 담당 지역 관리자만 원본 콘텐츠의 `region_id` 범위에서 처리할 수 있다.
2. 수정본 `EDIT_REQUESTED`와 원본의 정책상 유효한 상태·후보 `publish_at` 조합을 조건으로 확인한다.
3. 공개 콘텐츠 수정본 반려는 수정본만 `EDIT_REJECTED`로 전이하며 원본 `PUBLISHED` 상태, 필드와 `publish_at`을 유지한다.
4. 공개 전 수정본 반려는 수정본만 `EDIT_REJECTED`로 전이하며 원본 후보 필드를 반영하지 않고 `PENDING` 상태를 유지한다.
5. 수정본 상태 전이, 처리자·시각·반려 사유 저장과 성공 감사 기록은 하나의 MySQL 트랜잭션에서 함께 커밋한다.
6. 승인·반려·철회·자동 공개가 경합하면 현재 수정본·원본 상태를 조건으로 먼저 커밋한 하나만 성공한다.
