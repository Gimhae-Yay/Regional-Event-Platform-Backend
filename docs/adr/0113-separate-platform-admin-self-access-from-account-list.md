# ADR-0113: 전체관리자 본인 권한 조회를 계정 목록과 분리한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-21
- 결정일: 2026-08-21
- 관련 요구사항: [P1-FR-09 전체관리자와 지역·역할 관리](../p1/platform-admin.md#p1-fr-09-전체관리자와-지역역할-관리), [ADM-01](../p1/platform-admin.md#3-전체관리자-정책), [공통 인증·인가 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: 없음

## 맥락

전체관리자 Frontend는 로그인 직후와 새로고침 뒤 세션을 복구할 때 현재 사용자의 고권한 등급을 알아야 한다. 현재
Frontend는 `GET /api/v1/platform-admin/admin-accounts`로 모든 연결된 고권한 계정을 조회한 뒤 로그인 응답의
`userId`와 같은 항목을 찾아 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 등급을 판정한다.

전체관리자 계정 목록은 로그인 사용자의 본인 권한 확인보다 넓은 데이터다. 응답에는 다른 고권한 계정의 로그인 식별자,
표시 이름, 등급과 활성·비활성 배정 상태가 포함된다. 계정 생성·비활성화와 Frontend의 목록 화면은 `SUPER_ADMIN`
전용이지만, 현재 목록 조회 계약은 `ROLE_SUPER_ADMIN`과 `ROLE_PLATFORM_ADMIN`을 모두 허용한다. 이 구조는
`PLATFORM_ADMIN`의 정상 로그인과 세션 복원에 다른 고권한 계정 전체의 노출을 요구한다.

공통 `GET /api/v1/me`는 `ORDINARY` 계정의 일반 역할·담당 지역 연결을 반환하며, 고권한 계정은
`platform_admin_assignment`에 별도로 저장한다. 로그인 응답은 역할 표시 정보를 제공하지만 새로고침 뒤 사용하는
Stateless Refresh Token 재발급 응답은 새 Access Token만 반환하므로, 로그인 응답만으로는 세션을 일관되게 복원할 수 없다.

## 결정 동인과 불변 조건

- 로그인과 세션 복원에는 인증 주체 본인의 식별자와 유효 Access Token에 적용된 고권한 등급만 사용한다.
- `PLATFORM_ADMIN`의 정상적인 본인 권한 확인을 위해 다른 고권한 계정의 개인정보·등급·상태를 노출하지 않는다.
- 고권한 계정 목록 조회, 생성과 비활성화는 `ROLE_SUPER_ADMIN`만 허용한다.
- Access Token의 전역 authority snapshot과 요청 시점 DB의 활성 `PRIVILEGED` 계정 최종 검증 경계는
  [ADR-0108](0108-use-global-authority-snapshot-for-first-stage-rbac.md)을 유지한다.
- 고권한 배정 비활성화 뒤 기존 Access Token의 authority가 최대 15분 유지될 수 있는 현재 정책을 본인 조회 API가
  우회하거나 별도 상태 판정으로 바꾸지 않는다.
- 공통 `GET /api/v1/me`의 일반 역할·담당 지역 응답과 로그인·재발급 응답은 변경하지 않는다.
- 목록과 본인 조회는 계정·배정·감사 이력을 생성·수정·삭제하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 추천안: `GET /api/v1/platform-admin/me`를 추가하고 전체관리자 계정 목록을 `SUPER_ADMIN` 전용으로 제한 | 본인 권한과 타인 계정 목록의 노출 경계를 분리한다. 로그인과 Refresh Token 이후 세션 복원에 같은 계약을 사용할 수 있고 일반 역할 API를 변경하지 않는다. | 신규 API 계약과 단계적 Backend·Frontend 전환이 필요하다. | 낮음. 본인 조회를 유지한 채 목록 권한만 이전 계약으로 되돌릴 수 있다. | 높음 |
| 2 | 공통 `GET /api/v1/me`에 고권한 등급을 추가 | 기존 본인 조회 경로를 재사용한다. | `ORDINARY` 역할 연결과 `PRIVILEGED` 고권한 모델을 한 응답에 섞고 P0 공통 계약의 소비자 범위를 넓힌다. | 중간. 배포된 공통 응답 필드 제거와 다수 클라이언트 회귀 검증이 필요하다. | 낮음 |
| 3 | 로그인 응답의 역할 목록을 Frontend에 저장하고 재사용 | 신규 조회 API가 필요 없다. | 새로고침과 재발급 뒤 현재 등급을 서버에서 복원할 수 없고 저장된 클라이언트 상태가 오래되거나 변조될 수 있다. | 낮음. 저장 로직을 제거하면 된다. | 낮음 |
| 4 | 현재처럼 전체관리자 계정 목록에서 본인을 찾는다 | 추가 계약과 Backend 변경이 없다. | 로그인에 불필요한 다른 관리자 정보를 노출하며 목록 장애가 전체관리자 로그인 전체를 막는다. | 없음. 현행 유지다. | 부적합 |

## 결정

`GET /api/v1/platform-admin/me`를 전체관리자 전용 본인 권한 조회 API로 추가한다. 유효한 Access Token에
`ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN`이 있고, DB 최종 검증에서 인증 주체의
`app_user.status = ACTIVE`와 `account_kind = PRIVILEGED`가 확인된 경우에만 성공한다.

응답은 인증 주체의 `userId`와 Access Token authority snapshot에 해당하는 `grade`만 제공한다. `grade`는
`SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 중 하나다. 다른 고권한 계정 정보, 일반 역할, 담당 지역, Access Token,
Refresh Token과 고권한 배정의 현재 `status`는 반환하지 않는다. 호출 성공 자체를 현재 요청이 전체관리자 역할 보호
경계를 통과했다는 신호로 사용한다.

고권한 배정의 현재 `status`를 응답하지 않는 이유는 ADR-0108이 역할 보호 경로에서 발급 뒤 고권한 배정 상태를 전역 역할
조건으로 다시 판정하지 않고 최대 15분의 authority snapshot 지연을 수용하기 때문이다. 본인 조회만 현재 배정 상태를
재판정하면 Backend의 실제 인가와 Frontend 세션 판정이 달라진다. Refresh Token 재발급은 현재 권한 원천으로 새
Access Token을 발급하므로, 비활성 배정은 다음 재발급부터 전체관리자 authority를 얻지 못한다.

`GET /api/v1/platform-admin/admin-accounts`는 `ROLE_SUPER_ADMIN`만 허용한다. Frontend는 로그인 직후와 세션 복원에서
본인 조회 API를 사용하고, 전체관리자 계정 목록 화면에서만 목록 API를 호출한다. Frontend 라우트 가드는 편의상 화면을
제어하지만 보안 경계는 각 Backend API의 authority와 DB 최종 검증이 책임진다.

## 결과와 트레이드오프

### 기대 효과

- `PLATFORM_ADMIN`이 로그인하거나 세션을 복원할 때 다른 전체관리자 계정 정보를 내려받지 않는다.
- 본인 권한 확인과 계정 목록 장애가 분리되어 목록 API 장애가 전체관리자 콘솔의 모든 로그인을 막지 않는다.
- 공통 일반 역할 API와 로그인·재발급 계약을 변경하지 않고 고권한 계정 분리 모델을 유지한다.
- Frontend의 `SUPER_ADMIN` 전용 목록 화면과 Backend 목록 권한이 일치한다.

### 수용한 단점과 위험

- 전체관리자 콘솔 진입 시 본인 조회 요청이 한 번 필요하다.
- Backend와 Frontend를 잘못된 순서로 배포하면 구 Frontend의 `PLATFORM_ADMIN` 로그인이 목록 `403`으로 실패할 수 있다.
- 본인 조회의 `grade`는 Access Token snapshot이므로 고권한 배정 비활성화 직후 최대 15분 동안 이전 등급을 나타낼 수 있다.
- 목록 조회 권한 축소는 현재 목록 계약을 사용하는 `PLATFORM_ADMIN` 클라이언트에 호환되지 않는다.

## 전환과 롤백

DB schema와 기존 데이터 이관은 필요하지 않다. 다음 순서로 호환 전환한다.

1. 본인 조회와 목록 권한 변경을 각각 확정하는 API 문서를 먼저 배포한다.
2. Backend에 `GET /api/v1/platform-admin/me`를 추가하되, 이 단계에서는 목록의 기존 두 authority 허용을 유지한다.
3. Frontend의 로그인·세션 복원을 본인 조회 API로 전환하고 `SUPER_ADMIN` 목록 화면만 목록 API를 사용하도록 검증한다.
4. 전환된 Frontend가 배포된 뒤 Backend 목록 조회를 `ROLE_SUPER_ADMIN` 전용으로 제한한다.

2단계에서 문제가 생기면 신규 본인 조회 라우팅만 되돌리고 기존 목록 계약을 유지한다. 3단계 뒤 Frontend 문제가 생기면
목록 권한을 축소하기 전에 Frontend를 구 흐름으로 되돌릴 수 있다. 4단계 뒤 롤백이 필요하면 목록의
`ROLE_PLATFORM_ADMIN` 허용을 먼저 임시 복구한 뒤 구 Frontend를 배포한다. 본인 조회 API는 다른 계약과 충돌하지 않으므로
목록 권한 롤백과 함께 제거하지 않는다.

## 검증 방법

- 두 고권한 등급이 본인 조회에서 자신의 `userId`와 Token authority에 맞는 `grade`만 받고, 다른 계정 정보와 현재 배정
  상태를 받지 않는지 검증한다.
- 인증 없음·잘못된 Token은 `401`, 전체관리자 authority가 없는 유효한 Token과 비활성·비고권한 계정은 `403`인지 검증한다.
- `PLATFORM_ADMIN`의 목록 조회는 `403`, `SUPER_ADMIN`의 목록 조회는 기존 정렬·필드·빈 배열 계약을 유지하는지 검증한다.
- 로그인 직후와 Refresh Token을 사용한 새로고침 복원에서 Frontend가 목록 API 없이 등급과 라우트 가드를 복구하는지
  검증한다.
- 전체관리자 목록 화면만 목록 API를 호출하고, 생성·비활성화의 기존 `SUPER_ADMIN` 권한과 회귀하지 않는지 검증한다.
- 본인 조회와 목록 조회가 계정·배정·감사 이력을 변경하지 않고 응답 본문과 개인정보를 로그에 남기지 않는지 검증한다.
- 단계별 배포에서 구 Frontend와 신 Backend, 신 Frontend와 권한 축소 전 Backend 조합이 정상 동작하는지 확인한다.

## 대체 조건

- 고권한 배정 비활성화를 Access Token 수명보다 빠르게 전역 차단하는 정책이 확정되어 본인 조회와 Backend 인가가 현재
  배정 상태를 즉시 반영해야 한다.
- 외부 IdP나 서버 세션이 고권한 세션의 등급·상태와 강제 종료를 직접 제공하게 된다.
- 전체관리자 계정 목록을 `PLATFORM_ADMIN`에게도 제공해야 하는 별도 최소 권한 업무 요구와 노출 필드 계약이 확정된다.
