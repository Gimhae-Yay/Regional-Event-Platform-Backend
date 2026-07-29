# 지역·콘텐츠 카탈로그 콘텐츠 회차 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-02`, `FR-03`, `FR-06`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-06` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 공개 콘텐츠의 회차 조회와 승인된 운영자가 소유 콘텐츠의 회차를 생성·수정·취소하는 HTTP API 계약을 관리한다.
요청·응답 공통 형식과 인증 전달 방식은 `common/` 문서를 단일 출처로 삼으며, 각 API 명세에는 해당 API의
입력·상태 전이·응답·오류만 작성한다.

공개 조회는 `PUBLISHED` 콘텐츠의 `SCHEDULED` 회차만 노출한다. 회차 생성과 수정은 승인 요청 전인 `PENDING` 콘텐츠의
`SCHEDULED` 회차에만 허용한다. 소유 운영자의 회차 취소는 `SCHEDULED → CANCELLED` 전이와 활성 홀드·미체크인 확정 예약의
종결을 하나의 트랜잭션으로 처리한다. 운영자 API는 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계를 함께 검증한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-02, SES-01, SES-02 | `GET /api/v1/contents/{contentId}/sessions` | `content`, `content_session` |
| FR-03, AUTH-01, SES-01 | `POST /api/v1/operator/contents/{contentId}/sessions` | `content`, `content_session`, `user_role_assignment` |
| FR-03, AUTH-01, SES-01 | `PUT /api/v1/operator/contents/{contentId}/sessions/{sessionId}` | `content`, `content_session`, `user_role_assignment` |
| FR-06, AUTH-01, RSV-06 | `POST /api/v1/operator/sessions/{sessionId}/cancel` | `content`, `content_session`, `capacity_hold`, `reservation` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드를 명시한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 공개 콘텐츠 회차 목록 조회 | `GET /api/v1/contents/{contentId}/sessions` | [list-public-content-sessions.md](list-public-content-sessions.md) |
| 내 콘텐츠 회차 생성 | `POST /api/v1/operator/contents/{contentId}/sessions` | [session-create.md](session-create.md) |
| 내 콘텐츠 회차 수정 | `PUT /api/v1/operator/contents/{contentId}/sessions/{sessionId}` | [session-update.md](session-update.md) |
| 소유 운영자의 회차 취소 | `POST /api/v1/operator/sessions/{sessionId}/cancel` | [session-cancel.md](session-cancel.md) |
