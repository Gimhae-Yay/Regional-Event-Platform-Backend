# 인증 후기 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-08`, `FR-11`, `PRV-02`, `REV-01`, `REV-02`, `REV-03`, `REV-04` |
| 소유 도메인 | 인증 후기 |
| 기준 문서 | [인증 후기](../../../p0/review.md), [예약 QR·체크인](../../../p0/check-in.md), [인증·프로필](../../../p0/auth-profile.md), [ADR-0012](../../../adr/0012-retain-author-unlinked-reviews-and-visits-after-withdrawal.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 체크인 완료 방문에 근거한 인증 후기의 작성, 공개 목록 조회, 수정, 삭제와 삭제 원문 파기 계약을 구체화한다.
요청·응답의 공통 형식, 인증과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 후기 도메인에만 적용되는 값과 규칙을 작성한다.

### 요구사항 추적

| 요구사항 | 실행 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-08`, `REV-01` | `POST /visits/{visitId}/reviews` | `visit`, `review` |
| `FR-08`, `REV-02`, `PRV-02` | `GET /contents/{contentId}/reviews` | `content`, `review` |
| `FR-08`, `REV-03` | `PUT /reviews/{reviewId}` | `review.created_at`, `review.updated_at` |
| `FR-08`, `REV-04` | `DELETE /reviews/{reviewId}` | `review.status`, `review.deleted_at` |
| `FR-08`, `REV-04` | `scheduler` | `review.rating`, `review.review_text`, `review.deleted_at` |
| `FR-11` | 후기 작성·수정·삭제와 원문 파기 | `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1` |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 작성·수정·삭제는 활성 회원, 공개 목록은 인증 제외 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태, `data` 필드와 후기 상태 충돌 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 공개 후기 목록은 P0에서 단순 목록으로 제공 |

### 후기 입력·작성자 표시 계약

- `rating`은 `1` 이상 `5` 이하의 필수 정수다.
- `reviewText`는 `null` 또는 공백만으로 구성할 수 없고 `1`자 이상 `1000`자 이하인 필수 문자열이다.
- 공개 후기에서 작성자 연결이 유지되면 `인증 방문자`, 탈퇴로 연결이 제거되면 `탈퇴한 사용자`로 표시한다.
- 공개 응답에는 사용자 식별자, 이름과 연락처를 포함하지 않는다.

## 기능별 API 명세

| 기능 | 실행 경로 | 명세 |
| --- | --- | --- |
| 체크인 방문당 후기 1건 작성 | `POST /visits/{visitId}/reviews` | [create-review.md](create-review.md) |
| 인증 후기 목록 조회 | `GET /contents/{contentId}/reviews` | [list-content-reviews.md](list-content-reviews.md) |
| 후기 수정 | `PUT /reviews/{reviewId}` | [update-review.md](update-review.md) |
| 작성자 후기 삭제 | `DELETE /reviews/{reviewId}` | [delete-review.md](delete-review.md) |
| 후기 삭제 30일 뒤 원문 영구 파기 | `scheduler` | [purge-deleted-review-source.md](purge-deleted-review-source.md) |
