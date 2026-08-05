# 지역 운영 상태 변경 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02`, `ADM-05` |
| 소유 도메인 | 지역 |
| 기준 문서 | [지역 API](region.md), [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

전체관리자가 지역의 운영 상태를 변경한다. 변경은 지역 행과 성공 감사 이벤트를 같은 트랜잭션으로 커밋한다.

현재 ERD에는 지역 운영 상태 컬럼이 확정되어 있지 않다. 이 API는 `ADM-02`의 지역 운영 상태 변경 계약을 먼저
정의하되, 실제 구현 전에는 상태 종류, 전이 규칙, 공개 콘텐츠·예약·혜택에 미치는 영향과 저장 컬럼을 ADR·ERD에서
확정해야 한다.

## 2. 공통 계약 참조

변경·인증·응답·오류의 공통 규칙은 [지역 API 명세서](region.md#2-공통-계약-참조)를 따른다.

## 3. 지역 운영 상태 변경

### Request

```http
PATCH /platform-admin/regions/{regionId}/status
```

실제 요청 경로는 다음과 같다.

```http
PATCH /api/v1/platform-admin/regions/{regionId}/status
```

#### Request Example

```http
PATCH /api/v1/platform-admin/regions/3/status HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "operationalStatus": "SUSPENDED",
  "reason": "지역 운영 정책 점검으로 일시 중단"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `regionId` | String | Y | 지역 식별자. 양의 10진 정수 문자열이다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "operationalStatus": "SUSPENDED",
  "reason": "지역 운영 정책 점검으로 일시 중단"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `operationalStatus` | String | Y | 변경할 운영 상태. 허용 값은 구현 전 ADR·ERD에서 확정한다. 예시 값은 `ACTIVE`, `SUSPENDED`다. |
| `reason` | String | Y | 변경 사유. 앞뒤 공백 제거 후 1자 이상 500자 이하다. |

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
  "message": "지역 운영 상태 변경에 성공했습니다.",
  "data": {
    "regionId": "3",
    "regionCode": "JEONJU",
    "name": "전주시",
    "isPublic": false,
    "operationalStatus": "SUSPENDED",
    "updatedAt": "2026-08-05T05:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 지역 운영 상태 변경 성공 메시지 |
| `data.regionId` | String | 지역 식별자 |
| `data.regionCode` | String | 시스템에서 사용하는 지역 코드 |
| `data.name` | String | 사용자에게 표시할 지역명 |
| `data.isPublic` | Boolean | 공개 여부 |
| `data.operationalStatus` | String | 변경 후 운영 상태 |
| `data.updatedAt` | String | 지역 최종 수정 시각. UTC ISO 8601 일시 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `regionId`, `operationalStatus`, `reason`이 형식·범위를 만족하지 않는다. 지역과 감사 이력은 변경되지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 지역과 감사 이력은 변경되지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `PLATFORM_ADMIN`이 아니거나 전체관리자 계정 상태가 특권 변경을 허용하지 않는다. 지역과 감사 이력은 변경되지 않는다. |
| `404` | `NOT_FOUND` | 대상 지역이 없다. 감사 이력은 생성하지 않는다. |
| `409` | `REGION_STATUS_CONFLICT` | 이미 같은 운영 상태이거나 확정된 상태 전이 규칙상 허용되지 않는다. 지역과 감사 이력은 변경되지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 변경 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션은 롤백된다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REGION_STATUS_CONFLICT",
  "message": "지역 상태가 요청과 일치하지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 `PLATFORM_ADMIN`이어야 한다.
2. `reason`은 앞뒤 공백을 제거한 값으로 검증하고 감사 기록에 사용한다.
3. 운영 상태 허용 값과 전이 규칙은 구현 전 ADR·ERD에서 확정한다.
4. 같은 요청이 이미 반영된 상태라면 `409 REGION_STATUS_CONFLICT`를 반환하고 새 감사 이벤트를 만들지 않는다.
5. 성공 감사 이벤트는 `target_type = REGION`, 대상 `region_id`, 이전·이후 운영 상태, `result = SUCCESS`, `reason_code = REGION_STATUS_CHANGED`, 처리자 역할, 처리 시각과 `requestId`를 포함한다.
6. 지역 운영 상태가 공개 콘텐츠, 예약, 스탬프북, 미션, 쿠폰과 지역 관리자 권한에 미치는 영향은 구현 전 ADR·ERD에서 확정한다.
7. 성공 감사 이벤트에는 토큰과 개인정보를 저장하지 않는다. 활성 처리자 연결이 필요하면 `audit_event_actor_link`에만 둔다.
