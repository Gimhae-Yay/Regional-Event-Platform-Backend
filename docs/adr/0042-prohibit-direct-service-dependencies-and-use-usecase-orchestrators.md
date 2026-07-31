# ADR-0042: Service 간 직접 의존을 금지하고 UseCase로 협력을 조정

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-07-30
- 결정일: 2026-07-30
- 관련 요구사항: [기술 구성과 설계 원칙](../local-stamp-platform-prd.md#10-기술-구성과-설계-원칙)
- 관련 단계: 단계 0. 정책·설계 확정
- 관련 이슈: 없음
- 대체 대상: [ADR-0008](0008-evolve-clean-architecture-by-use-case.md#결정)의 Service 간 협력과 UseCase 도입 기준

## 맥락

[아키텍처](../ARCHITECTURE.md)는 Service의 Repository 인터페이스 의존성을 자신의 도메인 또는 Aggregate에
속한 최대 하나로 제한한다. 그러나 이 제한은 Service의 모든 의존성을 하나로 제한하는 규칙이 아니다.
단일 도메인 책임을 수행하는 Service도 토큰 발급, 암호화, 시간 계산처럼 `TokenProvider`, `PasswordEncoder`,
`Clock` 같은 지원 협력자가 필요할 수 있다.

반대로 Service가 다른 Service를 직접 의존하거나, 지원 협력자가 Repository 인터페이스 또는 다른 Service를
감싸면 `OrderService → UserService` 또는 숨은 다중 Repository 흐름처럼 여러 도메인의 실행 순서와
트랜잭션·권한 흐름이 Service 안에 감춰질 수 있다. `@Component`와 `@Bean`은 Spring Bean 등록 방식일 뿐
역할을 뜻하지 않으므로, 기술 협력자·외부 연동 Adapter·입력 컴포넌트도 같은 의존 규칙으로 다룰 수 없다.

ADR-0008은 협력자 수만으로 Application Service를 도입하지 않도록 했지만, 현재 팀은 Service의 책임과
의존 방향을 쉽게 검토할 수 있도록 Service 간 직접 의존을 금지하고, 여러 Service 협력은 명시적인
UseCase에서 조정하기로 했다. 이는 ADR-0008의 점진적 전환 전략 전체를 대체하지 않고, Service 간 협력과
UseCase 도입 기준만 대체한다.

## 결정 동인과 불변 조건

- Service의 Repository 인터페이스 의존성은 자신의 도메인 또는 Aggregate에 속한 최대 하나로 제한한다.
- Service 간 직접 의존과 다른 도메인의 Repository 직접 의존은 금지한다.
- 단일 도메인 책임을 지원하는 도메인 정책 객체와 기술 협력자는 허용하되, Repository 제한이나 다중 도메인
  조정을 숨기는 우회 경로가 되어서는 안 된다.
- Controller와 입력 컴포넌트는 단순 흐름에서는 Service 하나, 복합 흐름에서는 UseCase 하나를 진입점으로 사용한다.
- UseCase는 여러 Service의 실행 순서, 권한, 트랜잭션과 결과 조정을 책임지며 Repository를 직접 의존하지 않는다.
- 기존의 권한, 정합성, 멱등성, Outbox 원자성과 공개 API 동작은 유지한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | Service 간 직접 의존을 금지하고, Repository 의존성만 제한하며 역할별 협력자를 허용 | 단일 도메인 Service가 필요한 기술 협력자를 사용하면서도 의존 방향과 트랜잭션 소유자를 명확히 검토할 수 있다. | 협력자가 단순 지원인지 숨은 조정자인지 코드 리뷰에서 판단해야 한다. | 낮음~중간. 숨은 조정자를 UseCase 또는 역할 인터페이스로 분리할 수 있다. | 유지보수성과 코드 리뷰의 예측 가능성을 우선하는 현재 단계에 적합하다. |
| 2 | 같은 도메인 안의 Service 간 직접 의존을 허용 | 클래스 수와 생성 비용이 작다. | 간접 의존으로 협력 범위가 숨고, 다른 도메인 Service 참조와 순환 의존으로 확대되기 쉽다. | 낮음. 직접 주입을 허용하면 된다. | 단기 구현에는 편하지만 선택한 단일 책임 기준에 맞지 않는다. |
| 3 | Service의 모든 의존성을 Repository 하나로 제한 | 생성자 형태가 단순하고 위반 판정이 쉽다. | JWT, 암호화, 시간 같은 필요한 협력자를 Controller·UseCase로 밀어내거나 전달 전용 계층을 만든다. | 중간. 잘못 배치한 책임을 다시 Service로 옮겨야 한다. | 단일 책임을 지나치게 좁게 해석하므로 부적합하다. |
| 4 | 모든 `@Component`의 Service 직접 참조를 허용 | Spring Bean 사이 호출을 자유롭게 구현할 수 있다. | 기술 협력자와 입력 컴포넌트의 역할이 섞여 숨은 유스케이스와 순환 의존이 생긴다. | 중간. 호출 경로를 재분류하고 UseCase를 도입해야 한다. | 의존 방향을 명확히 해야 하는 현재 단계에 부적합하다. |

## 결정

Service의 Repository 인터페이스 의존성은 자신의 도메인 또는 Aggregate에 속한 최대 하나로 제한한다. 이
제한은 Service의 전체 의존성 수를 제한하지 않는다.

Service는 같은 단일 도메인 책임을 지원하는 `Policy`, `Calculator`, `Validator`, `TokenProvider`,
`PasswordEncoder`, `Clock` 같은 협력자를 추가로 의존할 수 있다. 협력자가 Repository 인터페이스나 다른
Service를 감싸거나 여러 Service의 실행 순서·권한·트랜잭션을 조정하면 허용된 협력자가 아니라 UseCase로
분리해야 할 복합 유스케이스로 본다.

Service는 다른 Service와 다른 도메인의 Repository를 직접 의존하지 않는다. 외부 시스템과 통신하거나 교체
가능성이 중요한 기술 경계는 `PaymentGateway`, `TokenProvider`처럼 역할 인터페이스로 표현하고 구현체를 기술
경계 뒤에 둔다. 단순한 내부 협력자까지 인터페이스로 강제하지 않는다.

`@Component` 또는 `@Bean`은 의존 방향을 결정하지 않는다. 도메인·기술 협력자와 외부 연동 Adapter는 Service나
UseCase를 역으로 참조하지 않는다. Scheduler, 메시지 Listener, 업무 흐름을 시작하는 Filter 같은 입력
컴포넌트는 Controller와 같은 입력 경계로서 단순 흐름에는 Service 하나, 복합 흐름에는 UseCase 하나를
호출한다. 예를 들어 `JwtTokenProvider`는 `AuthService`가 준비한 사용자 식별자나 클레임을 인자로 받아
토큰을 발급·검증하며, 사용자 조회를 위해 `UserService`를 직접 호출하지 않는다.

둘 이상의 Service를 실질적으로 조정해 하나의 업무 흐름을 완성하는 클래스는 `<행위>UseCase`로 명명한다.
UseCase는 Repository를 직접 의존하지 않고 실행 순서, 권한 범위, 트랜잭션 경계와 결과 조정을 책임진다.
단일 Service를 전달 호출하는 클래스는 UseCase로 만들지 않는다.

Repository와 Service를 모두 의존하지 않는 순수 계산·검증 로직은 `<도메인>Policy`, `<도메인>Calculator`,
`<도메인>Validator`처럼 역할을 드러내는 이름을 사용한다.

## 결과와 트레이드오프

### 기대 효과

- Service가 JWT 발급, 암호화, 시간 계산처럼 자신의 책임을 지원하는 협력자를 자연스럽게 사용할 수 있다.
- Service의 생성자와 협력자 역할을 보고 담당 도메인, 저장소 책임, 유스케이스 조정 여부를 파악할 수 있다.
- 다중 도메인 흐름의 진입점, 트랜잭션 경계와 테스트 범위가 UseCase로 드러난다.
- 다른 Service 재사용이나 지원 컴포넌트를 명분으로 한 숨은 도메인 간 결합과 순환 의존을 예방한다.

### 수용한 단점과 위험

- 복합 흐름마다 UseCase가 추가돼 클래스 수와 탐색 비용이 늘어난다.
- Service 생성자의 의존성 수만으로 책임이 적절한지 판단할 수 없고, 협력자의 역할을 함께 검토해야 한다.
- `TokenProvider` 같은 협력자가 Repository 접근이나 도메인 조정을 감추면 규칙을 우회할 수 있다.
- 입력 컴포넌트가 Service 또는 UseCase를 호출하는 경우 Controller 경로와 정책·권한 검증이 달라질 위험이 있다.

## 전환과 롤백

현재 코드에는 Service 간 직접 의존이 없어 데이터 이관·호환 계층·중단은 필요하지 않다. 신규 구현은
`Controller → Service → Repository` 또는 `Controller → UseCase → Service → Repository` 구조를 사용하고,
Service의 지원 협력자는 같은 단일 도메인 책임에만 둔다.

기존 또는 향후 Service 간 직접 의존, 협력자를 통한 Repository 우회가 발견되면 다음 순서로 전환한다.

1. 정상·경계·실패·권한·중복 요청 결과를 테스트로 고정한다.
2. 각 Repository 책임을 해당 Service에 남기고, 실행 순서와 트랜잭션 조정을 UseCase로 이동한다.
3. 기술 협력자가 Service를 직접 참조하면 필요한 식별자·클레임·값을 호출 인자로 바꾸고, 여러 Service 조정은 UseCase로 이동한다.
4. Controller 또는 다른 입력 Adapter가 Service 또는 UseCase 하나를 호출하도록 정리한다.
5. Service 간 직접 주입과 협력자를 통한 Repository 우회를 제거하고 의존성·트랜잭션·공개 API 회귀 테스트를 실행한다.

UseCase가 실질적인 조정 없이 단일 Service를 전달 호출하는 것으로 확인되면, 해당 UseCase를 제거하고
Controller 또는 입력 컴포넌트가 원래 Service를 직접 의존하도록 되돌린다. 이때도 Service 간 직접 의존은
다시 도입하지 않는다.

## 검증 방법

- 코드 리뷰에서 Service 생성자와 필드의 Repository가 자신의 도메인 또는 Aggregate에 속한 최대 하나인지 확인한다.
- Service가 다른 Service나 다른 도메인의 Repository를 직접 의존하지 않는지 확인한다.
- 추가 협력자가 Repository 인터페이스나 다른 Service를 감싸거나 여러 Service의 실행 순서와 트랜잭션을
  조정하지 않는지 코드 리뷰와 단위 테스트로 확인한다.
- 기술 협력자와 외부 연동 Adapter가 Service·UseCase를 역으로 참조하지 않고, 입력 컴포넌트가 하나의 Service
  또는 UseCase를 통해 업무 흐름을 시작하는지 확인한다.
- 복합 UseCase는 하나의 트랜잭션 경계에서 정상·경계·실패·권한·중복 요청 결과를 유지하는지 검증한다.
- 순수 계산·검증 로직이 Service 또는 UseCase 접미사를 사용하지 않는지 확인한다.
- 리팩터링 전후에 권한, 정합성, 멱등성, Outbox 원자성과 공개 API 결과가 유지되는지 검증한다.

## 대체 조건

- 여러 UseCase가 반복적으로 단일 Service 전달만 하고, 책임 분리·테스트 격리·변경 영향 축소 효과가 확인되지 않는다.
- 역할 기반 분류만으로 기술 협력자와 유스케이스 조정자의 경계를 일관되게 리뷰할 수 없다는 반복 가능한 증거가 생긴다.
- Service 간 직접 협력이 있어야만 도메인 불변식·트랜잭션·장애 복구를 더 명확하고 안전하게 표현할 수 있다는
  반복 가능한 증거가 생긴다.
- 모듈 분리나 독립 배포로 인해 현재의 패키지 수준 Service·UseCase 경계가 더 이상 적합하지 않다.
