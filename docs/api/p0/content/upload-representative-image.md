# 대표 이미지 S3 업로드 URL 발급 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `FR-14`, `AUTH-01`, `CON-02`, `CON-05` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0016](../../../adr/0016-use-private-s3-presigned-urls-and-immediate-image-deletion.md), [ADR-0030](../../../adr/0030-store-representative-image-references-on-content-roots.md), [ADR-0041](../../../adr/0041-bind-presigned-image-upload-to-operator-and-region.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 운영자가 콘텐츠 생성, 콘텐츠 수정본 생성 또는 대표 이미지 교체 전에 서버가 만든 S3 객체 키로
대표 이미지를 업로드할 수 있도록 짧은 유효기간의 presigned PUT URL을 발급한다. 서버는 요청 메타데이터를 검증한 뒤
`image_object`를 만들고, 이후 콘텐츠·수정본 API는 반환된 `imageObjectId`만 받아 대표 이미지로 연결한다.
콘텐츠 생성 전에는 아직 소유 콘텐츠 식별자가 없으므로 이 API는 승인된 운영자의 담당 지역과 업로드 용도만 검증한다.
기존 콘텐츠 또는 수정본에 실제로 연결할 수 있는지는 콘텐츠 생성·수정본 생성·수정 API가 소유 관계와 상태를 다시 검증한다.
S3 연동은 S3 인프라 어댑터를 통해 수행한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03` | `POST /operator/uploads/presigned-url` | `image_object`, `user_role_assignment` |
| `FR-14` | `POST /operator/uploads/presigned-url` | `image_object`, `user_role_assignment` |
| `AUTH-01` | `POST /operator/uploads/presigned-url` | 운영자 역할, 담당 지역 |
| `CON-02`, `CON-05` | `POST /operator/uploads/presigned-url` | 대표 이미지 객체, S3 객체 키, checksum |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/uploads/presigned-url`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할과 담당 지역이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 업로드 대상 이미지 객체 식별자, presigned PUT 정보를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 대표 이미지 S3 업로드 URL 발급

### Request

```http
POST /api/v1/operator/uploads/presigned-url
```

#### Request Example

```http
POST /api/v1/operator/uploads/presigned-url HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "mediaType": "image/webp",
  "byteSize": 524288,
  "checksum": "m3vD5u5z9Q4p7nZf3s1q5u9w2x8a7b6c5d4e3f2g1h0=",
  "usage": "CONTENT_REPRESENTATIVE"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

요청 예시의 JSON 객체를 사용한다.

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `mediaType` | String | Y | 대표 이미지 MIME 타입. P0에서는 `image/jpeg`, `image/png`, `image/webp`만 허용한다. |
| `byteSize` | Integer | Y | 업로드할 파일의 바이트 크기. `1` 이상 `5242880` 이하만 허용하며, 실제 연결 직전 S3 `HEAD`의 `ContentLength`와 같아야 한다. |
| `checksum` | String | Y | 클라이언트가 계산한 SHA-256 표준 Base64 체크섬. 디코딩 결과가 32바이트인 44자 Base64 문자열만 허용하며 S3 업로드 헤더와 `image_object.checksum`에 같은 값으로 사용한다. |
| `usage` | String | Y | 업로드 용도. P0에서는 `CONTENT_REPRESENTATIVE`만 허용한다. |

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
  "message": "대표 이미지 업로드 URL 발급에 성공했습니다.",
  "data": {
    "imageObjectId": "301",
    "uploadUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/2026/07/30/01J4X8M2W3A4B5C6D7E8F9G0H1.webp?X-Amz-Signature=...",
    "expiresAt": "2026-07-30T05:10:00Z",
    "uploadHeaders": {
      "Content-Type": "image/webp",
      "Content-Length": "524288",
      "x-amz-checksum-sha256": "m3vD5u5z9Q4p7nZf3s1q5u9w2x8a7b6c5d4e3f2g1h0="
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.imageObjectId` | String | 양의 10진 문자열인 생성된 이미지 객체 식별자. 콘텐츠 생성·수정본 생성·수정 API의 `representativeImageObjectId`로 사용한다. |
| `data.uploadUrl` | String | 비공개 S3 객체에 파일을 업로드하기 위한 단기 presigned PUT URL |
| `data.expiresAt` | String | 업로드 URL 만료 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.uploadHeaders` | Object | S3 PUT 요청에 반드시 포함해야 하는 헤더 모음 |
| `data.uploadHeaders.Content-Type` | String | 요청한 `mediaType`과 같은 값 |
| `data.uploadHeaders.Content-Length` | String | 요청한 `byteSize`를 10진 문자열로 표현한 값 |
| `data.uploadHeaders.x-amz-checksum-sha256` | String | 요청한 `checksum`과 같은 값 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `mediaType`, `byteSize`, `checksum`, `usage`가 허용 범위가 아니거나 `checksum`이 SHA-256 Base64 형식이 아니다. 이미지 객체와 업로드 URL을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 이미지 객체와 업로드 URL을 생성하지 않는다. |
| `400` | `INVALID_TYPE` | `byteSize`를 선언된 타입으로 변환할 수 없다. 이미지 객체와 업로드 URL을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 이미지 객체와 업로드 URL을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자 역할 또는 담당 지역이 없다. 이미지 객체와 업로드 URL을 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | S3 presigned URL 발급 실패 또는 예상하지 못한 서버 오류가 발생했다. 이미지 객체와 업로드 URL을 생성하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증 주체가 `ACTIVE` 상태이고 `OPERATOR` 역할과 담당 `region_id`를 가진 회원인지 확인한다. 요청에서 지역, 운영자, 콘텐츠, 수정본, S3 객체 키 또는 파일명을 지정할 수 없다.
2. 서버는 `mediaType`, `byteSize`, `checksum`, `usage`를 검증한 뒤 예측 불가능한 새 S3 객체 키를 생성한다. 객체 키에는 사용자 식별자, 원본 파일명, 개인정보를 포함하지 않으며 응답에도 노출하지 않는다.
3. 서버는 같은 트랜잭션에서 `image_object`를 `ACTIVE` 상태로 생성한다. `object_key`, `media_type`, `byte_size`, `checksum`, `created_by_user_id`, `region_id`, `upload_expires_at`, `linked_at = NULL`, `lifecycle_status = ACTIVE`, `delete_attempt_count = 0`을 저장한다.
4. 서버는 S3 인프라 어댑터를 통해 생성한 객체 키, 요청한 MIME 타입, 요청한 바이트 크기와 SHA-256 Base64 체크섬을 조건으로 S3 presigned PUT URL을 발급한다. 클라이언트는 응답의 `uploadHeaders`를 그대로 포함해 만료 전 업로드해야 한다.
5. 이 API는 콘텐츠나 수정본을 생성·수정하지 않으며 `content`, `content_revision`, `content_log`, `audit_event`를 변경하지 않는다. 실제 대표 이미지 연결은 콘텐츠 생성·수정본 생성·수정 API가 `imageObjectId`를 다시 검증한 뒤 수행한다.
6. 콘텐츠 생성·수정본 생성·수정 API는 연결 직전에 `image_object` 행을 잠그고 다음 조건을 모두 검증한다. `lifecycle_status = ACTIVE`, `created_by_user_id`가 현재 운영자, `region_id`가 운영자 담당 지역과 대상 콘텐츠 지역, `linked_at IS NULL`, `upload_expires_at > now`여야 한다. 또한 S3 인프라 어댑터가 조회한 S3 `HEAD` 결과의 SHA-256 Base64 체크섬과 `ContentLength`가 각각 `image_object.checksum`, `image_object.byte_size`와 같아야 한다. 하나라도 맞지 않으면 연결하지 않는다.
7. 콘텐츠 생성·수정본 생성·수정 API가 대표 이미지 연결을 성공시키면 같은 트랜잭션에서 `image_object.linked_at`을 현재 시각으로 설정하고 `created_by_user_id`를 `NULL`로 제거한다. 이후 기존 대표 이미지를 수정본 스냅샷으로 공유하는 것은 ADR-0030의 직접 FK 참조 규칙을 따른다.
8. 업로드 URL 발급 후 `upload_expires_at`까지 연결되지 않은 `ACTIVE` 이미지 객체는 보관 작업이 `linked_at IS NULL`과 직접 FK 참조 0건을 확인한 뒤 `DELETE_PENDING`으로 전환하고 S3 인프라 어댑터를 통해 S3 삭제를 시도한다. 삭제 실패 시 기존 `delete_attempt_count`, `last_delete_attempted_at`으로 멱등 재시도한다. 이 정리 흐름은 별도 클라이언트 API를 제공하지 않는다.

### 보안·로깅

- 응답의 `uploadUrl`은 짧은 유효기간을 가지며, 서버는 URL 자체를 DB나 Redis에 저장하지 않는다.
- 구조화 로그에는 `imageObjectId`, 요청 결과와 오류 코드처럼 비개인 식별만 남기고 `uploadUrl`, 객체 키, checksum, 토큰, 개인정보를 남기지 않는다.
- `created_by_user_id`는 업로드 객체의 최초 연결 권한 검증에만 사용하고 연결 성공 시 제거한다. `region_id`는 지역 경계 검증에 사용하며, 두 값 모두 응답과 로그에 노출하지 않는다.
- S3 버킷과 객체는 공개 쓰기·공개 읽기를 허용하지 않는다. 서버가 발급한 presigned PUT URL 외 업로드 경로는 허용하지 않는다.
