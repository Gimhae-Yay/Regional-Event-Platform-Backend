# PR #305 CI 테스트 장기 실행

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #305의 CI 빌드 및 테스트 단계가 MySQL 메타데이터 락을 무기한 대기해 15분 job timeout으로 취소되며 머지 검증이 실패한다. |
| 최초 확인 시각·시간대 | 2026-08-03 12:05 KST |
| 관련 요구사항·이슈 | PR #305, Task #298, Bug #297 |
| revision·브랜치 | `2ca28c054b0cf977becb3a0f5a8c3a8315674733`, `feature/298-shared-mysql-test-infrastructure` → `dev` |
| 환경·프로필 | GitHub Actions `ubuntu-latest`, Amazon Corretto 21.0.12, Gradle 9.5.1, MySQL Testcontainers 2.0.5 / 로컬 macOS 26.5.2, Amazon Corretto 21.0.11, Docker Desktop 29.5.2 |

## 기대 결과와 실제 결과

### 기대 결과

`./gradlew --no-daemon clean build`가 기존 CI 기준 시간과 비슷하거나 더 짧게 완료되고, 신규 MySQL 테스트 인프라 검증이 실제 Docker 환경에서 통과한다.

### 실제 결과

GitHub Actions run `30780603228`의 `빌드 및 테스트` 단계는 2026-08-03 11:59:11 KST부터 12:14:09 KST까지 진행된 뒤 15분 job timeout으로 취소됐다. artifact의 마지막 테스트 출력은 `MySqlDatabaseCleanerIntegrationTest`가 시작됐다는 기록이며 이후 출력 없이 timeout됐다. 로컬 Docker에서도 같은 테스트가 `TRUNCATE TABLE test_cleanup_child`의 메타데이터 락을 117초 이상 기다려 수동 취소했다.

수정 후 같은 Docker 환경에서 cleaner 단일 테스트는 34.44초에 성공했고, 전체 `clean build`는 4분 8초에 681건 모두 성공했다.

## 재현 절차

### 선행 조건

- PR #305 head `2ca28c0`
- Docker를 사용할 수 있는 GitHub Actions runner
- CI 워크플로의 `./gradlew --no-daemon clean build`

### 명령·요청·입력

1. PR #305 head에서 Docker daemon을 실행한다.
2. `./gradlew --no-daemon clean test --tests 'io.regionevent.regioneventbackend.support.mysql.MySqlDatabaseCleanerIntegrationTest' --info`를 실행한다.
3. 테스트가 멈춘 동안 테스트 JVM의 스레드 덤프와 MySQL `SHOW FULL PROCESSLIST`, `information_schema.innodb_trx`, `performance_schema.metadata_locks`를 확인한다.

### 재현 결과

- 실행 횟수: 2회(CI 전체 빌드 1회, 로컬 단일 테스트 1회)
- 성공 횟수: 0
- 실패 횟수: 2
- 종료 코드·HTTP 상태: CI 15분 timeout으로 `cancelled`, 로컬 117.68초 대기 후 수동 취소

## 수집한 증거

- 기준 Bug #297의 최신 CI 빌드 및 테스트 단계: 5분 10초
- PR #305 CI run `30780603228`: `빌드 및 테스트`가 14분 58초 실행된 뒤 job timeout으로 취소
- 로컬 `./gradlew --no-daemon clean build`: 44초 성공, Docker 의존 테스트 17개 클래스 skip
- 최종 PR diff에서 공유 컨테이너를 사용하는 테스트는 `MySqlDatabaseCleanerIntegrationTest` 한 클래스뿐이다.
- Testcontainers 공식 singleton 패턴은 수동 `start()` 후 테스트 스위트 종료 시 Ryuk 정리를 허용한다.
- 로컬 테스트 JVM 스레드 덤프: `Test worker`가 `MySqlDatabaseCleaner.truncate(MySqlDatabaseCleaner.java:96)`에서 MySQL 응답을 기다린다.
- MySQL process list: insert 연결은 미커밋 2행을 가진 `RUNNING` 트랜잭션이고 cleaner 연결은 `TRUNCATE TABLE test_cleanup_child`에서 `Waiting for table metadata lock` 상태다.
- metadata lock: insert 연결의 `SHARED_WRITE` 잠금은 `GRANTED`, cleaner 연결의 `EXCLUSIVE` 잠금은 `PENDING`이다.
- concrete 테스트 클래스에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 직접 추가한 임시 대조 실험은 동일 테스트를 21.64초에 통과시켰다. 임시 변경은 원복했다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-03 12:05 KST | 관찰 | CI 장기 실행 확인 | 기존 5분 10초 기준을 초과하는지 확인 | 9분 이상 실행 중 | 채택 |
| 2026-08-03 12:06 KST | 가설 | singleton 컨테이너를 명시적으로 stop하지 않아 JVM이 종료되지 않는다. | 공식 수명주기에서 수동 stop이 필수이거나 CI가 테스트 완료 뒤 정지해야 한다. | 공식 문서는 Ryuk가 스위트 종료 시 정리하는 패턴을 지원한다. | 기각 |
| 2026-08-03 12:12 KST | 검증 | 신규 cleaner 테스트의 락 대기를 로컬 Docker에서 재현한다. | 스레드 덤프와 MySQL process list가 동일 SQL 대기를 가리켜야 한다. | JVM은 `truncate:96`, MySQL은 `Waiting for table metadata lock`을 표시했다. | 채택 |
| 2026-08-03 12:13 KST | 검증 | `@DataJpaTest`의 트랜잭션이 상위 클래스의 `NOT_SUPPORTED`를 덮어쓴다. | concrete 클래스에 `NOT_SUPPORTED`를 직접 선언하면 락 대기가 사라져야 한다. | 임시 직접 선언 후 21.64초에 통과했다. | 채택 |
| 2026-08-03 12:14 KST | 관찰 | 원격 CI의 종료 상태와 마지막 테스트를 확인한다. | 같은 원인이면 cleaner 테스트 시작 뒤 출력 없이 job timeout돼야 한다. | cleaner 테스트가 03:04:12 UTC에 시작된 뒤 추가 출력 없이 03:14:09 UTC 취소됐다. | 채택 |
| 2026-08-03 12:24 KST | 변경·검증 | concrete 비트랜잭션 선언과 cleaner 실행 전 fail-fast 검증을 적용한다. | 원래 재현 테스트와 전체 Docker 빌드가 timeout 없이 통과해야 한다. | 단일 테스트 34.44초, 전체 빌드 4분 8초에 성공했다. | 채택 |

## 가설과 검증

### 가설 1: 신규 MySQL cleaner 테스트가 메타데이터 락 또는 정리 순서에서 대기한다.

- 근거: PR #305가 Docker에서 실제 실행되는 신규 통합 테스트와 전체 테이블 `TRUNCATE`를 추가했다.
- 참일 때의 예측: CI 로그의 마지막 실행 위치가 cleaner 테스트이며, 해당 테스트 또는 test task가 timeout까지 종료되지 않는다.
- 반증 조건: cleaner 테스트가 짧게 통과하고 다른 기존 테스트에서 대부분의 시간이 소비된다.
- 검증 방법: Actions 로그와 테스트 XML의 실행시간을 수집한다.
- 결과: 로컬에서 `TRUNCATE TABLE test_cleanup_child`의 `EXCLUSIVE` 메타데이터 락이 미커밋 insert 연결의 `SHARED_WRITE` 락을 기다리는 상태를 재현했다. 원격 artifact도 cleaner 테스트 시작 뒤 멈췄다.
- 판정: 채택

### 가설 2: 전체 테스트 순차 실행으로 기존 MySQL 컨테이너 15개가 직렬 시작된다.

- 근거: PR #305는 JUnit 병렬 실행을 명시적으로 끄지만 기존 MySQL 테스트의 공유 컨테이너 전환은 후속 Task #299 범위다.
- 참일 때의 예측: 신규 공통 컨테이너는 한 클래스에서만 사용되고 기존 15개 클래스는 각각 컨테이너를 시작해 기존 비용이 그대로 발생한다.
- 반증 조건: 기존 테스트가 이미 공통 컨테이너를 사용하거나 CI 로그에서 컨테이너 시작이 한 번뿐이다.
- 검증 방법: 최종 diff와 Actions 테스트 리포트·로그를 대조한다.
- 결과: 최종 diff상 기존 15개 테스트는 전환되지 않아 기존 실행 비용은 유지되지만, baseline은 5분 10초에 정상 종료됐다. 이번 15분 timeout은 cleaner 테스트의 무기한 락 대기에서 발생했다.
- 판정: 장기 실행 비용의 배경이지만 timeout의 근본 원인은 아니므로 기각

## 근본 원인

- 촉발 조건: `@DataJpaTest`인 `MySqlDatabaseCleanerIntegrationTest`가 부모 지원 클래스만으로 비트랜잭션 실행된다고 가정하고, 같은 테스트 본문에서 insert 후 별도 연결의 cleaner를 호출한다.
- 결함이 있는 코드·설정·데이터·계약: `NonTransactionalMySqlTestSupport`의 상속된 `@Transactional(NOT_SUPPORTED)`가 concrete 클래스의 `@DataJpaTest` 메타 애너테이션이 제공하는 `@Transactional(REQUIRED)`보다 우선하지 못한다.
- 증상으로 이어진 메커니즘: 테스트 트랜잭션의 insert 연결이 fixture 테이블의 `SHARED_WRITE` 메타데이터 락을 유지한다. cleaner의 별도 연결이 같은 테이블을 `TRUNCATE`하려면 `EXCLUSIVE` 메타데이터 락이 필요하지만 자기 테스트 트랜잭션이 끝날 때까지 획득할 수 없어 순환 대기한다.
- 기존 방어가 막지 못한 이유: 비트랜잭션 여부 assertion은 cleaner 호출 뒤에 있어 도달하지 못하고, 테스트에 timeout도 없어 CI job timeout까지 대기한다. 최종 커밋에서 별도 트랜잭션 경계 검증도 삭제됐다.
- 결론의 증거: JVM 스택의 `truncate:96`, MySQL process/transaction/metadata lock 상태, concrete `NOT_SUPPORTED` 직접 선언 후 21.64초 통과, 원격 CI artifact의 마지막 테스트가 모두 같은 실행 경로를 가리킨다.

## 해결 또는 완화

- 선택한 방법: concrete `@DataJpaTest`에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 직접 선언했다. 지원 클래스는 cleaner 호출 전후에 활성 트랜잭션을 검사해 잘못된 상속 구성을 즉시 실패시키며, cleaner 테스트에는 10초 timeout을 추가했다. Spring의 트랜잭션 롤백 동작을 재검증하는 테스트는 추가하지 않았다.
- 변경 파일: `MySqlDatabaseCleanerIntegrationTest.java`, `NonTransactionalMySqlTestSupport.java`, 이 조사 기록
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | 117.68초 이상 metadata lock 대기 후 수동 취소 | 수정 적용 후 34.44초 성공 | 해결 |
| 전체 Docker 빌드 | CI 15분 timeout 취소 | 로컬 4분 8초 성공, 681건 통과 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew --no-daemon test --tests 'io.regionevent.regioneventbackend.support.mysql.MySqlDatabaseCleanerIntegrationTest'` | 성공, 1건 skip | 로컬 Docker daemon 중지 |
| `./gradlew --no-daemon clean build` | 44초 성공 | Docker 의존 테스트 17개 클래스 skip |
| Docker에서 cleaner 테스트 원본 실행 | 117.68초 이상 대기 후 수동 취소 | MySQL metadata lock 재현 |
| concrete `NOT_SUPPORTED` 임시 직접 선언 후 cleaner 테스트 | 21.64초 성공 | 임시 변경 원복 |
| 수정 적용 후 `./gradlew --no-daemon clean test --tests 'io.regionevent.regioneventbackend.support.mysql.MySqlDatabaseCleanerIntegrationTest'` | 34.44초 성공 | 1건 실행, skip·실패 없음 |
| 수정 적용 후 `./gradlew --no-daemon clean build` | 4분 8초 성공 | 681건, skip 0, 실패 0, 오류 0 |

## 재발 방지와 문서 반영

- cleaner 테스트에 짧은 `@Timeout`을 두어 같은 회귀가 CI job timeout까지 숨지 않게 한다.
- `NonTransactionalMySqlTestSupport`의 cleaner 실행 전에 실제 트랜잭션 활성 여부를 검증해 잘못된 상속 사용을 즉시 실패시킨다.
- Spring 프레임워크의 기본 롤백 동작을 중복 검증하지 않고, 공유 컨테이너와 JDBC 옵션처럼 프로젝트가 직접 만든 경계만 별도로 검증한다.

## 잔여 위험과 후속 작업

- 수정은 로컬 PR 브랜치에만 있으며 커밋·push하지 않았다.
- 원격 GitHub Actions 재실행은 커밋·push 전이므로 수행하지 않았다.
- 향후 비트랜잭션 테스트는 concrete 클래스에 `NOT_SUPPORTED`를 명시해야 하며, 누락 시 지원 클래스의 fail-fast 검증으로 즉시 실패한다.

## 관련 자료

- GitHub Actions run `30780603228`
- PR #305
- Issue #297, #298, #299
- Testcontainers 공식 manual lifecycle control 문서
