# 전체 지역 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02` |
| 소유 도메인 | 지역 |
| 기준 문서 | [지역 API](region.md), [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [P0 ERD](../../../erd.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

전체관리자가 공개 여부와 관리자 배정 현황을 포함한 전체 지역 목록을 조회한다. 공개 사용자용 `GET /regions`와 달리
비공개 지역도 반환하며 지역과 감사 이력을 생성·수정·삭제하지 않는다.

P1은 별도 지역 운영 상태를 만들지 않는다. 응답의 `isPublic = false`는 비공개·준비,
`isPublic = true`는 공개·운영을 뜻한다.

## 2. 공통 계약 참조

조회·인증·응답·오류의 공통 규칙은 [지역 API 명세서](region.md#2-공통-계약-참조)를 따른다.

## 3. 전체 지역 조회

### Request

```http
GET /platform-admin/regions
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/platform-admin/regions
```

#### Request Example

```http
GET /api/v1/platform-admin/regions?isPublic=false HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `isPublic` | Boolean | N | 공개 여부 필터. `true` 또는 `false`를 허용한다. 생략하면 공개·비공개를 모두 반환한다. |

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
  "message": "전체 지역 조회에 성공했습니다.",
  "data": {
    "regions": [
      {
        "regionId": "1",
        "regionCode": "GIMHAE",
        "name": "김해시",
        "isPublic": true,
        "regionAdminCount": 2,
        "createdAt": "2026-07-20T00:00:00Z",
        "updatedAt": "2026-08-05T04:30:00Z"
      },
      {
        "regionId": "3",
        "regionCode": "JEONJU",
        "name": "전주시",
        "isPublic": false,
        "regionAdminCount": 0,
        "createdAt": "2026-08-05T04:30:00Z",
        "updatedAt": "2026-08-05T04:30:00Z"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 전체 지역 조회 성공 메시지 |
| `data.regions` | Array | 지역 목록. 결과가 없으면 빈 배열 `[]` |
| `data.regions[].regionId` | String | 지역 식별자 |
| `data.regions[].regionCode` | String | 시스템에서 사용하는 대문자 정규형 지역 코드 |
| `data.regions[].name` | String | 사용자에게 표시할 지역명 |
| `data.regions[].isPublic` | Boolean | 공개 여부 |
| `data.regions[].regionAdminCount` | Number | 해당 지역에 배정된 활성 지역 관리자 수. `user_role_assignment.role = REGION_ADMIN`, 배정 `status = ACTIVE`, 연결된 일반 계정 `status = ACTIVE`인 행의 수 |
| `data.regions[].createdAt` | String | 지역 생성 시각. UTC ISO 8601 일시 |
| `data.regions[].updatedAt` | String | 지역 최종 수정 시각. UTC ISO 8601 일시 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `isPublic`을 Boolean으로 변환할 수 없다. 조회 대상과 상태를 변경하지 않으며 값을 `true` 또는 `false`로 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 Access Token을 얻은 뒤 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 `PRIVILEGED` 계정의 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 갖지 않는다. 조회 대상과 상태를 변경하지 않으며 활성 고권한 배정을 얻기 전에는 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 조회 중 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시 장애가 해소된 뒤 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `app_user.account_kind = PRIVILEGED`이고 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다.
2. 공개·비공개 지역을 모두 조회 대상으로 삼는다. `isPublic` 필터가 있으면 해당 공개 여부와 일치하는 지역만 반환한다.
3. 공개 지역이 없거나 필터 결과가 없으면 `404`가 아닌 `200 OK`와 `data.regions = []`를 반환한다.
4. 정렬은 `region.name` 오름차순, 같은 이름이면 `region_id` 오름차순으로 고정한다.
5. `regionAdminCount`는 `user_role_assignment.region_id = region.region_id`, `role = REGION_ADMIN`, 배정 `status = ACTIVE`, 연결된 `app_user.account_kind = ORDINARY`, `app_user.status = ACTIVE`를 모두 만족하는 행만 집계한다. `REVOKED` 이력과 탈퇴로 사용자 연결이 제거된 행은 제외한다.
6. P1 초기 계약에서는 페이지네이션, 검색, 사용자 지정 정렬을 제공하지 않는다.
7. 조회 시 지역, 역할과 감사 이력을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 결과 건수와 결과 코드만 구조화 로그로 남긴다.
- 지역 관리자 또는 사용자 개인정보를 응답과 로그에 포함하지 않는다.
