# 페이지네이션

페이지 기반 목록 API는 `ApiResponse<PageResponse<응답 DTO>>`로 응답한다. 페이지 번호와 크기, 응답 메타데이터는
이 문서의 공통 계약을 모든 페이지 기반 API에 일관되게 적용하고, 정렬과 필터는 각 도메인 API 명세에서 확정한다.

## 응답 적용 범위

| 목록 유형 | 계약 |
| --- | --- |
| 페이지 기반 목록 | `ApiResponse<PageResponse<응답 DTO>>` |
| 단순 목록 | `PageResponse`를 강제하지 않음 |
| 커서·Slice 기반 목록 | `PageResponse`를 강제하지 않으며, 해당 API에 별도 계약을 작성 |

`PageResponse<T>`의 `T`에는 명시적으로 매핑한 응답 DTO만 담는다. Spring Data의 `Page`, `Slice`, `Pageable`,
JPA 엔티티와 영속성 Projection을 JSON으로 직접 노출하지 않는다.

## 페이지 응답 구조

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "콘텐츠 목록 조회에 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

| 필드 | 타입 | 계약 |
| --- | --- | --- |
| `content` | Array | 현재 페이지의 결과다. 결과가 없으면 `null` 대신 빈 배열을 사용한다. |
| `page` | Integer | 요청한 0부터 시작하는 현재 페이지 번호다. |
| `size` | Integer | 요청에 적용된 페이지 크기다. |
| `totalElements` | Long | 필터를 적용한 전체 결과 수다. |
| `totalPages` | Integer | 전체 페이지 수다. 빈 결과이면 `0`이다. |

`content`, `totalElements`, `totalPages`는 반드시 같은 필터 조건의 조회 결과로 생성해 서로 일치시킨다.

## 요청·정렬 규칙

| 항목 | 계약 |
| --- | --- |
| 페이지 번호 | `page`는 0부터 시작한다. 생략하면 `0`이며 음수는 `400 INVALID_INPUT`으로 거부한다. |
| 페이지 크기 | `size`를 생략하면 `20`이다. `1~100`만 허용하며 범위를 벗어나면 `400 INVALID_INPUT`으로 거부한다. |
| 타입 오류 | `page` 또는 `size`를 정수로 변환할 수 없으면 `400 INVALID_TYPE`으로 거부한다. |
| 정렬 | 사용자 지정 정렬 제공 여부와 고정 정렬 기준을 각 도메인 API 명세에서 정의한다. 동률을 해소하는 유일 식별자 정렬을 포함한다. |
| 필터 | 지원하는 필터명·자료형·조합 규칙을 각 도메인 API 명세에서 정의한다. |
| 빈 결과 | `200 OK`, `content: []`, `totalElements: 0`, `totalPages: 0`을 반환한다. |

## 구현 경계

- `PageResponse<T>` 변환은 Controller 또는 전용 API Mapper에서 중앙화한다.
- Service와 도메인 계층은 `global.response.PageResponse`에 의존하지 않는다.
- API별 페이지네이션 선택, 허용 정렬과 필터는 도메인 명세에 작성하되, 공통 규칙을 벗어나는 경우에는 사유와
  별도 계약을 명시한다.
