# 후기 수정-삭제 경합 상태 판정 조사

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 미재현 |
| 영향 | 삭제가 먼저 커밋된 후기 수정 경합에서 `NOT_FOUND` 대신 `FORBIDDEN`을 반환할 수 있다. |
| 최초 확인 시각 | 2026-08-05 KST |
| 관련 테스트 | `CreateVisitReviewUseCaseMySqlIntegrationTest.update_whenDeletionCommitsFirst_returnsNotFoundWithoutMutation` |
| revision / 브랜치 | `96b017e`, `feature/416-reject-pending-session` |
| 환경 | Java 21, MySQL 8 Testcontainers |

## 관찰 결과

- 전체 `./gradlew.bat build`에서 1,119개 중 1개가 실패했다. 기대값은 `NOT_FOUND`, 실제값은 `FORBIDDEN`이었다.
- 실패한 테스트만 MySQL Testcontainers 환경에서 단독으로 1회, 이어서 3회 반복 실행했으며 모두 통과했다.
- 전체 `./gradlew.bat build` 재실행은 1,119개 테스트 모두 통과했다.

## 재현 절차

1. 삭제 트랜잭션이 후기 상태를 `DELETED`로 변경한 뒤 잠금을 유지한다.
2. 다른 트랜잭션에서 후기 수정을 시작한다.
3. 삭제 트랜잭션을 커밋하고 수정 결과를 확인한다.

## 가설과 검증

### 가설: 조건부 갱신 뒤의 비잠금 조회가 이전 상태를 관찰한다

- 근거: `ReviewService.updatePublishedByAuthorWithinThirtyDays`는 조건부 갱신이 0건이면 비잠금 조회로 상태를 다시 판정한다.
- 검증: 동일 MySQL 테스트를 독립 실행으로 4회 수행했다.
- 결과: 모두 통과하여 최초 실패를 재현하지 못했다.

## 결론

- 근본 원인은 미확정이다. 단일 실패만으로 MySQL 반복 읽기 스냅샷 문제를 확정하지 않는다.
- 현재 기능 변경과 직접 연결된 증거가 없어 코드 수정은 하지 않았다.

## 검증 결과

| 테스트 | 결과 | 비고 |
| --- | --- | --- |
| 전체 `./gradlew.bat build` | 실패 | 1,119개 중 1개 실패 |
| 전체 `./gradlew.bat build` 재실행 | 통과 | 1,119개 테스트 통과 |
| 실패 테스트 단독 실행 | 통과 | 1회 |
| 실패 테스트 반복 실행 | 통과 | 3회 |

## 후속 작업

- 전체 빌드에서 다시 발생하면 MySQL 격리 수준과 쿼리 실행 순서를 추가로 수집한다.

## 관련 자료

- `src/main/java/io/regionevent/regioneventbackend/domain/review/service/ReviewService.java`
- `src/test/java/io/regionevent/regioneventbackend/domain/review/service/CreateVisitReviewUseCaseMySqlIntegrationTest.java`
