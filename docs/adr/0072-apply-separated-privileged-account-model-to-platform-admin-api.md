# ADR-0072: 전체관리자 API에 고권한 계정 분리 모델을 적용한다

- 상태: 채택됨
- 기록 유형: 고급
- 기록일: 2026-08-06
- 결정일: 2026-08-06
- 관련 요구사항: [P1-FR-09](../p1-spec.md#6-기능-요구사항과-소유-문서), [ADM-01](../p1/platform-admin.md#3-전체관리자-정책)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 ADR: [ADR-0064](0064-separate-privileged-account-class-from-ordinary-roles.md)

## 맥락

정책 반영 초안인 [P1 ERD](../p1-erd.md)는 일반 역할과 전체관리자 권한을 같은 역할 연결에 저장하지 않는다.
일반 역할에 `PLATFORM_ADMIN`을 추가하면 사용자 유형, 역할 이력, 고권한 계정 상태의 경계가 무너진다.

## 결정

- `app_user.account_kind`로 일반 계정과 고권한 계정을 구분한다.
- 전체관리자 권한은 `platform_admin_assignment`에만 저장한다. 등급은 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`, 상태는 `ACTIVE` 또는 `INACTIVE`다.
- `user_role_assignment`에는 `VISITOR`, `OPERATOR`, `REGION_ADMIN`만 저장하고, 전체관리자 사용자 목록도 활성 일반 계정만 반환한다.
- 전체관리자 API는 활성 `platform_admin_assignment`를 인가 기준으로 사용한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·위험 | 선택 |
| --- | --- | --- | --- | --- |
| 1 | 고권한 계정과 일반 역할을 분리한다 | ERD의 계정 유형·상태·이력 제약을 그대로 적용한다 | 별도 배정 조회가 필요하다 | 채택 |
| 2 | `user_role_assignment`에 `PLATFORM_ADMIN`을 추가한다 | 기존 역할 조회를 재사용한다 | P1 ERD 초안과 계정 유형 분리 규칙에 반한다 | 기각 |

## 결과와 검증 방법

- 전체관리자 생성 시 `app_user(account_kind = PRIVILEGED)`와 활성 `platform_admin_assignment`가 함께 생성된다.
- 일반 역할에 `PLATFORM_ADMIN`이 저장되지 않고, 일반 사용자 목록에 고권한 계정이 포함되지 않는지 검증한다.
