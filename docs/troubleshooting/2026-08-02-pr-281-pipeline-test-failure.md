# PR #281 파이프라인 테스트 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #281의 GitHub Actions 테스트 체크가 통과하지 못함 |
| 최초 확인 시각·시간대 | 2026-08-02 23:55:11 KST |
| 관련 요구사항·이슈 | #215, PR #281 |
| revision·브랜치 | `1f31d86a2a40edb3df7ff9904c0f2e30e553e7c1`, `feature/215-approve-content-revision` |
| 환경·프로필 | 로컬 OpenJDK 21.0.7, CI Amazon Corretto 21.0.12·MySQL 8.0.42 Testcontainers |

## 기대 결과와 실제 결과

### 기대 결과

PR #281의 GitHub Actions 테스트 체크가 성공한다.

### 실제 결과

CI의 `빌드 및 테스트` 체크에서 560개 테스트 중 1개가 실패했다. 실패 테스트는
`ContentRevisionApprovalUseCaseMySqlTest.승인과_자동_공개가_경합하면_원본_상태와_수정본을_함께_보호한다`이며,
승인 경로에서 `ObjectOptimisticLockingFailureException`이 발생했다.

## 재현 절차

### 선행 조건

- GitHub CLI 인증
- PR #281 조회 권한

### 명령·요청·입력

1. PR #281의 체크 상태를 조회한다.
2. 실패한 GitHub Actions 실행 로그를 수집한다.
3. 실패한 테스트를 동일한 조건으로 로컬에서 재현한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: GitHub Actions 종료 코드 1

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- 실패 체크: `빌드 및 테스트`
- Actions run: `30752644876`
- 실패 위치: `ContentRevisionApprovalUseCaseMySqlTest.java:209`, 승인 호출 `:233`
- 예외 체인: `ExecutionException` → `ObjectOptimisticLockingFailureException` →
  `StaleObjectStateException` → `StaleStateException`
- CI 요약: 560 tests completed, 1 failed

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-02 23:55 KST | 관찰 | PR #281 파이프라인 테스트 실패 보고 | Actions 체크와 로그에서 구체적인 실패를 확인한다. | 조사 시작 | 대기 |
| 2026-08-03 00:00 KST | 검증 | PR #281 Actions 실패 로그 확인 | 실패 테스트와 예외 체인을 특정한다. | 자동 공개 경합 테스트에서 낙관적 락 예외 확인 | 채택 |
| 2026-08-03 00:02 KST | 검증 | 수정본 잠금이 원본 행도 직렬화하는지 코드 대조 | 원본 행이 명시적으로 잠기지 않으면 자동 공개의 조건부 갱신과 승인 flush가 경합한다. | 수정본 Repository 잠금만 있고 원본 전용 잠금 호출이 없음 | 채택 |
| 2026-08-03 00:03 KST | 변경 | 원본 `Content` 선잠금 후 수정본 잠금 | 자동 공개와 승인이 원본 행에서 직렬화되고 패자는 상태·버전 검증으로 거절된다. | 잠금 순서와 Repository 원본 ID 조회를 구현 | 채택 |
| 2026-08-03 00:07 KST | 검증 | CI와 같은 `clean build` 실행 | MySQL 경합 테스트를 포함한 전체 빌드가 성공한다. | 3분 23초, BUILD SUCCESSFUL | 채택 |

## 가설과 검증

### 가설 1: CI 환경에서만 드러나는 테스트 또는 데이터베이스 차이

- 근거: 로컬 `./gradlew test`와 `./gradlew build`는 PR 생성 전에 성공했다.
- 참일 때의 예측: Actions 로그에 CI 전용 환경 또는 MySQL Testcontainers 테스트의 실패가 나타난다.
- 반증 조건: 로컬과 동일한 H2 기반 테스트나 컴파일 단계에서 실패한다.
- 검증 방법: 실패 체크와 job 로그를 확인하고 해당 테스트를 로컬에서 단독 실행한다.
- 결과: CI에서 MySQL 경합 테스트가 실행되며 승인 트랜잭션 flush 시 원본 `Content` 버전 불일치가 발생했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 공개 전 수정본 승인과 원본 콘텐츠 자동 공개 조건부 갱신이 동시에 실행됨
- 결함이 있는 코드·설정·데이터·계약: 승인 흐름은 수정본을 비관적 잠금하지만 원본 `Content` 행의 잠금 순서를 명시적으로 확보하지 않음
- 증상으로 이어진 메커니즘: 자동 공개가 원본 버전을 먼저 증가시키면 승인 flush의 버전 조건이 실패해
  `ObjectOptimisticLockingFailureException`이 트랜잭션 프록시 밖으로 전달됨
- 기존 방어가 막지 못한 이유: 사전 기준 버전 검증 이후 원본 행이 바뀔 수 있고, 테스트 도우미는 계약상
  `CONTENT_STATE_CONFLICT`만 경합 패배로 처리하므로 원시 낙관적 락 예외를 실패로 감지함
- 결론의 증거: CI 실패 테스트·예외 체인과 `ContentRevisionRepository.findReviewTargetByIdForUpdate`의 수정본 중심 잠금 구현

## 해결 또는 완화

- 선택한 방법: 승인 전에 원본 `Content` 행을 먼저 명시적으로 비관적 잠금하고 최신 상태를 읽은 뒤 수정본을 잠그도록 순서를 고정함
- 변경 파일:
  - `ContentRevisionRepository.java`
  - `ContentRevisionService.java`
  - `ApproveContentRevisionUseCase.java`
  - `ContentRevisionRepositoryTest.java`
  - `ApproveContentRevisionUseCaseTest.java`
- 정책·계약 변경 여부: 없음. 기존 `CONTENT_STATE_CONFLICT`와 선착순 종결 계약을 구현함

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 승인·자동 공개 MySQL 경합 | CI에서 `ObjectOptimisticLockingFailureException` | 같은 MySQL 8.0.42 테스트 반복 성공 | 해결 |
| 전체 CI 빌드 명령 | 560개 중 1개 실패 | `./gradlew --no-daemon clean build` 성공 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 승인 관련 단위·Repository·Controller·MySQL 테스트 | 성공 | MySQL 경합 3건 실제 실행, 스킵 없음 |
| 승인·자동 공개 MySQL 경합 테스트 재실행 | 성공 | `--rerun-tasks` 적용 |
| `./gradlew --no-daemon clean build` | 성공 | CI와 동일한 Gradle 명령, 3분 23초 |
| `git diff --check` | 성공 | 공백 오류 없음 |

## 재발 방지와 문서 반영

원본과 수정본을 함께 변경하거나 판정하는 경합 경로에서 원본 선잠금 순서를 단위 테스트로 고정했다.
MySQL Testcontainers 경합 테스트가 실제 행 잠금과 선착순 종결 결과를 검증한다.

## 잔여 위험과 후속 작업

로컬 MySQL 8.0.42와 CI 동일 빌드 명령은 통과했다. PR 브랜치 push 뒤 GitHub Actions 환경에서 최종 재확인한다.

## 관련 자료

- PR #281
- 이슈 #215
- GitHub Actions run `30752644876`
