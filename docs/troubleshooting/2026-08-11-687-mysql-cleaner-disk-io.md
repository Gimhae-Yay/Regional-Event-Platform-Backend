# #687 MySQL 테스트 DB 정리의 로컬 디스크 I/O 대기 조사

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 완화 |
| 영향 | 로컬 전체 Gradle 테스트의 종료 시간이 길어지거나, 동시 실행 상황에서 `MySqlDatabaseCleaner`가 MySQL 응답을 장시간 기다릴 수 있다. 제품 트랜잭션·운영 DB에는 영향이 없다. |
| 최초 확인 시각·시간대 | 부모 Bug #687에 기록된 시각은 확인 불가; 이 조사는 2026-08-11 KST에 시작했다. |
| 관련 요구사항·이슈 | [P0 테스트 및 출시 수용 기준](../p0-spec.md#9-테스트-및-출시-수용-기준), [#687](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/687), [#690](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/690) |
| revision·브랜치 | `829524359d5a968a2000379bcfd236653dc23d28`, `test/690-testcontainers-mysql-tmpfs` |
| 환경·프로필 | macOS arm64, OpenJDK 21.0.7, Gradle 9.5.1, Docker Server 29.5.2, Docker 메모리 7.75 GiB, Docker CPU 4, 기본 테스트 프로필 |

## 기대 결과와 실제 결과

### 기대 결과

동일 작업 트리에서 하나씩 실행하는 전체 테스트는 Testcontainers MySQL의 데이터 정리·migration·테스트 격리를
유지한 채 종료 코드 0으로 끝나야 한다. 이 조사에서는 동시 Gradle 실행을 새 정책으로 바꾸거나 재현하지 않는다.

### 실제 결과

부모 Bug #687에는 두 전체 빌드가 `MySqlDatabaseCleaner.truncate()`에서 MySQL 응답을 기다렸다는 관찰이 있다.
동일한 순차 전체 테스트는 tmpfs 미적용 3회와 적용 2회 모두 종료 코드 0으로 정상 종료했다. 첫 Before 실행은 1,710 tests가 모두 통과했지만
실행 제어 셸의 경과 시간 출력이 회수되지 않아 성능 수치에는 사용하지 않는다. 이어서 직접 추적한 두 실행은 각각
694.74초와 693.82초에 1,710 tests를 모두 통과했고 `swaps`는 0회였다. tmpfs 적용 뒤 두 실행은 517.31초와
507.26초에 1,710 tests를 모두 통과했고 `swaps`는 다시 0회였다. 이 조건에서 순차 전체 테스트 중앙값은 26.2%
감소했다. Docker 메모리 부족·OOM·tmpfs 공간 부족은 관찰하지 않았다. 대기 중인 Gradle daemon은 실행 중인 테스트
작업이라는 증거가 아니며 종료·변경하지 않는다.

## 재현 절차

### 선행 조건

- Docker daemon이 실행 중이고, 같은 작업 트리에서 Gradle 명령은 한 번만 실행한다.
- `SharedMySqlTestContainer`는 아직 Docker writable layer를 사용한다.
- `git status --short`가 비어 있고 기준 revision은 위 표와 같다.

### 명령·요청·입력

1. `/usr/bin/time -lp ./gradlew --no-daemon clean test`를 실행한다.
2. 첫 실행이 종료 코드 0으로 끝난 뒤 같은 명령을 한 번 더 실행한다.
3. 각 실행의 경과 시간, 종료 코드, Testcontainers MySQL 시작·정리 실패, Docker 메모리 관련 출력을 기록한다.

### 재현 결과

- 실행 횟수: 5회(보조 정상성 확인 1회, 유효 Before 측정 2회, 유효 After 측정 2회)
- 성공 횟수: 5회
- 실패 횟수: 0회
- 종료 코드·HTTP 상태: 모두 종료 코드 0

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- 부모 Bug #687의 스레드 덤프 관찰은 `MySqlDatabaseCleaner.truncate()` → `clean()` →
  `NonTransactionalMySqlTestSupport.cleanDatabaseBeforeTest()`에서 MySQL 응답을 기다렸다고 설명한다.
- 현재 `SharedMySqlTestContainer`는 `mysql:8.0.42`를 JVM 안에서 공유하고 테스트 datasource Hikari 최대 풀 크기를
  4로 제한하지만, MySQL 데이터 디렉터리의 저장소는 별도로 구성하지 않는다.
- Testcontainers 2.0.5의 `GenericContainer.withTmpFs(Map<String, String>)` API와 Docker의 동작 환경을 확인했다.
- 첫 실행 중 스레드 덤프에서 Test worker는 `MySqlDatabaseCleaner.truncate()`의 MySQL socket read에 있었고,
  같은 시점 MySQL process list에는 `TRUNCATE TABLE stampbook`이 실행 중이었다. 이 단일 관찰만으로 잠금 대기나
  Docker 디스크 I/O를 근본 원인으로 확정하지 않는다.
- 유효 Before 1: `real 694.74`, `user 472.63`, `sys 59.19`, 최대 RSS 2,674,163,712 bytes, swaps 0회
- 유효 Before 2: `real 693.82`, `user 489.58`, `sys 55.13`, 최대 RSS 2,666,905,600 bytes, swaps 0회

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-11 KST | 관찰 | #687의 기존 대기 증거와 현행 공유 MySQL 지원 코드를 확인 | cleaner의 정리 경로와 Docker writable layer 사용 여부를 분리해 확인한다. | 후자는 별도 구성 없이 기본 저장소를 사용한다. | 채택 |
| 2026-08-11 KST | 가설 | 정상 순차 전체 테스트에서 Docker writable layer의 fsync가 정리 시간을 키운다 | tmpfs 적용 전후 같은 명령의 중앙값과 정상 종료·격리를 비교한다. | 측정 예정 | 대기 |
| 2026-08-11 KST | 변경 계획 | 테스트 컨테이너 데이터 디렉터리만 제한된 tmpfs로 옮긴다 | 제품 설정 변화 없이 MySQL 통합 테스트와 전체 테스트가 통과한다. | [ADR-0093](../adr/0093-use-tmpfs-for-testcontainers-mysql-data.md) 채택 | 채택 |
| 2026-08-11 KST | 검증 | tmpfs 미적용 순차 전체 테스트를 두 번 측정 | 두 실행 모두 종료 코드 0이고 동일 조건의 비교 기준을 확보한다. | 694.74초·693.82초, 각 1,710 tests 통과, swaps 0회 | 채택 |
| 2026-08-11 KST | 변경 | `SharedMySqlTestContainer`의 `/var/lib/mysql`에 `rw,size=512m` tmpfs 적용 | 제품 설정 변화 없이 데이터 디렉터리만 tmpfs가 된다. | [SharedMySqlTestContainerTest](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainerTest.java) 통과 | 채택 |
| 2026-08-11 KST | 검증 | tmpfs 적용 순차 전체 테스트를 두 번 측정 | 두 실행 모두 종료 코드 0이고 Before 중앙값보다 작으며 격리·메모리 조건을 유지한다. | 517.31초·507.26초, 각 1,710 tests 통과, 중앙값 -26.2%, swaps 0회 | 채택 |

## 가설과 검증

### 가설 1: Testcontainers MySQL 데이터 파일의 Docker writable layer I/O가 순차 전체 테스트의 정리 비용에 기여한다

- 근거: #690의 범위와 `SharedMySqlTestContainer`의 기본 저장소 구성, 대량 `TRUNCATE`를 수행하는 cleaner 경로
- 참일 때의 예측: 같은 Docker·Java·Gradle 환경의 순차 전체 테스트에서 tmpfs 적용 후 중앙 경과 시간이 Before보다 작고,
  migration·데이터 정리·전체 테스트가 계속 통과한다.
- 반증 조건: After가 개선되지 않거나, tmpfs 때문에 데이터 격리·테스트 정상 종료가 깨지거나 Docker 메모리 부족·swap·OOM이 발생한다.
- 검증 방법: `./gradlew --no-daemon clean test`를 Before와 After에 각각 2회 순차 실행하고 중앙값·종료 코드·오류를 비교한다.
- 결과: After 2회는 517.31초·507.26초에 정상 종료했고 중앙값은 Before 694.28초에서 After 512.29초로
  182.00초(26.2%) 줄었다. `MySqlDatabaseCleanerIntegrationTest`와 After 전체 테스트에서 격리가 유지됐다.
- 판정: 채택. 단, 이 결과는 Docker writable layer I/O가 순차 실행 비용에 기여한다는 가설을 지지할 뿐, #687의
  동시 Gradle 실행 잠금 대기의 유일한 근본 원인을 입증하지는 않는다.

## 근본 원인

- 촉발 조건: 순차 전체 테스트의 공유 Testcontainers MySQL 데이터 디렉터리 쓰기. 부모 Bug #687의 동시 전체 빌드 재현은 이 Task의 제외 범위다.
- 결함이 있는 코드·설정·데이터·계약: 근본 원인으로 확정하지 않음. MySQL 테스트 데이터가 기본 Docker writable layer를 사용하는 구성은 성능 완화 대상으로만 확인됐다.
- 증상으로 이어진 메커니즘: `MySqlDatabaseCleaner`의 반복 `TRUNCATE`가 MySQL 데이터 파일 변경을 일으키며, tmpfs 적용 뒤 순차 전체 테스트 중앙값이 줄었다. 파일시스템 I/O 계측이나 동시 잠금 재현이 없으므로 Docker writable layer I/O를 #687의 근본 원인으로 확정하지 않는다. tmpfs는 잠금·동시 실행 정책을 변경하지 않는다.
- 기존 방어가 막지 못한 이유: 순차 성능·메모리 한계와 동시 실행 잠금 대기를 분리해 관찰하는 기록이 없었다.
- 결론의 증거: Before/After 각 2회 동일 명령의 중앙값이 26.2% 감소했고, MySQL cleaner 통합 테스트·After 전체 테스트·별도 `test`·`build`가 모두 통과했다. 동시 실행 잠금의 원인과 해소 여부는 확인하지 못했다.

## 해결 또는 완화

- 선택한 방법: ADR-0093에 따라 테스트 전용 MySQL 데이터 디렉터리에 512 MiB 용량 제한 tmpfs를 적용했다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainer.java`, `src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainerTest.java`
- 정책·계약 변경 여부: 제품 API·DB·운영 Docker 정책 변경 없음.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 순차 전체 테스트 정상 종료 | 2/2 성공, 694.74초·693.82초 | 2/2 성공, 517.31초·507.26초 | 완화 |
| MySQL 테스트 데이터 격리 | 1,710 tests 통과 | cleaner 통합 테스트, After 전체 테스트 2회 통과 | 유지 |
| Docker 메모리 부족·swap·OOM | swaps 0회, OOM 없음 | swaps 0회, OOM·tmpfs 공간 부족 없음 | 유지 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests 'io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainerTest'` | 성공 | 9초, Docker 없이 tmpfs 매핑 검증 |
| `./gradlew --no-daemon clean test --tests 'io.regionevent.regioneventbackend.support.mysql.MySqlDatabaseCleanerIntegrationTest'` | 성공 | 52초, migration·fixture 정리·Flyway 이력 보존 |
| `./gradlew --no-daemon clean test` | Before·After 각 2회 성공 | Before 694.74초·693.82초, After 517.31초·507.26초; 각 1,710 tests |
| `./gradlew test` | 성공 | 이전 After 전체 테스트를 재사용해 UP-TO-DATE, 2초 |
| `./gradlew build` | 성공 | 이전 After 전체 테스트를 재사용해 `test` UP-TO-DATE, 2초 |

## 재발 방지와 문서 반영

- ADR-0093에 tmpfs 범위·512 MiB 데이터 디렉터리 상한·롤백·대체 조건을 기록했다.
- 같은 명령·환경의 improvement-log에 Before/After 판정과 원시 수치를 기록했다.

## 잔여 위험과 후속 작업

- tmpfs는 같은 작업 트리의 동시 Gradle 실행으로 발생하는 잠금 대기 자체를 해결하지 않는다. 이 조사의 최종 상태를
  `해결`이 아닌 `완화`로 둔 이유다.
- Docker 메모리 구성, fixture 크기 또는 MySQL 이미지가 바뀌면 512 MiB 상한을 다시 검증해야 한다.
- 동시 실행 대기의 근본 원인은 #687 범위에서 별도 재현·정책 결정이 필요하다.

## 관련 자료

- [ADR-0093](../adr/0093-use-tmpfs-for-testcontainers-mysql-data.md)
- [SharedMySqlTestContainer](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainer.java)
- [MySqlDatabaseCleaner](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/MySqlDatabaseCleaner.java)
