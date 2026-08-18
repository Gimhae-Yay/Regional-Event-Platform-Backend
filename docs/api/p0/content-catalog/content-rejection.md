# 콘텐츠 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-04 승인·자동 공개·종료](../../../p0/content-catalog.md#fr-04-승인자동-공개종료), `CON-01`, `CON-09` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0019](../../../adr/0019-use-minimal-content-status-log.md), [ADR-0021](../../../adr/0021-record-content-reasons-in-content-log.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 심사 대기 콘텐츠를 사유와 함께 반려하는 API다. 서버는 `PENDING → REJECTED` 상태 전이,
사유를 포함한 `content_log` 추가와 감사 기록을 하나의 트랜잭션으로 처리한다. 반려된 콘텐츠는 일반 사용자에게 노출되지 않으며,
운영자는 보완 후 다시 제출할 수 있다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-04, CON-01, CON-09 | `POST /api/v1/region-admin/contents/{contentId}/reject` | `content`, `content_log`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 `application/json; charset=UTF-8`을 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 담당 지역의 `REGION_ADMIN` 역할만 허용한다. 콘텐츠의 `region_id`와 인증 주체의 담당 지역이 같아야 한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 공통 네 필드를 사용하며 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 사유를 포함한 콘텐츠 반려

`deleted_at IS NULL`이고 현재 상태가 `PENDING`인 최초 심사·반려 후 재제출 콘텐츠만 반려할 수 있다. 최신 `PENDING`
상태 로그의 직전 상태 로그가 `APPROVED`인 콘텐츠는 공개 전 수정 심사 대기이므로, 수정본 식별자를 기준으로 한 별도
심사 계약으로 반려해야 하며 이 API로 원본을 `REJECTED`로 전이할 수 없다. 동일 콘텐츠의 승인 또는 반려가 경합하면
현재 상태를 조건으로 먼저 성공한 요청만 상태와 로그를 변경한다. 이미 `REJECTED` 상태이고 요청 `reason`이 기존 반려 사유와
같은 재요청은 기존 반려 결과를 `200 OK`로 반환하며 상태·이력·감사 기록을 추가로 변경하지 않는다. 기존 반려 사유와 다른
재요청은 상태 충돌로 거부한다.

### Request

```http
POST /api/v1/region-admin/contents/{contentId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/contents/123/reject HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "회차별 정원 정보를 확인할 수 없습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 담당 지역 관리자 인증에 사용한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 반려할 콘텐츠 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "회차별 정원 정보를 확인할 수 없습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 반려 사유. `null`, 빈 문자열, 공백만으로 된 값은 허용하지 않는다. 상태 로그와 감사 기록에 보존한다. |

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
  "message": "콘텐츠 반려에 성공했습니다.",
  "data": {
    "contentId": 123,
    "status": "REJECTED",
    "rejectedAt": "{ISO 8601 형식과 기준 시간대}"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `콘텐츠 반려에 성공했습니다.`다. |
| `data.contentId` | Long | 반려한 콘텐츠 식별자다. |
| `data.status` | String | 반려 후 상태 `REJECTED`다. |
| `data.rejectedAt` | String | `REJECTED` 콘텐츠 로그의 처리 시각이다. 시간 형식은 API 공통 규칙의 확정 값을 따른다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `contentId`가 양수가 아니거나 `reason`이 누락·빈 값이다. 상태와 이력은 변경하지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 상태와 이력은 변경하지 않는다. |
| 400 | `INVALID_TYPE` | `contentId`를 Long으로 변환할 수 없다. 상태와 이력은 변경하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태와 이력은 변경하지 않는다. |
| 403 | `FORBIDDEN` | 지역 관리자 역할이 없거나 콘텐츠의 담당 지역이 다르다. 상태와 이력은 변경하지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠가 존재하지 않거나 이미 소프트 삭제됐다. 상태와 이력은 변경하지 않는다. |
| 409 | `CONTENT_STATE_CONFLICT` | 콘텐츠가 `PENDING`이 아니고 요청 `reason`과 같은 기존 반려 결과도 없거나, 공개 전 수정 심사 대기 콘텐츠이거나, 동시 승인 요청이 먼저 처리됐다. 상태와 이력은 변경하지 않으며 최신 상세를 조회한 뒤 판단한다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_STATE_CONFLICT",
  "message": "콘텐츠 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```
