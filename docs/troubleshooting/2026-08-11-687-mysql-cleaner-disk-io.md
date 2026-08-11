# #687 MySQL 테스트 DB 정리의 로컬 디스크 I/O 대기 조사

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 조사 중 |
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
동일한 순차 전체 테스트는 3회 모두 종료 코드 0으로 정상 종료했다. 첫 실행은 1,710 tests가 모두 통과했지만
실행 제어 셸의 경과 시간 출력이 회수되지 않아 성능 수치에는 사용하지 않는다. 이어서 직접 추적한 두 실행은 각각
694.74초와 693.82초에 1,710 tests를 모두 통과했고 `swaps`는 0회였다. 이 시점에는 Docker 메모리 부족·OOM을
관찰하지 않았다. 대기 중인 Gradle daemon은 실행 중인 테스트 작업이라는 증거가 아니며 종료·변경하지 않는다.

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

- 실행 횟수: 3회(보조 정상성 확인 1회, 유효 Before 측정 2회)
- 성공 횟수: 3회
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

## 가설과 검증

### 가설 1: Testcontainers MySQL 데이터 파일의 Docker writable layer I/O가 순차 전체 테스트의 정리 비용에 기여한다

- 근거: #690의 범위와 `SharedMySqlTestContainer`의 기본 저장소 구성, 대량 `TRUNCATE`를 수행하는 cleaner 경로
- 참일 때의 예측: 같은 Docker·Java·Gradle 환경의 순차 전체 테스트에서 tmpfs 적용 후 중앙 경과 시간이 Before보다 작고,
  migration·데이터 정리·전체 테스트가 계속 통과한다.
- 반증 조건: After가 개선되지 않거나, tmpfs 때문에 데이터 격리·테스트 정상 종료가 깨지거나 Docker 메모리 부족·swap·OOM이 발생한다.
- 검증 방법: `./gradlew --no-daemon clean test`를 Before와 After에 각각 2회 순차 실행하고 중앙값·종료 코드·오류를 비교한다.
- 결과: Before 2회는 694.74초·693.82초에 정상 종료했다. After 측정 전이므로 병목 기여 여부는 아직 확인하지 못했다.
- 판정: 대기

## 근본 원인

- 촉발 조건: 확인 중. 부모 Bug #687의 동시 전체 빌드 재현은 이 Task의 제외 범위다.
- 결함이 있는 코드·설정·데이터·계약: 확인 중. 이 Task는 Docker writable layer I/O를 완화하는 설정만 검증한다.
- 증상으로 이어진 메커니즘: 확인 중. tmpfs는 잠금·동시 실행 정책을 변경하지 않는다.
- 기존 방어가 막지 못한 이유: 순차 성능·메모리 한계와 동시 실행 잠금 대기를 분리해 관찰하는 기록이 없었다.
- 결론의 증거: Before 정상 종료·스레드 덤프·process list는 수집했다. After 실행 결과와 MySQL 통합 테스트 결과를 추가한다.

## 해결 또는 완화

- 선택한 방법: ADR-0093에 따라 테스트 전용 MySQL 데이터 디렉터리에 용량 제한 tmpfs를 적용할 예정이다.
- 변경 파일: 측정 및 구현 후 갱신한다.
- 정책·계약 변경 여부: 제품 API·DB·운영 Docker 정책 변경 없음.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 순차 전체 테스트 정상 종료 | 2/2 성공, 694.74초·693.82초 | 측정 예정 | 대기 |
| MySQL 테스트 데이터 격리 | 측정 예정 | 측정 예정 | 대기 |
| Docker 메모리 부족·swap·OOM | swaps 0회, OOM 없음 | 측정 예정 | 대기 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew --no-daemon clean test` | Before 2회 성공 | 1,710 tests, 유효 경과 시간 694.74초·693.82초 |
| `./gradlew test` | 측정 예정 | After 전체 회귀 |
| `./gradlew build` | 측정 예정 | After 전체 빌드 |

## 재발 방지와 문서 반영

- ADR-0093에 tmpfs 범위·512 MiB 데이터 디렉터리 상한·롤백·대체 조건을 기록했다.
- 실제 결과가 추가되면 같은 명령·환경의 improvement-log에만 성능 판정을 남긴다.

## 잔여 위험과 후속 작업

- tmpfs는 같은 작업 트리의 동시 Gradle 실행으로 발생하는 잠금 대기 자체를 해결하지 않는다.
- Docker 메모리 구성, fixture 크기 또는 MySQL 이미지가 바뀌면 512 MiB 상한을 다시 검증해야 한다.
- 동시 실행 대기의 근본 원인은 #687 범위에서 별도 재현·정책 결정이 필요하다.

## 관련 자료

- [ADR-0093](../adr/0093-use-tmpfs-for-testcontainers-mysql-data.md)
- [SharedMySqlTestContainer](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainer.java)
- [MySqlDatabaseCleaner](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/MySqlDatabaseCleaner.java)
