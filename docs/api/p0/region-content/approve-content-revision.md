# 콘텐츠 수정본 승인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0037](../../../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 심사 대기 수정본을 승인한다. 공개 콘텐츠 수정본은 후보 필드만 현재 공개본에 반영한다.
공개 전 수정본은 후보 필드와 후보 공개 예정 시각을 반영하고 원본을 `PENDING → APPROVED`로 재승인한다.

## 2. 수정본 승인

### Request

```http
POST /api/v1/region-admin/content-revisions/{revisionId}/approve
```

요청 본문은 없다.

| Name | Type | Required | Description |
| --- | --- | --- |
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
  "message": "콘텐츠 수정본 승인에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "revisionStatus": "EDIT_APPROVED",
    "contentStatus": "APPROVED",
    "reservationPrice": 20000,
    "publishAt": "2026-08-20T09:00:00+09:00",
    "reviewedAt": "2026-07-30T02:10:00Z"
  }
}
```

| Name | Type | Description |
| --- | --- | --- |
| `data.revisionId` | String | 승인한 수정본 식별자 |
| `data.contentId` | String | 원본 콘텐츠 식별자 |
| `data.revisionStatus` | String | 승인 뒤 `EDIT_APPROVED` |
| `data.contentStatus` | String | 공개 콘텐츠 수정본이면 `PUBLISHED`, 공개 전 수정본이면 `APPROVED` |
| `data.reservationPrice` | Integer | 승인 뒤 원본에 반영된 예약 기본 금액이다. 정수 KRW이며 `0` 이상이다. |
| `data.publishAt` | String | 승인 뒤 원본의 공개 예정 시각 |
| `data.reviewedAt` | String | 승인 처리 시각. UTC ISO 8601 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양의 10진 문자열이 아니다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 지역 관리자 역할이 없거나 원본 콘텐츠가 담당 지역에 속하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 원본 콘텐츠가 소프트 삭제됐다. |
| `409` | `CONTENT_STATE_CONFLICT` | 수정본이 `EDIT_REQUESTED`가 아니거나 원본 상태·버전·후보 `publish_at` 조건이 맞지 않거나, 반려·철회·자동 공개·콘텐츠 중단·종료에 따른 `EDIT_INVALIDATED` 전이가 먼저 종결됐다. |

### 처리 규칙

1. 담당 지역 관리자만 원본 콘텐츠의 `region_id` 범위에서 처리할 수 있다.
2. 수정본 `EDIT_REQUESTED`, 원본 `deleted_at IS NULL`, `content.version_no = content_revision.base_content_version`을 함께 조건으로 확인한다.
3. `content_revision.publish_at IS NULL`이면 원본은 `PUBLISHED`여야 한다. 후보 표시 필드·`reservation_price`·대표 이미지를 반영하고 원본 상태·기존 `publish_at`은 유지한다.
4. `content_revision.publish_at IS NOT NULL`이면 원본은 `PENDING`이고 최신 `PENDING` 상태 로그의 직전 상태 로그가 `APPROVED`여야 한다. 후보 표시 필드·`reservation_price`와 후보 `publish_at`을 반영하고 원본을 `PENDING → APPROVED`로 전이한다.
5. 두 경우 모두 원본 반영, 원본 버전 증가, 수정본 `EDIT_APPROVED` 전이, 필요한 `APPROVED` 상태 로그와 성공 감사 기록을 하나의 MySQL 트랜잭션에서 함께 커밋한다. 이미 생성된 `reservation_price_snapshot`은 수정하지 않는다.
6. 승인·반려·철회·자동 공개·콘텐츠 중단·종료가 경합하면 현재 수정본·원본 상태와 버전을 조건으로 먼저 커밋한 하나만 성공한다. 콘텐츠 중단·종료가 먼저 `EDIT_INVALIDATED`를 커밋하면 이 요청은 `409 CONTENT_STATE_CONFLICT`로 거부한다.
