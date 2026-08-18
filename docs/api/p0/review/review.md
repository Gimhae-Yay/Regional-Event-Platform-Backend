# 인증 후기 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-08](../../../p0/review.md#fr-08-인증-후기), `REV-01`~`REV-04`, `PRV-02` |
| 소유 도메인 | 인증 후기 |
| 기준 문서 | [인증 후기](../../../p0/review.md), [ADR-0012](../../../adr/0012-retain-author-unlinked-reviews-and-visits-after-withdrawal.md), [ADR-0034](../../../adr/0034-use-page-number-pagination-for-review-list.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 체크인 완료 방문 기록에 근거한 후기의 작성, 공개 목록 조회, 수정과 삭제 HTTP 계약을 정의한다.
후기는 방문당 한 건만 작성할 수 있고 `PUBLISHED → DELETED` 외 상태 전이는 허용하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-08, REV-01 | `POST /api/v1/visits/{visitId}/reviews` | `visit`, `review` |
| FR-08, REV-02 | `GET /api/v1/contents/{contentId}/reviews` | `content`, `review` |
| FR-08, REV-03 | `PATCH /api/v1/reviews/{reviewId}` | `review` |
| FR-08, REV-04 | `DELETE /api/v1/reviews/{reviewId}` | `review` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 JSON 요청은 `application/json; charset=UTF-8`을 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 작성·수정·삭제는 활성 작성자 Access Token이 필요하며 공개 목록은 인증 없이 조회한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 공통 최상위 필드와 기존 공개 오류 코드만 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록은 페이지 번호 기반이며 최신 작성순으로 정렬한다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 체크인 방문당 후기 작성 | `POST /api/v1/visits/{visitId}/reviews` | [create-visit-review.md](create-visit-review.md) |
| 인증 후기 목록 조회 | `GET /api/v1/contents/{contentId}/reviews` | [get-content-reviews.md](get-content-reviews.md) |
| 후기 수정 | `PATCH /api/v1/reviews/{reviewId}` | [update-review.md](update-review.md) |
| 작성자 후기 삭제 | `DELETE /api/v1/reviews/{reviewId}` | [delete-review.md](delete-review.md) |
