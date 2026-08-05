# #407 테스트 이동 중 컴파일 오류

## 요약

- 상태: 해결
- 영향 범위: 테스트 코드만 해당하며 제품 코드와 migration에는 영향이 없다.
- 발생 시각: 2026-08-04 KST
- 관련 이슈: GitHub #407

Repository 테스트의 순수 검증을 단위 테스트로 옮긴 직후 테스트 컴파일이 실패했다. 인자 순서 하나와 정적 import 하나를 바로잡은 뒤, 대상 테스트와 전체 clean build가 모두 통과했다.

## 재현과 관찰

### 기대 결과

다음 Gradle 테스트 명령이 테스트 소스를 컴파일하고 성공해야 한다.

```bash
./gradlew test --tests 'io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldTest' --tests 'io.regionevent.regioneventbackend.domain.user.repository.AppUserRepositoryTest'
```

### 실제 결과

- `CapacityHoldTest`의 helper가 `CapacityHold` 생성자에 `Instant`를 `String invalidationReason` 위치로 전달해 타입 오류가 발생했다.
- `AppUserRepositoryTest`에서 순수 생성자 검증을 옮기며 삭제한 `assertThatThrownBy` 정적 import가, 남아 있는 유일 제약 검증에도 필요해 컴파일 오류가 발생했다.

## 원인과 수정

| 원인 | 수정 | 변경 파일 |
| --- | --- | --- |
| helper의 마지막 두 인자 순서가 실제 생성자와 달랐다. | `invalidationReason` 다음에 `capacityReleasedAt`을 전달하도록 순서를 맞췄다. | `CapacityHoldTest` |
| DB 유일 제약 검증이 같은 assertion을 계속 사용했다. | 필요한 정적 import를 복원했다. | `AppUserRepositoryTest` |

## 검증

- 수정 뒤 영향을 받은 16개 단위·Repository 테스트를 실행해 통과했다.
- `/usr/bin/time -p ./gradlew --no-daemon clean build`가 0으로 종료했다.
- 전체 결과는 892 tests, failure 0, error 0, skipped 111이다. Docker CLI가 없어 Testcontainers 의존 테스트는 로컬에서 skip되며 CI에서 확인한다.

## 재발 방지

- 생성자 호출을 helper로 옮길 때는 대상 생성자의 인자 이름과 순서를 함께 확인한다.
- Repository 테스트에서 assertion을 제거하기 전, 같은 파일의 다른 DB 계약이 해당 import를 사용하는지 확인한다.

## 관련 기록

- [#407 Before/After 기록](../improvements/2026-08-04-407-repository-datajpa-contracts.md)
