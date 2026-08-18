# 심사 대기 콘텐츠 수정본 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [ADR-0037](../../../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md), [API 공통 계약](../../common/README.md) |

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
| `Authorization` | Header | Y | `Bearer <accessToken>` 형식의 `ROLE_REGION_ADMIN` snapshot Access Token |
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
    "reservationPrice": 20000,
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
| `data.reservationPrice` | Integer | 수정본 후보 예약 기본 금액이다. 정수 KRW이며 `0` 이상이다. 승인 전에는 원본 가격을 바꾸지 않는다. |
| `data.representativeImageUrl` | String | 후보 대표 이미지의 단기 presigned GET URL |
| `data.representativeImageUrlExpiresAt` | String | 후보 대표 이미지 URL의 UTC 만료 시각 |
| `data.candidatePublishAt` | String or null | 공개 전 수정본의 후보 공개 예정 시각. 공개 콘텐츠 수정본에서는 `null` |
| `data.sessions` | Array | 원본 콘텐츠의 현재 회차. 수정본으로 회차·정원·체크인 창은 변경하지 않는다. |
| `data.submittedAt` | String | 수정본 제출 시각. UTC ISO 8601 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`를 `Long`으로 해석한 값이 양수가 아니다. 수정본·원본 콘텐츠·이미지 객체와 감사 기록은 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `revisionId`를 signed 64비트 `Long`으로 변환할 수 없다. 수정본·원본 콘텐츠·이미지 객체와 감사 기록은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본·원본 콘텐츠·이미지 객체와 감사 기록은 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 원본 콘텐츠가 현재 담당 지역에 속하지 않는다. 수정본·원본 콘텐츠·이미지 객체와 감사 기록은 변경하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 `EDIT_REQUESTED`가 아니거나 원본이 소프트 삭제됐다. 콘텐츠 중단·전체 철회·종료로 `EDIT_INVALIDATED`가 된 수정본도 심사 대상이 아니므로 포함한다. 심사 대상이 아닌 수정본의 존재·상태는 노출하지 않으며 수정본·원본 콘텐츠·이미지 객체와 감사 기록은 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 수정본과 원본 상태·후보 `publish_at` 조합 또는 후보 이미지 연결이 정책과 일치하지 않거나 예상하지 못한 서버 오류가 발생했다. 수정본·원본 콘텐츠·이미지 객체와 감사 기록은 변경하지 않는다. |

### 처리 규칙

1. 인증 주체는 `ROLE_REGION_ADMIN` snapshot을 가지고 활성 `ORDINARY` 계정이어야 한다. 서버는 현재 담당 `region_id`와 수정본 원본 콘텐츠 `region_id`가 일치하는지 검증한다.
2. 원본 콘텐츠가 소프트 삭제되지 않았고 수정본이 `EDIT_REQUESTED`인 경우만 반환한다. `EDIT_APPROVED`, `EDIT_REJECTED`, `EDIT_WITHDRAWN`, `EDIT_INVALIDATED` 수정본과 소프트 삭제된 원본의 수정본은 반환하지 않는다. 이미 무효화된 수정본의 상태 조합을 심사 유형으로 분류하지 않아 `500`을 반환하지 않는다.
3. 공개 콘텐츠 수정본은 `candidatePublishAt = null`, 원본 `PUBLISHED`여야 한다.
4. 공개 전 수정본은 `candidatePublishAt`이 있고 원본 `PENDING`이며 최신 `PENDING` 상태 로그의 직전 상태 로그가 `APPROVED`여야 한다.
5. 후보 이미지의 `ACTIVE` 상태와 수정본의 유효한 직접 연결을 검증한 뒤에만 단기 presigned GET URL과 정확한 UTC 만료 시각을 발급한다. 권한 검증을 통과하기 전에는 URL을 발급하지 않는다.
6. `representativeImageUrl`과 만료 시각은 DB나 Redis에 저장하지 않고 응답을 조립할 때마다 새로 생성한다. 응답에는 `imageObjectId`, S3 객체 키, 원본 파일명 또는 사용자 식별정보를 포함하지 않는다.
7. 회차는 원본의 현재 값만 반환하며 수정본 승인으로 회차·정원·체크인 창은 변경하지 않는다.
8. 이 조회는 수정본·원본 콘텐츠·이미지 객체를 생성·수정·삭제하지 않고 감사 이벤트도 생성하지 않는다.

### 감사 및 관측

- 조회 성공과 실패는 `requestId`, 담당 지역 식별자, 수정본 식별자와 결과 코드만 구조화 로그에 남긴다.
- 후보 이미지 객체 키, 후보 필드 원문과 사용자 식별정보는 구조화 로그에 남기지 않는다.
