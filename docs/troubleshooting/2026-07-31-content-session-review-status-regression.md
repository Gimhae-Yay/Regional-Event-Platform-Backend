# 콘텐츠 회차 심사 상태 도입 시 발생한 회귀

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | 기존 `CANCELLED` 회차 마이그레이션 검증과 회차 변경 요청 저장소 테스트가 실패했다. |
| 최초 확인 시각·시간대 | 2026-07-31 16:06 KST |
| 관련 요구사항·이슈 | GitHub Issue #183, ADR-0038 |
| revision·브랜치 | `ac0d593a6de92ab0525fccc197962c87453577e0` · `feature/183-content-session-review-status` |
| 환경·프로필 | macOS, Java 21.0.7, Gradle 9.5.1, 기본 테스트 프로필의 H2 |

## 기대 결과와 실제 결과

### 기대 결과

- 기존 `SCHEDULED`, `COMPLETED`, `CANCELLED` 회차를 보존하면서 심사 상태와 심사 정보를 추가한다.
- 새 회차는 `PENDING`으로 생성되고 명시적 승인 후에만 `SCHEDULED`가 된다.
- 기존 회차 변경 요청 저장소 테스트도 명시된 상태 계약에 맞게 통과한다.

### 실제 결과

- 상태별 조건을 하나의 OR 식으로 표현한 심사 정보 제약 조건에서 기존 `CANCELLED` 픽스처 삽입이 거부됐다.
- 제약식을 수정한 뒤 전체 빌드에서는 `SessionRevisionRepositoryTest` 두 건이 `targetSession must be scheduled`로 실패했다.

## 재현 절차

### 선행 조건

- `V10__add_content_session_review_status.sql`과 새 `ContentSession` 상태 전이를 적용한다.
- `build.gradle`과 테스트 실행 환경은 변경하지 않는다.

### 명령·요청·입력

1. `./gradlew test --tests 'io.regionevent.regioneventbackend.global.config.InitialP0SchemaMigrationTest'`
2. `./gradlew build`
3. 기존 `CANCELLED` 마이그레이션 픽스처와 새 `ContentSession`을 사용하는 회차 변경 요청 픽스처를 실행한다.

### 재현 결과

- 실행 횟수: 각 명령 1회
- 성공 횟수: 0회
- 실패 횟수: 마이그레이션 검증 1건, 회차 변경 요청 저장소 테스트 2건
- 종료 코드·HTTP 상태: Gradle 종료 코드 1

## 수집한 증거

- H2가 기존 `CANCELLED` 회차 삽입 시 `ck_content_session_review_state` 위반을 보고했다.
- 동일한 상태별 조건을 함의 형태의 AND 식으로 바꾸자 같은 픽스처가 통과했다.
- 전체 빌드의 나머지 실패 두 건은 `SessionRevision` 생성 시 대상 회차가 `SCHEDULED`여야 한다는 도메인 검증에서 발생했다.
- 해당 테스트 픽스처는 `ContentSession` 생성 직후 저장했으며, 생성자의 새 계약은 초기 상태 `PENDING`이다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-31 16:06 KST | 관찰 | 마이그레이션 테스트 실행 | 기존 종료 상태 데이터 보존 여부 확인 | `CANCELLED` 삽입이 제약 조건 위반으로 실패 | 재현 |
| 2026-07-31 16:07 KST | 검증 | 상태별 OR 제약식을 동치인 함의 AND 식으로 변경 | 같은 기존 데이터가 허용되어야 함 | 마이그레이션 테스트 통과 | 채택 |
| 2026-07-31 16:08 KST | 관찰 | 전체 빌드 실행 | 다른 상태 계약 회귀 확인 | `SessionRevisionRepositoryTest` 두 건 실패 | 재현 |
| 2026-07-31 16:10 KST | 검증 | 대상 회차 픽스처에 명시적 승인 전이 추가 | `SCHEDULED` 선행 조건 충족 | 해당 테스트와 전체 빌드 통과 | 해결 |

## 가설과 검증

### 가설 1: 상태별 OR 제약식이 H2의 기존 종료 상태 데이터와 호환되지 않는다

- 근거: 심사 정보가 없는 `CANCELLED` 행이 새 제약 조건 위반으로 거부됐다.
- 참일 때의 예측: 같은 규칙을 상태별 함의 AND 식으로 표현하면 기존 행을 허용하면서 `REJECTED` 필수 정보는 계속 강제한다.
- 반증 조건: 제약식 변경 후에도 기존 행이 거부되거나 잘못된 `REJECTED` 행이 허용된다.
- 검증 방법: 마이그레이션 테스트에서 기존 상태 행과 심사 거절 필수값 위반 행을 함께 검증한다.
- 결과: 기존 상태 행은 통과하고 심사 정보가 불완전한 `REJECTED` 행은 거부됐다.
- 판정: 채택

### 가설 2: 기존 테스트 픽스처가 생성 즉시 `SCHEDULED`라는 이전 계약에 의존한다

- 근거: 실패 메시지는 대상 회차의 `SCHEDULED` 선행 조건이고, 픽스처는 새 회차를 승인하지 않았다.
- 참일 때의 예측: 픽스처에서 승인 전이를 명시하면 회차 변경 요청 저장 테스트가 통과한다.
- 반증 조건: 승인 후에도 같은 도메인 검증이 실패한다.
- 검증 방법: 심사자를 생성하고 `approve`를 호출한 뒤 대상 회차를 저장해 테스트를 재실행한다.
- 결과: `SessionRevisionRepositoryTest`와 전체 빌드가 통과했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 회차 심사 상태와 심사 정보 제약을 추가하고 새 회차의 초기 상태를 `PENDING`으로 변경했다.
- 결함이 있는 코드·설정·데이터·계약: H2에서 기존 상태와 호환되지 않은 제약식 표현, 이전 생성자 상태를 암묵적으로 가정한 테스트 픽스처.
- 증상으로 이어진 메커니즘: 마이그레이션 픽스처는 DB 제약에 막혔고, 변경 요청 픽스처는 `PENDING` 대상 회차를 전달해 도메인 선행 조건에 막혔다.
- 기존 방어가 막지 못한 이유: 상태별 DB 제약과 생성자 기본 상태 변경을 기존 종료 상태 및 연관 도메인 픽스처까지 처음부터 함께 검증하지 않았다.
- 결론의 증거: 같은 입력으로 제약식 표현과 승인 전이만 최소 변경했을 때 각각의 원래 재현 절차가 통과했다.

## 해결 또는 완화

- 선택한 방법: 심사 정보 제약을 상태별 함의 AND 식으로 작성하고, 회차 변경 요청 테스트 픽스처가 `approve`를 명시적으로 호출하도록 수정했다.
- 변경 파일: `V10__add_content_session_review_status.sql`, `SessionRevisionRepositoryTest.java`
- 정책·계약 변경 여부: 없음. ADR-0038의 초기 상태와 허용 전이를 구현하고 기존 상태 호환성을 유지한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 기존 `CANCELLED` 회차 마이그레이션 | 제약 조건 위반 | 마이그레이션 성공 | 통과 |
| 잘못된 `REJECTED` 심사 정보 | 제약 조건 위반 | 제약 조건 위반 유지 | 통과 |
| 회차 변경 요청 저장소 테스트 | 2건 실패 | 전체 통과 | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepositoryTest'` | 통과 | 명시적 승인 픽스처 검증 |
| `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.content.entity.ContentSessionTest' --tests 'io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepositoryTest' --tests 'io.regionevent.regioneventbackend.global.config.InitialP0SchemaMigrationTest'` | 통과 | 상태 전이, DB 제약, 기존 상태 호환성 |
| `./gradlew build` | 통과 | 전체 회귀 검증 |

## 재발 방지와 문서 반영

- 마이그레이션 테스트에서 기존 `SCHEDULED`, `COMPLETED`, `CANCELLED` 상태의 호환성과 `REJECTED` 필수 심사 정보를 함께 검증한다.
- 새 `ContentSession`을 연관 도메인의 테스트 픽스처로 사용할 때 필요한 상태 전이를 명시한다.

## 잔여 위험과 후속 작업

- 운영 DB에 대한 실제 마이그레이션 적용은 배포 절차에서 별도로 확인해야 한다.

## 관련 자료

- GitHub Issue #183
- ADR-0038
