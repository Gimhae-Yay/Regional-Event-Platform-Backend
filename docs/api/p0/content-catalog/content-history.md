# 콘텐츠 반려·승인·종료 이력 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-04 승인·자동 공개·종료](../../../p0/content-catalog.md#fr-04-승인자동-공개종료), `CON-09` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0019](../../../adr/0019-use-minimal-content-status-log.md), [ADR-0021](../../../adr/0021-record-content-reasons-in-content-log.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 콘텐츠의 상태 이력을 조회하는 API다. 이력은 `content_log`의 추가 전용 기록을 `date`, `id` 오름차순으로
반환하며, 반려(`REJECTED`), 승인(`APPROVED`), 종료(`ENDED`)뿐 아니라 생성·자동 공개와 소프트 삭제(`DELETED`)를 포함한
모든 콘텐츠 상태 이력을 함께 반환한다. 탈퇴한 처리자는 식별자 대신 공통 표시값으로 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-04, CON-09 | `GET /api/v1/region-admin/contents/{contentId}/history` | `content`, `content_log` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 `application/json; charset=UTF-8`을 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot으로 1차 인가하고, DB에서 활성 `ORDINARY` 계정과 현재 담당 지역·콘텐츠 `region_id` 일치를 확인한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 공통 네 필드를 사용하며 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 해당 콘텐츠의 전체 이력을 반환하며 페이지네이션을 적용하지 않는다. |

## 3. 콘텐츠 상태 이력 조회

이력 행은 수정·개별 삭제하지 않는다. 소프트 삭제된 콘텐츠도 담당 지역 관리자에게 이력 조회를 허용하며 `DELETED` 로그를
반환한다. `REJECTED`, `SUSPENDED`, `WITHDRAWN`, `DELETED`의 `reason`은 필수이고, 생성·승인·자동 공개·종료 로그의
`reason`은 `null`일 수 있다. 자동 공개와 종료는 시스템 처리이므로 `actor`를 `null`로 반환한다.
`WITHDRAWN` 이력은 [전체 콘텐츠 철회 승인](../region-content/approve-content-withdrawal.md)이 저장한 요청 사유,
승인 관리자와 승인 시각을 반환하며, 수정본 `EDIT_WITHDRAWN` 이력과 혼합하지 않는다.

### Request

```http
GET /api/v1/region-admin/contents/{contentId}/history
```

#### Request Example

```http
GET /api/v1/region-admin/contents/123/history HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 담당 지역 관리자 인증에 사용한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 이력을 조회할 콘텐츠 식별자. 양수여야 한다. |

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
  "message": "콘텐츠 이력 조회에 성공했습니다.",
  "data": {
    "contentId": 123,
    "histories": [
      {
        "status": "REJECTED",
        "reason": "회차별 정원 정보를 확인할 수 없습니다.",
        "processedAt": "{ISO 8601 형식과 기준 시간대}",
        "actor": {
          "userId": 8,
          "displayName": "김해 지역 관리자"
        }
      },
      {
        "status": "APPROVED",
        "reason": null,
        "processedAt": "{ISO 8601 형식과 기준 시간대}",
        "actor": {
          "userId": 8,
          "displayName": "김해 지역 관리자"
        }
      },
      {
        "status": "ENDED",
        "reason": null,
        "processedAt": "{ISO 8601 형식과 기준 시간대}",
        "actor": null
      },
      {
        "status": "DELETED",
        "reason": "등록 요청을 철회했습니다.",
        "processedAt": "{ISO 8601 형식과 기준 시간대}",
        "actor": {
          "userId": 8,
          "displayName": "김해 지역 관리자"
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `콘텐츠 이력 조회에 성공했습니다.`다. |
| `data.contentId` | Long | 이력을 조회한 콘텐츠 식별자다. |
| `data.histories` | Array&lt;Object&gt; | `processedAt`, 내부 로그 식별자 오름차순의 전체 콘텐츠 상태 이력이다. 빈 이력은 빈 배열로 반환한다. |
| `data.histories[].status` | String | 변경 뒤 콘텐츠 상태 또는 로그 전용 삭제 이벤트다. `PENDING`, `REJECTED`, `APPROVED`, `PUBLISHED`, `SUSPENDED`, `WITHDRAWN`, `ENDED`, `DELETED` 중 하나다. `DELETED`는 현재 `content.status`가 아닌 소프트 삭제 로그 전용 코드다. |
| `data.histories[].reason` | String \| null | 상태 전이 또는 삭제 사유다. `REJECTED`, `SUSPENDED`, `WITHDRAWN`, `DELETED`에서는 null이 아니고, `PENDING`, `APPROVED`, `PUBLISHED`, `ENDED`에서는 `null`일 수 있다. |
| `data.histories[].processedAt` | String | 콘텐츠 로그의 처리 시각이다. 시간 형식은 API 공통 규칙의 확정 값을 따른다. |
| `data.histories[].actor` | Object \| null | 사용자 처리자다. 자동 공개·종료 같은 시스템 처리에는 `null`이다. |
| `data.histories[].actor.userId` | Long \| null | 활성 처리자의 식별자다. 처리자가 탈퇴한 경우 `null`이다. |
| `data.histories[].actor.displayName` | String | 처리자 표시 이름이다. 처리자가 탈퇴한 경우 `탈퇴한 사용자`를 반환한다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `contentId`가 양수가 아니다. 이력은 변경하지 않는다. |
| 400 | `INVALID_TYPE` | `contentId`를 Long으로 변환할 수 없다. 이력은 변경하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 이력은 변경하지 않는다. |
| 403 | `FORBIDDEN` | `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정, 담당 지역 또는 콘텐츠 지역이 맞지 않는다. 이력은 변경하지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠가 존재하지 않는다. 소프트 삭제된 콘텐츠는 담당 지역 관리자에게 `DELETED` 이력을 포함해 반환한다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
