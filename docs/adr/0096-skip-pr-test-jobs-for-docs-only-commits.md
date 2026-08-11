# ADR-0096: docs 전용 PR에서는 CI 테스트 Job을 생략한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-12
- 결정일: 2026-08-12
- 관련 요구사항: [CI 워크플로](../../.github/workflows/ci.yml), [ADR-0039](0039-run-dev-integration-ci-with-github-actions.md), [ADR-0092](0092-shard-testcontainers-ci-by-deterministic-class-name.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: [ADR-0039](0039-run-dev-integration-ci-with-github-actions.md)의 모든 `dev` 대상 PR에서 Gradle 빌드·테스트를 실행하는 범위와 [ADR-0092](0092-shard-testcontainers-ci-by-deterministic-class-name.md)의 모든 `dev` 대상 PR에서 기존 테스트를 정확히 한 번 실행하는 범위. 단, `docs:` 커밋만 포함한 PR에만 적용한다.

## 맥락

현재 CI는 `dev` 대상 Pull Request와 `dev` 푸시에서 `fast-test`, Testcontainers shard 1·2를 실행한다.
문서 변경만 포함한 PR도 같은 Gradle 패키징과 Testcontainers 테스트를 수행하므로, 코드·설정·테스트의 실행 결과에
영향을 주지 않는 변경에 runner 시간이 사용된다.

GitHub Actions의 `pull_request` 트리거는 모든 PR 커밋 제목이 `docs:`로 시작하는지 조건으로 필터링할 수 없다.
경로·브랜치·커밋 메시지로 워크플로 자체를 생략하면 필수 상태 검사가 `Pending`으로 남아 병합을 막을 수 있다.
반면 workflow 안에서 조건으로 생략한 Job은 성공 상태로 완료된다.

## 결정 동인과 불변 조건

- 모든 `dev` 대상 PR에는 완료된 GitHub Actions 상태 검사가 남아야 한다.
- PR의 모든 커밋 제목이 정확히 소문자 `docs:`로 시작할 때만 Gradle 패키징, 일반 테스트와 Testcontainers 테스트를 모두 실행하지 않는다.
- `docs:`가 아닌 커밋이 하나라도 있거나 판정에 실패하면 기존의 모든 CI 테스트를 실행한다.
- `dev` 브랜치 `push`에서는 커밋 접두사와 관계없이 기존의 모든 CI 테스트를 실행한다.
- 기존의 필수 상태 검사 이름과 Testcontainers shard 구성은 유지한다.
- 판정 Job은 Pull Request 커밋 메타데이터 읽기 외의 저장소 쓰기, 배포, 비밀값·외부 서비스 접근을 수행하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 경량 판정 Job을 실행하고 `docs:` 전용 PR의 테스트 Job만 조건으로 생략 | 모든 PR에 완료 상태를 남기며, 문서 전용 PR에서 Gradle·Testcontainers runner 사용을 없앤다. 조건으로 생략된 Job은 성공 상태가 되어 필수 검사와 호환된다. | 워크플로와 경량 판정 Job 자체는 실행된다. 커밋 조회 API 오류는 fail-closed로 전체 테스트를 실행한다. | 낮음. 판정 Job과 Job 조건을 제거하면 기존 실행으로 돌아간다. | 사용자가 선택했으며 완료 상태와 테스트 생략을 함께 만족한다. |
| 2 | `paths-ignore` 또는 GitHub의 skip 지시어로 워크플로 자체를 생략 | 설정이 짧고 runner가 전혀 실행되지 않는다. | 경로 기준은 커밋 접두사 기준과 다르며, 워크플로가 생략되면 필수 상태 검사가 `Pending`으로 남을 수 있다. | 낮음. 트리거 필터를 제거하면 된다. | 완료 상태를 남겨야 하는 현재 요구와 맞지 않는다. |

## 결정

`.github/workflows/ci.yml`은 기존 `pull_request`와 `push` 트리거를 유지한다. 모든 실행에서 먼저 경량
`ci-eligibility` Job을 실행한다.

- `pull_request` 실행에서는 `pull-requests: read` 최소 권한으로 Pull Request의 커밋 목록을 모든 페이지에서 조회한다.
- 커밋 제목(subject)이 하나 이상이고 모두 정규식 `^docs:`에 일치하면 `skip-tests=true`를 출력한다. 커밋 본문은
  판정하지 않으며, 대소문자가 다른 `Docs:`는 일치하지 않는다.
- 조회 오류, 빈 커밋 목록 또는 `docs:`가 아닌 제목이 하나라도 있으면 `skip-tests=false`로 처리한다.
- `push` 실행은 항상 `skip-tests=false`로 처리한다.

`fast-test`와 `container-test`는 판정 Job의 출력값이 `false`일 때만 실행한다. `docs:` 전용 PR에서는 두 테스트
Job 모두 조건부로 생략되어 성공 상태를 남긴다. 판정 Job 자체가 실패하지 않도록 오류를 fail-closed 출력으로 변환하고,
판정 결과와 커밋 수만 로그에 남긴다. 커밋 제목·본문 전체는 로그에 출력하지 않는다.

## 결과와 트레이드오프

### 기대 효과

- 문서 전용 PR은 Gradle 초기화, 패키징과 Testcontainers 기동 없이 완료된다.
- 모든 PR에서 워크플로와 기존 테스트 Job의 완료 상태를 확인할 수 있다.
- 코드·설정·테스트 변경 또는 판정 오류는 기존 CI 범위를 그대로 실행해 검증 누락을 막는다.
- `dev` 병합 후에는 `push` CI가 항상 전체 테스트를 실행해 통합 상태를 확인한다.

### 수용한 단점과 위험

- 문서 전용 PR도 경량 GitHub-hosted runner Job 하나를 사용한다.
- `docs:` 접두사는 커밋 메시지 규약이므로, 실제 변경 파일 종류와 불일치하는 커밋을 작성하면 CI 생략 여부도 그 규약을 따른다.
- 문서 변경이 애플리케이션의 실행 결과에 영향을 준다는 새 정책이 확정되면 이 예외는 재검토해야 한다.
- PR에 한 번이라도 비문서 커밋이 있으면 모든 테스트를 실행하므로, 이후 문서 전용 커밋을 추가해도 테스트는 생략되지 않는다.

## 전환과 롤백

1. CI 워크플로에 `ci-eligibility` Job, Pull Request 읽기 권한과 테스트 Job 조건을 추가한다.
2. `docs:` 전용 PR, 비문서 커밋 포함 PR, `dev` 푸시를 각각 실행해 Job 조건과 상태를 확인한다.
3. 판정 오류 또는 필수 상태 검사 호환성 문제가 확인되면 판정 Job과 조건을 함께 되돌려 모든 실행에서 기존 테스트 Job을 실행한다.

데이터 이관, 호환 계층, 공개 API·DB·운영 인프라 변경은 필요 없다.

## 검증 방법

- `docs:`로 시작하는 커밋만 가진 `dev` 대상 PR에서 `ci-eligibility`가 성공하고 `fast-test`와 두 `container-test` shard가 모두 성공 상태의 생략으로 표시되는지 확인한다.
- `docs:`가 아닌 제목을 하나라도 포함한 PR에서 `fast-test`와 두 `container-test` shard가 모두 실행되고 통과하는지 확인한다.
- 커밋 목록 API 호출을 실패하도록 대체한 검증에서 `skip-tests=false`가 출력되고 모든 테스트 Job이 실행되는지 확인한다.
- `dev` 푸시에서 커밋 제목과 무관하게 모든 테스트 Job이 실행되는지 확인한다.
- branch protection이 기존 테스트 Job을 필수 검사로 요구할 때, 조건부 생략된 Job이 성공으로 완료돼 PR 병합을 막지 않는지 확인한다.

## 대체 조건

- 커밋 메시지 접두사 대신 변경 파일 경로·라벨·별도 메타데이터를 판정 기준으로 삼는 정책이 확정된다.
- 문서 전용 PR의 경량 판정 Job도 사용량·대기 시간 문제를 반복적으로 일으켜 다른 완료 상태 제공 방식이 필요해진다.
- GitHub Actions가 PR의 모든 커밋 제목을 트리거 조건에서 직접 안전하게 표현하는 기능을 제공한다.
- `docs:` 접두사와 실제 변경 범위의 불일치로 테스트 누락이 재현된다.
