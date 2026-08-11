# ADR-0093: Testcontainers MySQL 데이터 디렉터리를 테스트 전용 tmpfs로 구성한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-11
- 결정일: 2026-08-11
- 관련 요구사항: [P0 명세](../p0-spec.md#9-테스트-및-출시-수용-기준)의 자동화 테스트 합격 기준
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#687](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/687), [#690](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/690)
- 대체 대상: 없음

## 맥락

BUG #687에서 같은 로컬 작업 트리의 전체 Gradle 테스트가 `MySqlDatabaseCleaner`의 반복 `TRUNCATE` 중 MySQL
응답을 장시간 기다린 사실이 확인됐다. 이 Task는 잠금 대기 정책이나 cleaner 알고리즘을 변경하지 않고, 정상 종료하는
순차 전체 테스트에서 Docker writable layer의 `fsync` 비용이 정리 시간을 과도하게 늘리는 위험만 줄인다.

공유 Testcontainers MySQL의 데이터는 테스트 프로세스가 끝난 뒤 보존할 이유가 없다. 사용자는 제품 MySQL과 운영
Docker 설정은 그대로 두고, 테스트 컨테이너 데이터 디렉터리에만 tmpfs를 적용하는 방식을 선택했다.

## 결정 동인과 불변 조건

- MySQL 통합 테스트의 스키마 migration, 테스트별 DB 정리와 데이터 격리를 유지한다.
- 제품 MySQL, 운영 Docker 설정, `MySqlDatabaseCleaner`의 정리 알고리즘과 대기 제한은 변경하지 않는다.
- MySQL 데이터 파일은 컨테이너 종료 뒤 보존하지 않으며, tmpfs 사용량은 무한정 커지지 않게 제한한다.
- Docker 메모리 부족, swap 또는 OOM은 성능 개선으로 판정하지 않고 실패 증거로 남긴다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | Testcontainers MySQL의 `/var/lib/mysql`만 `rw,size=512m` tmpfs로 마운트 | 테스트 데이터 쓰기를 Docker 디스크 fsync 경로에서 분리하고, 데이터 디렉터리의 최대 메모리 사용량을 컨테이너당 512 MiB로 제한한다. 제품·운영 설정을 건드리지 않는다. | tmpfs는 휘발성이며 데이터셋이 512 MiB를 넘거나 Docker 메모리 압박·swap·OOM이 발생하면 테스트가 실패하거나 느려질 수 있다. | 낮음. 테스트 지원 컨테이너의 tmpfs 설정 한 줄을 제거한다. | 사용자가 채택했고, 테스트 데이터가 종료 후 불필요한 현재 제약에 맞는다. |
| 2 | Docker writable layer를 그대로 사용 | 설정·메모리 사용량 변화가 없다. | 대량 `TRUNCATE`의 디스크 fsync 병목을 그대로 남긴다. | 없음 | 확인된 로컬 순차 테스트 지연 위험을 줄이지 못한다. |
| 3 | cleaner 알고리즘 또는 잠금 대기 제한을 변경 | 정리 쿼리나 대기 실패를 직접 다룰 수 있다. | #690의 제외 범위이며, 데이터 격리·잠금 정책의 별도 검증이 필요하다. | 중간 | 현재 Task의 제한을 넘는다. |

## 결정

`SharedMySqlTestContainer`가 생성하는 Testcontainers MySQL에만 `/var/lib/mysql` → `rw,size=512m` tmpfs
마운트를 적용한다. `512m`은 데이터 디렉터리의 상한이며, MySQL 프로세스 전체 메모리 상한은 아니다. 로컬 Docker
Desktop에 확인된 가용 메모리 약 7.75 GiB 안에서 일반적인 통합 테스트 데이터셋을 수용하면서, 비정상적인 데이터
증가를 명확한 테스트 실패로 드러내기 위한 제한이다.

## 결과와 트레이드오프

### 기대 효과

- 정상 종료하는 순차 전체 테스트에서 테스트 DB 정리의 로컬 Docker 디스크 I/O 의존을 줄인다.
- 기존 컨테이너 단위 데이터 수명과 테스트 데이터 격리를 유지한다.

### 수용한 단점과 위험

- 컨테이너가 종료되면 MySQL 데이터는 복구할 수 없다. 이는 테스트 데이터에는 허용되지만 제품 DB에는 적용할 수 없다.
- 큰 fixture, Docker Desktop 메모리 축소, 다른 컨테이너와의 경쟁으로 `512m`가 부족할 수 있다.
- tmpfs는 #687의 동시 Gradle 실행에 따른 잠금 대기를 해결하거나 대기 시간을 제한하지 않는다.

## 전환과 롤백

1. 테스트 지원 `SharedMySqlTestContainer`에 tmpfs 매핑을 추가하고 데이터 디렉터리 경로·옵션을 단위 테스트로 고정한다.
2. 동일한 순차 전체 테스트 명령으로 Before/After를 비교하고, MySQL 통합 테스트와 전체 빌드를 검증한다.
3. tmpfs 마운트 실패, MySQL 초기화 실패, Docker 메모리 부족, swap 관찰 또는 OOM이 발생하면 이 변경을 되돌리고
   troubleshooting 기록에 환경·명령·증거를 남긴다.

롤백은 테스트 지원 컨테이너의 tmpfs 설정과 이를 검증하는 테스트만 제거하는 것으로 충분하며, 데이터 이관·호환성
계층·제품 설정 변경은 없다.

## 검증 방법

- `SharedMySqlTestContainer`가 `/var/lib/mysql`에 `rw,size=512m`을 전달하는지 단위 테스트로 확인한다.
- MySQL 통합 테스트에서 migration, 테스트 전후 정리와 데이터 격리가 유지되는지 확인한다.
- 같은 Docker·Java·Gradle 환경에서 `./gradlew --no-daemon clean test`를 순차 반복해 종료 코드, 경과 시간과
  `MySqlDatabaseCleaner` 관련 실패 여부를 Before/After로 비교한다.
- 전체 `./gradlew test`와 `./gradlew build`가 통과하는지 확인한다.

## 대체 조건

- 정상 테스트 데이터셋이 `/var/lib/mysql` 512 MiB 제한을 초과한다.
- 동일한 순차 조건에서 Docker 메모리 부족, swap 또는 OOM이 한 번이라도 발생한다.
- tmpfs 적용 뒤에도 동일 조건의 중앙값이 Before보다 개선되지 않거나 테스트 데이터 격리가 깨진다.
- #687의 동시 Gradle 실행 재현에서 잠금 대기가 계속돼 cleaner 또는 실행 격리 정책의 별도 결정이 필요해진다.
