# 콘텐츠 수정본 심사 대기 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [인증·프로필](../../../p0/auth-profile.md), [API 공통 계약](../../common/README.md), 선행 계약 결정 PR |

## 1. 개요

담당 지역 관리자가 심사를 기다리는 콘텐츠 수정본 목록을 조회한다. 심사 대상은 생성 즉시 완전 후보로 동결된
`EDIT_REQUESTED` 수정본만이다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-10`, `CON-05` | `GET /region-admin/content-revisions?status=EDIT_REQUESTED` | `content_revision`, `content`, `user_role_assignment` |
| `AUTH-01` | `GET /region-admin/content-revisions?status=EDIT_REQUESTED` | `content.region_id`, `user_role_assignment.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-revisions`; 시각은 ISO 8601 `+09:00` 오프셋 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN`과 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`의 단순 목록과 API별 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 페이지네이션을 적용하지 않는 P0 단순 목록 |

## 3. 콘텐츠 수정본 심사 대기 목록 조회

### Request

```http
GET /region-admin/content-revisions?status=EDIT_REQUESTED
```

#### Request Example

```http
GET /api/v1/region-admin/content-revisions?status=EDIT_REQUESTED HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 상태의 `REGION_ADMIN`이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- |
| `status` | String | Y | 항상 `EDIT_REQUESTED`여야 한다. |

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
  "message": "담당 지역 콘텐츠 수정본 심사 대기 목록 조회에 성공했습니다.",
  "data": {
    "contentRevisions": [
      {
        "contentRevisionId": 201,
        "contentId": 101,
        "candidateTitle": "김해 가야문화 체험 여름 프로그램",
        "status": "EDIT_REQUESTED",
        "submittedAt": "2026-07-30T09:00:00+09:00",
        "editorId": 20
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
| `message` | String | 공개 성공 메시지 |
| `data.contentRevisions` | Array | 담당 지역의 심사 대기 수정본 목록. 결과가 없으면 빈 배열 `[]` |
| `data.contentRevisions[].contentRevisionId` | Long | 수정본 식별자 |
| `data.contentRevisions[].contentId` | Long | 원본 콘텐츠 식별자 |
| `data.contentRevisions[].candidateTitle` | String | 생성 시 동결된 수정 후보의 제목 |
| `data.contentRevisions[].status` | String | 항상 `EDIT_REQUESTED` |
| `data.contentRevisions[].submittedAt` | String | 생성과 동시에 심사 요청된 수정본의 동결 시각 |
| `data.contentRevisions[].editorId` | Long | 수정본을 생성한 소유 운영자 식별자 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 누락됐거나 `EDIT_REQUESTED`가 아니다. 수정본과 콘텐츠 상태는 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 수정본과 콘텐츠 상태는 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 담당 지역이 없다. 수정본과 콘텐츠 상태는 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 수정본·원본 콘텐츠·편집자·지역 연결의 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 수정본과 콘텐츠 상태는 변경하지 않으며 일시적 장애라면 같은 요청으로 재시도할 수 있다. |

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

1. 인증 주체는 활성 상태이며 담당 `region_id`가 연결된 `REGION_ADMIN`이어야 한다.
2. 서버는 클라이언트가 지역을 지정하지 못하게 하고 인증 주체의 역할 배정에서 담당 지역을 결정한다.
3. `content.region_id = 인증 지역 관리자의 담당 region_id`이고 수정본 `status = EDIT_REQUESTED`인 경우만 반환한다.
4. `EDIT_REQUESTED`가 아닌 수정본은 반환하지 않는다.
5. 목록은 `submittedAt` 오름차순, 같은 시각이면 `contentRevisionId` 오름차순으로 고정 정렬한다.
6. 심사 대기 수정본이 없으면 `404`가 아닌 `200 OK`와 `data.contentRevisions = []`을 반환한다.
7. P0에서는 페이지네이션, 추가 상태 필터, 검색과 사용자 지정 정렬을 제공하지 않는다.
8. 조회는 수정본, 콘텐츠, 이미지 객체와 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 수정본 상태 전이와 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 담당 지역 식별자, 결과 건수와 결과 코드만 구조화 로그에 남긴다.
- 다른 지역의 수정본 식별자, 후보 필드와 이미지 정보는 응답·구조화 로그에 포함하지 않는다.
