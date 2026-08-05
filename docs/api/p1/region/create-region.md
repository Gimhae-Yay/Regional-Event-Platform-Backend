# 지역 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02`, `ADM-05` |
| 소유 도메인 | 지역 |
| 기준 문서 | [지역 API](region.md), [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

전체관리자가 새 지역을 생성한다. 생성 직후 지역은 공개 사용자 탐색에 노출되지 않도록 `isPublic = false`로
저장한다. 지역 생성과 성공 감사 이벤트는 하나의 트랜잭션으로 커밋하며, 지역 또는 감사 이벤트 저장 중 하나라도
실패하면 모두 롤백한다.

전체관리자 권한 모델과 최초 계정 준비 절차는 P1 구현 전 ADR·ERD에서 확정해야 한다.

## 2. 공통 계약 참조

생성·인증·응답·오류의 공통 규칙은 [지역 API 명세서](region.md#2-공통-계약-참조)를 따른다.

## 3. 지역 생성

새 지역의 시스템 코드와 표시 이름을 생성한다. 생성 사유는 특권 감사 이력의 근거로 사용하며 공개 응답에는 포함하지 않는다.

### Request

```http
POST /platform-admin/regions
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/platform-admin/regions
```

#### Request Example

```http
POST /api/v1/platform-admin/regions HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "regionCode": "JEONJU",
  "name": "전주시",
  "reason": "P1 파일럿 운영 지역 추가"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "regionCode": "JEONJU",
  "name": "전주시",
  "reason": "P1 파일럿 운영 지역 추가"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `regionCode` | String | Y | 시스템 지역 코드. 앞뒤 공백 제거 후 대문자로 정규화하며 `^[A-Z][A-Z0-9_]{1,31}$`를 만족해야 한다. 대소문자를 구분하지 않는 중복은 허용하지 않는다. |
| `name` | String | Y | 사용자에게 표시할 지역명. 앞뒤 공백 제거 후 1자 이상 50자 이하다. |
| `reason` | String | Y | 지역 생성 사유. 앞뒤 공백 제거 후 1자 이상 500자 이하다. 감사 이력에만 저장하고 공개 응답에는 포함하지 않는다. |

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
  "message": "지역 생성에 성공했습니다.",
  "data": {
    "regionId": "3",
    "regionCode": "JEONJU",
    "name": "전주시",
    "isPublic": false,
    "createdAt": "2026-08-05T04:30:00Z",
    "updatedAt": "2026-08-05T04:30:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 지역 생성 성공 메시지 |
| `data.regionId` | String | 생성된 지역 식별자 |
| `data.regionCode` | String | 생성된 지역의 시스템 코드 |
| `data.name` | String | 생성된 지역의 표시 이름 |
| `data.isPublic` | Boolean | 생성 직후 공개 여부. 항상 `false` |
| `data.createdAt` | String | 지역 생성 시각. UTC ISO 8601 일시 |
| `data.updatedAt` | String | 지역 최종 수정 시각. 생성 응답에서는 `createdAt`과 같다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 요청 본문이 없거나 `regionCode`, `name`, `reason`이 형식·범위를 만족하지 않는다. 지역과 감사 이력은 생성되지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 지역과 감사 이력은 생성되지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `PLATFORM_ADMIN`이 아니거나 전체관리자 계정 상태가 특권 변경을 허용하지 않는다. 지역과 감사 이력은 생성되지 않는다. |
| `409` | `REGION_CODE_ALREADY_EXISTS` | 정규화한 `regionCode`와 같은 지역 코드가 이미 존재한다. 지역과 감사 이력은 생성되지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 지역 생성 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 롤백된다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REGION_CODE_ALREADY_EXISTS",
  "message": "이미 사용 중인 지역 코드입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 `PLATFORM_ADMIN`이어야 한다.
2. `regionCode`는 앞뒤 공백을 제거하고 대문자로 정규화한 뒤 저장한다.
3. `name`과 `reason`은 앞뒤 공백을 제거한 값으로 검증하고 저장 또는 감사 기록에 사용한다.
4. 생성 직후 `isPublic`은 `false`로 고정한다. 공개 또는 운영 상태 변경은 이 API에서 처리하지 않는다.
5. 지역 생성과 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 처리한다.
6. 성공 감사 이벤트는 `target_type = REGION`, 생성된 `region_id`, `previous_state = null`, `next_state = CREATED`, `result = SUCCESS`, `reason_code = REGION_CREATED`, 처리자 역할, 처리 시각과 `requestId`를 포함한다.
7. 성공 감사 이벤트에는 토큰과 개인정보를 저장하지 않는다. 활성 처리자 연결이 필요하면 `audit_event_actor_link`에만 둔다.
8. `region(region_code)` 유일 제약 충돌은 `409 REGION_CODE_ALREADY_EXISTS`로 변환한다.
9. 같은 `regionCode`에 대한 동시 생성 요청은 하나만 성공하고 나머지는 `409 REGION_CODE_ALREADY_EXISTS`를 반환한다.
10. 입력·인증·인가 단계에서 끝난 실패 요청은 실패 감사 이벤트를 만들지 않고 `requestId`, 결과 코드와 필요한 비개인 식별자만 구조화 로그로 남긴다.
