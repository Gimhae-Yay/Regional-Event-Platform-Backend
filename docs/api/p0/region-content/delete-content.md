# 공개 전 콘텐츠 상태별 삭제 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-08`, `CON-09` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [인증·프로필](../../../p0/auth-profile.md), [API 공통 계약](../../common/README.md), 선행 계약 결정 PR |

## 1. 개요

담당 지역 관리자가 공개 전 콘텐츠를 사유와 함께 소프트 삭제한다. 삭제는 `PENDING`, `APPROVED`에만
허용하며, `PUBLISHED` 이후 상태의 콘텐츠는 삭제하지 않고 상태와 사유 이력을 보존한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14`, `CON-08` | `DELETE /region-admin/contents/{contentId}` | `content`, `content_log` |
| `AUTH-01`, `CON-09` | `DELETE /region-admin/contents/{contentId}` | `content.region_id`, `user_role_assignment`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/contents/{contentId}`; 생성·수정·심사·처리 이벤트 시각은 ISO 8601 UTC `Z` 문자열이며 콘텐츠 일정값만 `+09:00` 오프셋 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN`과 대상 콘텐츠의 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 `CONTENT_DELETE_CONFLICT`를 포함한 API별 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 명령 API이므로 적용하지 않음 |

## 3. 공개 전 콘텐츠 상태별 삭제

### Request

```http
DELETE /region-admin/contents/{contentId}
```

#### Request Example

```http
DELETE /api/v1/region-admin/contents/101 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "reason": "행사 준비가 취소되었습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 상태의 `REGION_ADMIN`이어야 한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 삭제할 콘텐츠 식별자. 양의 10진 문자열이어야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "행사 준비가 취소되었습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `reason` | String | Y | 삭제 사유. 공백만으로 구성할 수 없으며 `content_log.status = DELETED` 로그에 기록한다. |

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
  "message": "공개 전 콘텐츠 삭제에 성공했습니다.",
  "data": {
    "contentId": "101",
    "deletionEventStatus": "DELETED",
    "deletedAt": "2026-07-30T01:00:00Z",
    "deletionReason": "행사 준비가 취소되었습니다."
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.contentId` | String | 양의 10진 문자열 삭제된 콘텐츠 식별자 |
| `data.deletionEventStatus` | String | 항상 로그 전용 이벤트 코드 `DELETED` |
| `data.deletedAt` | String | `content.deleted_at`과 `DELETED` 로그의 처리 시각. UTC `Z` 문자열 |
| `data.deletionReason` | String | 저장된 삭제 사유 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양의 10진 문자열이 아니거나 `reason`이 없거나 공백뿐이다. 콘텐츠·로그·감사 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `contentId`를 양의 10진 문자열 식별자로 해석할 수 없다. 콘텐츠·로그·감사 기록을 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 콘텐츠·로그·감사 기록을 변경하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 콘텐츠·로그·감사 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 대상 콘텐츠의 지역이 담당 지역과 일치하지 않는다. 콘텐츠·로그·감사 기록을 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없다. 콘텐츠·로그·감사 기록을 변경하지 않으며 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `CONTENT_DELETE_CONFLICT` | 콘텐츠가 `PENDING`, `APPROVED` 중 하나가 아니거나 이미 소프트 삭제됐거나, 다른 삭제·상태 전이가 먼저 성공했다. 콘텐츠·로그·성공 감사 기록을 부분 변경하지 않으며 현재 상태를 다시 조회해야 한다. 롤백 뒤에는 비개인 실패 `audit_event`를 별도 트랜잭션으로 기록한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠·이미지·회차 연결의 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 콘텐츠·로그·감사 기록은 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_DELETE_CONFLICT",
  "message": "콘텐츠를 삭제할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 상태이며 담당 `region_id`가 연결된 `REGION_ADMIN`이어야 한다.
2. 대상 콘텐츠 `region_id`와 인증 지역 관리자의 담당 `region_id`가 일치해야 한다.
3. `content.status IN (PENDING, APPROVED)`와 `deleted_at IS NULL`을 동시에 만족한 최초 요청만 성공한다.
4. 성공 시 `content.deleted_at`을 기록하고 `content_log`에 사유가 있는 `DELETED` 이벤트를 추가한다. `DELETED`는 `content.status` 값이 아니다.
5. 성공 시 콘텐츠의 대표 이미지 직접 FK와 연결 시각을 제거한다. 제거 대상 `image_object` 행을 잠근 뒤 `content`와 모든 `content_revision`의 직접 FK 참조를 검사해 참조가 0건일 때만 `DELETE_PENDING`으로 전이한다.
6. 대표 이미지 FK 해제와 필요 시 `DELETE_PENDING` 전이는 소프트 삭제·`DELETED` 로그·성공 감사 기록과 같은 MySQL 트랜잭션에서 커밋한다. 커밋 뒤에만 비공개 S3 원본 삭제를 즉시 시도하고 실패하면 이미지 삭제 재시도 정책을 따른다.
7. 삭제 후 승인·자동 게시·복구와 다른 상태 전이를 허용하지 않는다.
8. 자동 게시와 삭제가 경합하면 현재 상태와 `deleted_at IS NULL` 조건을 먼저 충족해 커밋한 처리만 성공한다.
9. `PUBLISHED`, `SUSPENDED`, `WITHDRAWN`, `ENDED`는 이 API로 삭제할 수 없다.
10. 소유 운영자의 직접 삭제는 허용하지 않는다.

### 감사 및 정합성

- `deleted_at` 설정, 사유가 있는 `DELETED` 로그와 성공 `audit_event`는 하나의 MySQL 트랜잭션에서 함께 커밋하거나 함께 롤백한다.
- 삭제 전 대표 이미지 객체를 잠그고 모든 `content`·`content_revision`의 직접 FK 참조를 검사한다. 참조가 0건일 때만 `DELETE_PENDING`으로 전이하며, 둘 이상의 객체를 함께 잠글 때는 식별자 오름차순을 사용한다.
- `DELETE_PENDING` 커밋 뒤에만 비공개 S3 원본을 즉시 삭제하고 실패하면 재시도한다. 참조 중인 객체는 삭제하지 않는다.
- 성공 감사 기록은 처리자, 처리 시각, 콘텐츠 식별자와 삭제 사유를 재현할 수 있어야 한다.
- 롤백된 삭제 거부·충돌은 콘텐츠·로그·성공 감사 이벤트를 남기지 않고, 롤백 완료 뒤 별도 트랜잭션에서 비개인 `FAILURE` `audit_event`로 기록한다.
