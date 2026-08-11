# ADR-0092: Testcontainers 테스트를 결정적 클래스명 shard로 분할해 CI를 병렬 실행한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-10
- 결정일: 2026-08-10
- 관련 요구사항: [P0 명세](../p0-spec.md#9-테스트-및-출시-수용-기준)의 자동화 테스트 합격 기준
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: [ADR-0039](0039-run-dev-integration-ci-with-github-actions.md)의 단일 `build-and-test` 작업 구성 범위

## 맥락

ADR-0039의 단일 `build-and-test` 작업은 `./gradlew --no-daemon clean build`로 전체 테스트를 직렬 실행한다.
작업 제한 시간은 15분이며, 실제 실행이 이 제한에 도달해 `:test` 도중 취소됐다. 최근 성공 실행에서도 전체
테스트 시간은 13분 29초까지 증가해 변동을 흡수할 여유가 없다.

최근 `dev` 실행의 테스트 결과는 Testcontainers 기반 테스트 55개가 테스트 시간의 대부분을 차지함을 보였다.
반면 Testcontainers가 아닌 테스트만 병렬화하는 방안은 최대 수십 초 수준의 단축에 그쳐 15분 제한을 안정적으로
지키지 못한다.

사용자는 작업 제한 시간 상향, 외부 관리형 테스트 DB·Redis 도입, 유료 러너 사용을 허용하지 않았다. 모든 기존
테스트는 계속 병합 합격 조건으로 유지해야 한다.

## 결정 동인과 불변 조건

- `dev` 대상 PR과 `dev` 푸시에서 모든 기존 테스트를 정확히 한 번 실행한다.
- 각 CI job은 15분 제한 안에서 완료할 수 있어야 한다.
- MySQL·Redis 테스트는 현재처럼 GitHub 호스팅 runner 내부의 Testcontainers로 실행하며 외부 자격 증명·관리형
  테스트 인프라를 추가하지 않는다.
- 새 Testcontainers 테스트가 추가돼도 수동 목록 누락으로 검증에서 빠지지 않아야 한다.
- 기존 `test` task의 로컬 전체 테스트와 JaCoCo 동작은 보존한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | Testcontainers 테스트를 두 개의 결정적 shard로 나누고 일반 테스트와 병렬 CI job으로 실행 | 각 job의 wall-clock 시간을 낮추면서 모든 테스트를 유지한다. 새 테스트도 클래스명 기반으로 자동 배치된다. 외부 인프라와 비용이 없다. | job별 Gradle 초기화와 컨테이너 기동이 중복된다. 클래스별 실제 시간이 바뀌면 두 shard의 시간이 불균형해질 수 있다. | 낮음. Gradle CI task와 워크플로를 단일 task 실행으로 되돌린다. | 사용자가 선택한 15분 제한 유지와 전체 테스트 실행을 함께 만족한다. |
| 2 | Testcontainers가 아닌 테스트만 Gradle worker로 병렬화 | 한 job 안에서 설정 변경이 작고 공유 컨테이너 경합을 피한다. | 단축 가능한 구간이 작아 15분 제한 초과를 막기 부족하다. | 낮음. worker 설정을 되돌린다. | 현재 실행 시간 문제에 부적합하다. |
| 3 | CI 작업 제한 시간을 늘린다 | 구현이 가장 단순하다. | 사용자가 허용하지 않았고, 테스트 증가를 감추기만 한다. | 낮음. 제한 값을 되돌린다. | 현재 제약에 부적합하다. |

## 결정

CI는 다음 세 job을 의존성 없이 병렬 실행한다.

- `fast-test`: Testcontainers 기반이 아닌 모든 테스트와 애플리케이션 패키징을 실행한다.
- `container-test` matrix shard 1·2: Testcontainers 기반 테스트를 두 shard로 나눠 각각 실행한다.

Gradle은 `src/test/java`의 `*Test.java`에서 Testcontainers 사용 또는 공유 MySQL 테스트 지원 상속을 식별한다.
각 대상 클래스는 고정 salt를 포함한 SHA-256 클래스명 해시의 첫 바이트로 shard 1 또는 2에 결정적으로 배치한다.
동일 커밋에서는 항상 같은 shard로 실행되고, 새 대상 테스트도 정확히 하나의 shard에 포함된다.

기존 `test` task는 변경하지 않는다. 로컬 전체 테스트와 JaCoCo 리포트는 계속 기존 `test` task를 사용하며, CI 전용
`fastTest`, `containerTestShard1`, `containerTestShard2`, `ciFastCheck` task를 추가한다.

## 결과와 트레이드오프

### 기대 효과

- Testcontainers 테스트 시간을 두 runner에 분산해 CI wall-clock 시간을 약 5분 이상 줄일 수 있다.
- 각 shard는 별도 runner에서 독립 MySQL·Redis Testcontainers를 사용하므로 데이터 정리와 컨테이너 상태가
  다른 shard에 영향을 주지 않는다.
- 일반 테스트·패키징 실패와 컨테이너 테스트 실패를 별도 job에서 빠르게 식별할 수 있다.

### 수용한 단점과 위험

- runner 총 사용 시간과 테스트 결과 artifact 개수는 늘어난다. 현재 public 저장소의 표준 GitHub-hosted runner
  범위에서는 실행 비용이 발생하지 않지만, artifact 보관 용량은 계속 관찰한다.
- 결정적 해시가 실행 시간을 완벽하게 균등화하지는 않는다. 새로 추가된 장시간 테스트로 특정 shard가 15분에
  가까워질 수 있다.
- 각 container shard가 별도 Gradle JVM과 컨테이너를 시작하므로 단일 작업의 Spring 테스트 컨텍스트 캐시는 공유되지
  않는다.

## 전환과 롤백

1. Gradle에 CI 전용 테스트 분류·분할 task와 완전성 검증을 추가한다.
2. CI 워크플로의 단일 `build-and-test` job을 세 병렬 job으로 교체하고 job별 결과를 7일간 보관한다.
3. PR에서 세 job의 총 테스트 수가 기존 전체 `test`와 같고 모두 통과하는지 확인한다.

특정 shard가 15분을 넘거나 컨테이너 격리 문제가 확인되면 CI 워크플로를 `./gradlew --no-daemon clean build` 단일
작업으로 되돌리고 CI 전용 Gradle task를 제거한다. 데이터·공개 API·운영 인프라는 변경하지 않는다.

## 검증 방법

- Gradle 구성 단계에서 전체 `*Test` 클래스 집합이 `fastTest`, `containerTestShard1`, `containerTestShard2`의
  합집합과 같고, 두 container shard가 겹치지 않는지 실패로 검증한다.
- 로컬에서 각 CI 전용 Gradle task와 기존 전체 `test`를 실행해 선택된 테스트와 패키징이 통과하는지 확인한다.
- GitHub Actions PR 실행에서 세 job이 모두 15분 안에 통과하고, job별 JUnit XML·HTML 테스트 리포트가 보관되는지
  확인한다.
- Testcontainers 테스트를 새로 추가한 커밋에서 정확히 하나의 container shard가 해당 클래스를 실행하는지 확인한다.

## 대체 조건

- 최근 10회 중 하나라도 container shard가 12분을 넘거나 15분 제한으로 취소된다.
- shard 간 실행 시간 차이가 3분을 넘는 상태가 3회 연속 발생한다.
- Testcontainers 테스트가 외부 네트워크, 자격 증명 또는 전용 인프라를 요구하게 된다.
- 브랜치 보호 규칙이 단일 CI 상태 검사 이름을 요구해 병렬 job 구성과 충돌한다.
