# 미션 정의 마이그레이션의 H2 상태 시각 CHECK 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | #589의 Flyway migration 검증이 실패해 미션 영속 기반 구현을 완료·통합할 수 없었다. |
| 최초 확인 시각·시간대 | 2026-08-08 15:55 KST |
| 관련 요구사항·이슈 | #589, P1 ERD 3.3·5.3, 지역 미션 MSN-01 |
| revision·브랜치 | f9783964, `feature/589-regional-mission-definition-persistence` |
| 환경·프로필 | H2 2.4.240 MySQL mode, Gradle 9.5.1, Corretto OpenJDK 21.0.11, 별도 Spring 프로필 미사용 |

## 기대 결과와 실제 결과

### 기대 결과

`DRAFT` 미션은 `published_at`, `ended_at`이 모두 `NULL`인 상태로 저장된다. `PUBLISHED` 상태만
`published_at < ends_at`를 만족해야 하며, `ENDED`는 실제 종료 시각을 가진다.

### 실제 결과

기존 `DriverManagerDataSource` 기반 검증은 Flyway migration 뒤 새 H2 연결을 열어
유효한 `DRAFT` 미션 INSERT를 `CK_MISSION_STATUS_TIMESTAMPS` 위반으로 실패시켰다.
`SingleConnectionDataSource`로 Flyway와 검증 SQL을 같은 H2 연결에서 실행하도록 바꾼 뒤
동일 시나리오가 통과한다.

## 재현 절차

### 선행 조건

- `feature/589-regional-mission-definition-persistence` 브랜치
- V18 migration과 `MissionDefinitionMigrationTest`가 아직 커밋되지 않은 상태

### 명령·요청·입력

1. H2 메모리 DB를 MySQL mode로 생성한다.
2. `classpath:db/migration`의 Flyway 최신 migration을 적용한다.
3. `region`, `content`, `MISSION_REWARD` 쿠폰 정책을 만들고 `DRAFT` 미션을 INSERT한다.
4. `./gradlew test --tests io.regionevent.regioneventbackend.global.config.MissionDefinitionMigrationTest`를 실행한다.

### 재현 결과

- 실행 횟수: 2
- 성공 횟수: 0
- 실패 횟수: 2
- 종료 코드·HTTP 상태: Gradle test 종료 코드 1, HTTP 요청 없음

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- `build/test-results/test/TEST-io.regionevent.regioneventbackend.global.config.MissionDefinitionMigrationTest.xml`
- 첫 INSERT: `mission_id=1`, `condition_type=VISIT_COUNT`, `required_visit_count=3`,
  `status=DRAFT`, `published_at=NULL`, `ended_at=NULL`
- CHECK 위반 뒤 H2 내부 원인: `JdbcSQLNonTransientConnectionException: The database has been closed [90098-240]`

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-08 15:55 KST | 관찰 | 최초 최소 테스트 실행 | 유효한 DRAFT INSERT가 성공해야 한다. | `CK_MISSION_STATUS_TIMESTAMPS` 위반 | 재현 |
| 2026-08-08 16:07 KST | 변경 | `IN`을 동등한 명시 OR 비교로 변경 | H2가 `IN` 평가 문제라면 DRAFT INSERT가 성공한다. | 동일 실패 | 기각 |
| 2026-08-08 16:27 KST | 검증 | SQL `NULL` 리터럴로 DRAFT INSERT | JDBC `NULL` 바인딩이 원인이면 리터럴 INSERT는 성공한다. | 동일 CHECK 위반 | 기각 |
| 2026-08-08 16:28 KST | 검증 | Flyway와 같은 H2 연결에서 DRAFT INSERT | 연결 경계가 원인이면 단일 연결에서는 성공한다. | 성공 | 채택 |
| 2026-08-08 17:12 KST | 수정·검증 | `SingleConnectionDataSource`로 migration 검증 실행 | 동일 migration·DRAFT INSERT가 성공한다. | 대상 테스트 통과 | 해결 |

## 가설과 검증

### 가설 1: H2가 CHECK의 `IN` 조건을 DRAFT 상태에서 다르게 평가한다

- 근거: 실패한 CHECK에 `status IN ('DRAFT', 'PENDING_REVIEW')`가 포함됐다.
- 참일 때의 예측: `IN`을 명시 OR 비교로 바꾸면 동일 INSERT가 성공한다.
- 반증 조건: 명시 OR 비교에서도 동일 실패가 난다.
- 검증 방법: V18 CHECK의 해당 표현만 명시 OR 비교로 바꾸고 대상 테스트를 실행한다.
- 결과: 동일 CHECK 위반이 재현됐다.
- 판정: 기각.

### 가설 2: `JdbcTemplate`의 `NULL` 바인딩이 상태 시각 CHECK를 실패시킨다

- 근거: 최초 재현은 `published_at`, `ended_at`을 JDBC 매개변수 `null`로 전달했다.
- 참일 때의 예측: `NULL` SQL 리터럴로 같은 DRAFT 행을 넣으면 성공한다.
- 반증 조건: SQL 리터럴 INSERT도 동일 CHECK 위반으로 실패한다.
- 검증 방법: 같은 Flyway DB에서 상태 시각만 `NULL` SQL 리터럴로 넣는 진단 테스트를 추가한다.
- 결과: 두 상태 시각을 리터럴 `NULL`로 넣은 DRAFT INSERT도 `CK_MISSION_STATUS_TIMESTAMPS` 위반으로 실패했다.
- 판정: 기각.

### 가설 3: Flyway migration 뒤의 H2 연결 경계가 CHECK 평가에 영향을 준다

- 근거: 오류 체인에 H2 데이터베이스 종료 예외가 포함됐고, raw H2 단일 연결에서는 같은 CHECK와 INSERT가 성공했다.
- 참일 때의 예측: Flyway와 INSERT가 같은 물리 H2 연결을 사용하면 DRAFT INSERT가 성공한다.
- 반증 조건: 단일 연결에서도 동일 CHECK 위반이 난다.
- 검증 방법: `SingleConnectionDataSource`로 Flyway와 INSERT를 실행한다.
- 결과: 단일 연결 테스트는 성공했다. 기존 `DriverManagerDataSource` 기반 테스트 두 개는 계속 실패했다.
- 판정: 채택 후보. 일반 `DriverManagerDataSource`의 연결 재생성과 Flyway 영향 중 어느 쪽인지 추가 분리한다.

## 근본 원인

- 촉발 조건: Flyway migration 완료 뒤 `DriverManagerDataSource`로 새 H2 연결을 열어 검증 SQL을 실행했다.
- 결함이 있는 설정: 메모리 H2 migration 검증이 schema 생성과 검증 SQL의 연결 생명주기를 분리했다.
- 증상으로 이어진 메커니즘: H2 데이터베이스가 최초 연결 종료 시점의 상태를 보장하지 못해 후속 INSERT가 CHECK 제약 위반으로 보고됐다.
- 기존 방어가 막지 못한 이유: migration 검증이 실제 Flyway 연결과 다른 물리 연결을 사용했다.
- 결론의 증거: 동일 CHECK와 INSERT가 단일 H2 연결에서는 성공했고, `SingleConnectionDataSource` 적용 뒤 대상 migration 테스트가 통과했다.

## 해결 또는 완화

- 선택한 방법: Flyway와 검증 SQL이 하나의 물리 H2 연결을 공유하도록 `SingleConnectionDataSource`를 사용했다.
- 변경 파일: `MissionDefinitionMigrationTest`, V18 migration, `Mission` 엔티티의 상태 시각 CHECK 표현.
- 정책·계약 변경 여부: 없음. `DRAFT`·`PENDING_REVIEW`의 시각 null 규칙을 `CASE ... END = 1`으로 명시해 SQL `UNKNOWN`을 차단했다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | 실패 | 통과 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests io.regionevent.regioneventbackend.global.config.MissionDefinitionMigrationTest` | 통과 | 수정 뒤 대상 migration 검증 |

## 재발 방지와 문서 반영

같은 migration 검증에서는 Flyway와 SQL 검증이 동일한 H2 연결을 사용하도록 유지한다.

## 잔여 위험과 후속 작업

MySQL Testcontainers 검증은 Docker 실행 환경에서 별도로 확인해야 한다.

## 관련 자료

- `src/main/resources/db/migration/V18__create_mission_definition_tables.sql`
- `src/test/java/io/regionevent/regioneventbackend/global/config/MissionDefinitionMigrationTest.java`
