# 대표 이미지 S3 업로드 URL 발급 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `FR-14`, `AUTH-01`, `CON-02`, `CON-05` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 운영자가 대표 이미지를 비공개 S3에 직접 업로드할 수 있는 짧은 유효기간의 presigned URL을 발급한다.
이 API는 콘텐츠나 수정본 식별자를 받지 않고, 인증된 운영자가 소유하는 `TEMPORARY` 이미지 객체를 만든다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03` | `POST /operator/uploads/presigned-url` | `image_object` |
| `FR-14` | `POST /operator/uploads/presigned-url` | `image_object` |
| `AUTH-01` | `POST /operator/uploads/presigned-url` | 운영자 역할, 임시 이미지 객체 소유자 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/uploads/presigned-url`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 임시 이미지 객체·업로드 URL 만료 정보를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 URL 발급이므로 적용하지 않는다. |

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
  "mediaType": "image/jpeg",
  "byteSize": 245761,
  "checksum": "uU0nuZNNPgilLlLX2n2r+sSE7+N6U4DukIj3rOLvzek="
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

```json
{
  "mediaType": "image/jpeg",
  "byteSize": 245761,
  "checksum": "uU0nuZNNPgilLlLX2n2r+sSE7+N6U4DukIj3rOLvzek="
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `mediaType` | String | Y | 업로드할 대표 이미지의 이미지 MIME 타입 |
| `byteSize` | Long | Y | 양수인 업로드 파일 크기(바이트) |
| `checksum` | String | Y | SHA-256 다이제스트를 Base64로 인코딩한 값 |

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
    "uploadUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/...",
    "uploadUrlExpiresAt": "2026-07-30T05:20:00Z",
    "temporaryObjectExpiresAt": "2026-07-30T06:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.imageObjectId` | String | 양의 10진 문자열인 서버가 생성하고 인증된 운영자에게 소유시킨 `TEMPORARY` 이미지 객체 식별자 |
| `data.uploadUrl` | String | 비공개 S3 객체에 직접 업로드하는 presigned URL |
| `data.uploadUrlExpiresAt` | String | `uploadUrl`의 만료 시각 |
| `data.temporaryObjectExpiresAt` | String | 연결되지 않은 임시 이미지 객체의 만료 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 이미지 메타데이터가 유효하지 않다. 임시 이미지 객체를 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 임시 이미지 객체를 생성하지 않는다. |
| `400` | `INVALID_TYPE` | `byteSize`를 정수로 변환할 수 없다. 임시 이미지 객체를 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 임시 이미지 객체를 생성하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자 역할 또는 담당 지역이 없다. 임시 이미지 객체를 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 임시 이미지 객체를 생성하지 않는다. |

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

1. 서버는 인증된 승인 운영자의 소유로 `TEMPORARY` 이미지 객체와 서버 생성 객체 키를 만들고, 미디어 타입·바이트 크기·SHA-256 Base64 체크섬을 저장한다. 콘텐츠·수정본 식별자는 요청받거나 연결하지 않는다.
2. 클라이언트는 `uploadUrl`로 비공개 S3에 직접 `PUT` 업로드하며, presigned URL 서명에 포함된 필수 `x-amz-checksum-sha256` 헤더에 요청의 `checksum` 값을 보낸다. 이 헤더가 없거나 값이 다르면 업로드를 거부한다. 이 API는 파일 바이트를 받지 않고 업로드 완료를 보장하지 않는다.
3. 클라이언트는 임시 객체 만료 전에 콘텐츠 생성·콘텐츠 보완·수정본 생성·수정본 보완 요청에서 `imageObjectId`를 지정할 수 있다. 수정본 재요청은 이미지 객체를 받지 않으며, 이미지 변경이 없는 경우 새 임시 객체 발급·검증을 요구하지 않는다. 연결 전에 서버는 S3 `HEAD` 결과의 SHA-256 체크섬이 저장한 같은 Base64 값과 일치하는지, 업로드 검증과 소유자를 확인하고 임시 만료를 적용하지 않는다.
4. 객체 키, 원본 파일명, 공개 URL과 사용자 식별 정보는 응답·영속 이미지 메타데이터에 노출하지 않는다.
5. 호출마다 새 임시 이미지 객체와 URL을 발급한다. 멱등 키 계약은 정의하지 않는다.
