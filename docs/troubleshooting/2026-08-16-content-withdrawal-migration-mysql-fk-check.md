# 철회 요청 migration의 MySQL FK·CHECK 충돌

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | V42 migration이 MySQL 8에 적용되지 않아 애플리케이션 배포가 중단됨 |
| 최초 확인 시각·시간대 | 2026-08-16 18:25 KST |
| 관련 요구사항·이슈 | Issue #873, 철회 요청 상태별 필드 제약과 사용자 연결 해제 |
| revision·브랜치 | `83075669`, `feature/873-content-withdrawal-request` |
| 환경·프로필 | Java 21, MySQL 8.0.42 Testcontainers, Gradle test |

## 기대 결과와 실제 결과

### 기대 결과

V42가 철회 요청 테이블의 FK와 상태별 CHECK 제약을 함께 생성한다.

### 실제 결과

MySQL 오류 3823으로 `CREATE TABLE content_withdrawal_request`가 실패했다. `reviewed_by_user_id`는
`ON DELETE SET NULL` FK의 참조 동작에 필요하므로 CHECK 제약에서 사용할 수 없다는 오류였다.

## 재현 절차

### 선행 조건

- Docker 실행 가능
- MySQL 8.0.42 Testcontainers 사용

### 명령·요청·입력

1. `./gradlew test --tests '*ContentWithdrawalRequestMigrationMySqlTest'`
2. 전체 Flyway migration을 빈 MySQL 스키마에 적용한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle test 실패

## 수집한 증거

- SQL State: `HY000`
- MySQL 오류 코드: `3823`
- 실패 제약: `ck_content_withdrawal_request_pending_fields`
- 충돌 컬럼: `reviewed_by_user_id`

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-16 18:25 KST | 관찰 | V42를 실제 MySQL에서 실행 | 방언 또는 제약 호환 문제가 있으면 Flyway가 실패 | 오류 3823으로 재현 | 채택 |
| 2026-08-16 18:27 KST | 검증 | `ON DELETE SET NULL` FK 컬럼과 CHECK 참조가 충돌 | 오류에 FK 참조 동작과 CHECK 이름이 함께 표시 | MySQL 오류 메시지로 연결 확인 | 채택 |
| 2026-08-16 18:30 KST | 검증 | 자동 참조 동작 제거 후 원래 테스트 재실행 | Flyway가 V42를 적용 | migration 통과, 테스트 데이터 준비에서 별도 연결 설정 오류 확인 | 채택 |
| 2026-08-16 18:33 KST | 검증 | 단일 연결로 제약 검증 재실행 | 생성 컬럼·유일 제약과 감사 enum 검증 | V42 제약 통과, 감사 테스트 데이터의 필수 actor 컬럼 누락 확인 | 채택 |
| 2026-08-16 18:36 KST | 검증 | 필수 감사 actor fixture 보정 후 원래 테스트 재실행 | 모든 migration과 제약 검증 통과 | Gradle 성공 | 채택 |

## 가설과 검증

### 가설 1: nullable 사용자 FK의 자동 삭제 동작과 상태 CHECK가 충돌한다

- 근거: 오류가 `reviewed_by_user_id`, FK 참조 동작, CHECK 제약을 함께 지목한다.
- 참일 때의 예측: 자동 `ON DELETE SET NULL`을 제거하면 동일 CHECK를 유지한 채 migration이 적용된다.
- 반증 조건: 자동 참조 동작을 제거해도 같은 오류 3823이 발생한다.
- 검증 방법: 세 사용자 FK의 `ON DELETE SET NULL`만 제거하고 원래 MySQL 테스트를 재실행한다.
- 결과: `ON DELETE SET NULL` 제거 후 V42 migration 적용 성공
- 판정: 채택

## 근본 원인

- 촉발 조건: MySQL 8이 V42의 테이블 생성문을 검증한다.
- 결함이 있는 코드·설정·데이터·계약: 사용자 FK에 `ON DELETE SET NULL`을 지정하면서 같은 FK 컬럼을 상태별 CHECK에서 참조했다.
- 증상으로 이어진 메커니즘: MySQL은 참조 동작에 사용되는 컬럼을 CHECK에서 사용하는 조합을 거부해 Flyway migration을 중단한다.
- 기존 방어가 막지 못한 이유: H2 migration과 JPA 테스트는 MySQL의 FK·CHECK 제한을 재현하지 않는다.
- 결론의 증거: MySQL 오류 코드 3823과 실패 컬럼·제약 이름.

## 해결 또는 완화

- 선택한 방법: 계약이 요구하는 nullable FK와 상태 CHECK를 유지하고, 확정 계약에 없는 자동 삭제 동작만 제거한다.
- 변경 파일: `src/main/resources/db/migration/V42__create_content_withdrawal_request.sql`
- 정책·계약 변경 여부: 없음. 회원 탈퇴 후 애플리케이션이 연결을 해제할 수 있는 nullable 컬럼 계약을 유지한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | MySQL 오류 3823 | 전체 migration과 V42 제약 검증 통과 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests '*ContentWithdrawalRequestMigrationMySqlTest'` | 실패 | 수정 전 재현 |
| 동일 테스트 1차 재실행 | migration 통과, 테스트 준비 실패 | 세션 변수와 연결 수명 불일치로 테스트 구성 보정 |
| 동일 테스트 2차 재실행 | V42 제약 통과, 감사 fixture 실패 | 기존 필수 `actor_kind`·`actor_role`을 fixture에 추가 |
| 동일 테스트 3차 재실행 | 성공 | MySQL 8.0.42에서 Flyway, FK, 생성 컬럼 유일 제약, 감사 enum 검증 |
| `./gradlew fastTest` | 성공 | 2,005개 빠른 테스트 회귀 검증 |

## 재발 방지와 문서 반영

MySQL migration 테스트에 FK, 상태별 CHECK, 생성 컬럼 유일 제약을 함께 유지한다.

## 잔여 위험과 후속 작업

확인된 잔여 위험 없음.

## 관련 자료

- Issue #873 구현 인계 요약
- `docs/adr/0101-store-content-withdrawal-requests-and-serialize-review.md`
