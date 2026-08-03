# 아키텍처

## 1. 목적

이 문서는 로컬스탬프 백엔드가 변경에 유연하고 책임을 쉽게 추적할 수 있는 구조를 유지하기 위한
아키텍처 기준을 정의한다. 패키지는 업무 도메인을 우선하고, 클래스 간 의존성은 Controller, UseCase,
Service, Repository의 책임 경계를 넘지 않도록 관리한다.

Java 코드 작성, JPA, 예외와 테스트의 세부 기준은 [코드 컨벤션](code-convention.md)을 따른다.
유스케이스 경계의 도입·전환·롤백 기준은 [ADR-0008](adr/0008-evolve-clean-architecture-by-use-case.md)을 따르며,
Service 간 직접 의존, Repository 의존성 범위, UseCase 명명과 컴포넌트 역할은
[ADR-0042](adr/0042-prohibit-direct-service-dependencies-and-use-usecase-orchestrators.md)를 따른다.
외부 기술 경계의 출력 Port 필요성과 배치는 각각
[ADR-0008](adr/0008-evolve-clean-architecture-by-use-case.md#결정)과
[ADR-0061](adr/0061-locate-output-ports-in-owning-domain.md)을 따른다.
아직 `dev`에 없는 제공 Service와의 병렬 구현을 위한 입력 Port는
[ADR-0062](adr/0062-use-domain-input-ports-for-parallel-implementation.md)을 따른다.

## 2. 기본 구조

기본 구조는 도메인 중심의 3-Layer Architecture다. 기술 계층을 최상위 기준으로 두지 않고, 지역,
콘텐츠, 예약, 결제, 방문, 후기, 쿠폰 등 업무 도메인을 먼저 구분한다.

```text
io.regionevent.regioneventbackend
├── domain
│   ├── content
│   │   ├── controller
│   │   ├── dto
│   │   ├── entity
│   │   ├── port (필요 시)
│   │   │   ├── in  (미구현 제공 Service와 병렬 구현 시)
│   │   │   └── out (외부 기술 경계 시)
│   │   ├── repository
│   │   └── service
│   ├── reservation
│   ├── payment
│   ├── visit
│   ├── review
│   └── coupon
├── global
│   ├── config
│   ├── error
│   ├── response
│   └── security
└── infra
    ├── payment
    ├── redis
    └── storage
```

- 도메인별 코드는 `domain.<도메인>` 아래에 둔다.
- 여러 도메인이 공유하는 기술 관심사만 `global`에 둔다.
- 결제사, Redis, S3 등 외부 시스템 구현은 `infra`에 둔다.
- 외부 기술 경계가 실제로 필요한 도메인은 역할 인터페이스를 `domain.<도메인>.port.out`에 둔다.
- 아직 `dev`에 없는 제공 Service를 별도 Task와 병렬 구현해야 하는 경우, 제공 도메인은 확정된 행위 계약을
  `domain.<도메인>.port.in`에 둔다. 이미 `dev`에 있는 Service는 이 계약을 새로 만들지 않고 UseCase가
  직접 의존한다.
- `port`와 그 하위 패키지는 모든 도메인에 미리 만들지 않으며, 각 조건이 실제로 충족될 때만 생성한다.
- 특정 도메인에서만 쓰는 코드를 편의를 이유로 `global`에 올리지 않는다.
- 순환 패키지 의존성을 만들지 않는다.

도메인 패키지 안의 기본 의존 방향은 다음과 같다.

```text
Controller → Service A
                       ├── Repository A (같은 도메인 또는 Aggregate, 최대 1)
                       ├── Entity / Value Object
                       └── 기술 협력자(PasswordEncoder, Clock) 또는 외부 연동 Port.out(TokenProvider)
```

여기서 최대 하나로 제한하는 대상은 Service의 Repository 인터페이스 의존성이다. Service의 모든 의존성을
하나로 제한하는 규칙은 아니다. Service는 자신의 단일 도메인 책임을 수행하는 데 필요한 정책 객체와 기술
협력자를 추가로 의존할 수 있다.

Repository 의존성은 자신과 같은 도메인 또는 Aggregate에 속해야 한다. 예를 들어 `OrderService`가
의존할 수 있는 Repository 인터페이스는 `OrderRepository` 하나이며, `UserRepository`처럼 다른 도메인의
Repository를 직접 의존해서는 안 된다. 여러 Repository를 함께 조정해야 하는 경우에도 Service에 Repository를
추가하지 않는다. 대신 Repository별 Service를 분리하고, `UseCase`를 진입점으로 추가한다.

```text
Controller → UseCase
                 ├── Service A → Repository A
                 └── Service B → Repository B
```

`UseCase`는 여러 Service를 의존할 수 있지만, Repository를 직접 의존하지 않는다.
Service는 다른 Service를 직접 의존하지 않는다.

복합 UseCase가 이미 `dev`에 있는 Service를 사용하면 해당 Service를 직접 의존한다. 제공 Service가 아직
`dev`에 없고, 제공·소비 Task를 병렬로 구현해야 하면 제공 도메인이 먼저 확정한 `port.in` 행위 계약을
UseCase가 의존할 수 있다. 이후 제공 Service가 그 계약을 구현한다. 이 예외는 병렬 Task 경계에만 적용하며,
기존 Service를 인터페이스로 일괄 전환하거나 Service 간 직접 의존을 허용하는 규칙이 아니다.

## 3. 계층별 책임

### 3.1 Controller

Controller는 HTTP 입출력 경계다. 입력 검증, 인증 정보 전달, 요청 DTO와 응답 DTO 변환만 담당하며,
비즈니스 흐름과 트랜잭션을 직접 처리하지 않는다.

Controller 하나는 다음 둘 중 하나의 진입점 하나에만 의존한다.

- 단일 Repository로 완결되는 단순 흐름에서는 하나의 Service
- 여러 Service의 조정이 필요한 복합 흐름에서는 하나의 UseCase

### 3.2 Service

Service는 자신이 담당한 도메인 또는 Aggregate의 Repository 인터페이스를 최대 하나만 의존한다. 이 제한은
Repository 의존성에만 적용하며, Service의 전체 의존성 수를 제한하지 않는다. 하나의 Service에 둘 이상의
Repository 인터페이스, 다른 도메인의 Repository 또는 다른 Service를 주입하지 않는다.

Service는 엔티티와 값 객체의 도메인 규칙을 호출하고, 자신의 단일 도메인 책임을 수행하는 데 필요한
`Policy`, `Calculator`, `Validator`, `TokenProvider`, `PasswordEncoder`, `Clock` 같은
협력자를 추가로 의존할 수 있다. 이 협력자는 Repository 인터페이스나 다른 Service를 감싼 우회 경로가
아니어야 하며, 여러 Service의 실행 순서·권한·트랜잭션을 조정해서는 안 된다.

Service는 HTTP 요청·응답 타입, 다른 도메인의 Repository, 구체적인 외부 시스템 구현에 직접 의존하지 않는다.
외부 시스템과 통신하거나 교체 가능성이 중요한 기술 경계는 `PaymentGateway`, `TokenProvider`처럼 역할
인터페이스로 표현하고 구현체를 기술 경계 뒤에 둔다. 단순한 내부 협력자까지 인터페이스로 만들 필요는 없다.

```text
ReservationService → ReservationRepository
PaymentService     → PaymentRepository

AuthService → UserRepository
            → TokenProvider
            → PasswordEncoder

JwtTokenProvider ──implements── TokenProvider
```

다음 의존성은 Repository 수가 하나여도 도메인 책임이 맞지 않으므로 허용하지 않는다.

```text
OrderService → UserRepository
```

다른 Service의 기능을 함께 실행해야 하면 해당 Service에 의존하지 않고 UseCase에서 조정한다.

```text
OrderService → UserService
```

### 3.3 UseCase

하나의 업무 흐름에서 둘 이상의 Repository 또는 Service가 필요하면, 그 Repository를 각각 담당하는 Service로
책임을 분리하고 해당 Service들을 조정하는 UseCase를 구현한다. UseCase는 유즈케이스 파사드로서 Controller에
하나의 단순한 진입점을 제공한다. UseCase가 여러 Repository를 직접 주입받거나, 특정 Service에 Repository를
추가 주입해서는 안 된다.

UseCase는 실행 순서, 필요한 권한 범위, 여러 Service의 협력과 트랜잭션 경계를 책임진다. 도메인 규칙을
중복 구현하거나 단순히 한 Service를 전달 호출하는 계층으로 만들지 않는다. 실질적인 유스케이스 조정이
없다면 UseCase를 추가하지 않고 Controller가 해당 Service를 직접 의존한다.

Repository와 Service를 모두 의존하지 않는 순수 계산·검증 로직은 UseCase나 Service로 이름 짓지 않고
`<도메인>Policy`, `<도메인>Calculator`, `<도메인>Validator`처럼 역할을 드러내는 이름을 사용한다.

```text
CompleteReservationUseCase
├── ReservationService → ReservationRepository
└── PaymentService     → PaymentRepository
```

### 3.4 Repository

Repository는 영속성 조회와 저장에 집중한다. Service는 Repository의 인터페이스에 의존하며, JPA,
Redis 또는 외부 시스템의 구체 구현은 `infra`와 같은 기술 경계 뒤로 격리한다.

### 3.5 Component와 Adapter의 역할

`@Component`와 `@Bean`은 Spring Bean 등록 방식일 뿐, 아키텍처 계층이나 의존 방향을 뜻하지 않는다.
클래스의 역할에 따라 다음 방향을 적용한다.

| 역할 | 예 | 의존 방향 |
| --- | --- | --- |
| 도메인·기술 협력자 | `ReservationPolicy`, `JwtTokenProvider`, `PasswordEncoder`, `Clock` | Service 또는 UseCase가 사용한다. 협력자는 Service나 UseCase를 다시 참조하지 않으며, 업무 판단에 필요한 사용자 식별자·클레임 등 도메인 데이터는 인자로 받는다. |
| 입력 컴포넌트 | Scheduler, 메시지 Listener, 업무 흐름을 시작하는 Filter | Controller와 같은 입력 경계다. 단순 흐름에서는 Service 하나, 복합 흐름에서는 UseCase 하나를 호출한다. |
| 외부 연동 Adapter | `PortOnePaymentAdapter`, S3 Adapter | 역할 인터페이스를 구현해 외부 시스템과 통신한다. 애플리케이션은 구현체가 아니라 역할 인터페이스에 의존한다. |

외부 연동 역할 인터페이스는 그 업무 책임을 소유한 `domain.<도메인>.port.out`에 둔다. 예를 들어
`PaymentGateway`는 `domain.payment.port.out`에 두고, `PortOnePaymentAdapter`는 `infra.payment`에서 이를
구현한다. 단일 Service 또는 복합 UseCase는 이 역할 인터페이스에 의존할 수 있지만, Port가 Repository나
Service를 감싸 여러 도메인 흐름을 우회해서는 안 된다.

병렬 구현이 필요한 경우의 입력 Port는 제공 도메인이 `domain.<도메인>.port.in`에 둔다. 예를 들어
`AuthorizePayment`를 `domain.payment.port.in`에 먼저 확정하면, 예약 확정 UseCase는 이를 의존해 구현하고
결제 Service는 이후 이 계약을 구현할 수 있다. 이 계약은 제공 Service가 아직 `dev`에 없고 제공·소비 Task의
범위와 행위가 확정된 경우에만 만든다. 기존 Service, 내부 정책 객체, 단순 기술 협력자, 전달 전용 계층 또는
테스트 편의만으로 `port.in`을 만들지 않는다.

따라서 `JwtTokenProvider`가 사용자 정보를 위해 `UserService`를 직접 호출해서는 안 된다. 필요한 사용자 식별자나
클레임은 `AuthService` 또는 UseCase가 준비해 인자로 전달한다. 외부 콜백처럼 업무 흐름을 시작하는 경우에는
기술 협력자의 역의존으로 처리하지 않고 입력 컴포넌트로 분류한다.

## 4. 의존성 규칙

### 4.1 단순 유스케이스

단일 Repository로 처리할 수 있는 유스케이스는 Controller, Service, Repository의 세 핵심 계층으로 구성한다.
Service는 자신의 책임을 지원하는 기술 협력자를 추가로 사용할 수 있지만, 이 때문에 UseCase를 추가하지 않는다.

```text
ReservationController → ReservationService → ReservationRepository
```

입력 컴포넌트도 Controller와 같은 규칙을 적용한다.

```text
입력 경계 (Controller / Scheduler / MessageListener)
├── Service → Repository                         (단순 흐름)
└── UseCase
    ├── Service A → Repository A
    └── Service B → Repository B                 (복합 흐름)
```

### 4.2 복합 유스케이스

여러 Repository 또는 Service의 협력이 필요한 경우, 하나의 Service가 여러 Repository를 직접 의존하게 만들지
않는다. Repository별 책임을 Service로 분리하고, UseCase가 여러 Service를 조정한다. Service가 다른 Service를
직접 호출해 협력해서는 안 된다.

```text
ReservationController → CompleteReservationUseCase
                              ├── ReservationService → ReservationRepository
                              └── PaymentService     → PaymentRepository
```

예를 들어 주문 생성에 사용자 조회와 주문 저장이 함께 필요하면 `OrderService → UserRepository`로 연결하지
않는다. `CreateOrderUseCase`가 `UserService → UserRepository`와 `OrderService → OrderRepository`를 조정한다.

다음과 같은 의존성은 허용하지 않는다.

```text
ReservationController → ReservationService
                              ├── ReservationRepository
                              └── PaymentRepository

ReservationController → ReservationService → PaymentService
```

### 4.3 이름과 배치

- Controller는 `<도메인>Controller`로 이름 짓고 `controller` 패키지에 둔다.
- Service는 `<도메인>Service`로 이름 짓고 `service` 패키지에 둔다.
- 복합 흐름의 UseCase는 수행하는 행위가 드러나도록 `<행위>UseCase`로 이름 짓고, 관련 도메인의
  `service` 패키지 또는 유스케이스 전용 하위 패키지에 둔다.
- Repository와 Service를 조정하지 않는 순수 로직은 `<도메인>Policy`, `<도메인>Calculator`,
  `<도메인>Validator`처럼 역할을 드러내는 이름을 사용한다.
- Repository는 `<도메인>Repository`로 이름 짓고 `repository` 패키지에 둔다.
- 외부 기술 역할 인터페이스는 `<역할>` 이름으로 해당 도메인의 `port.out` 패키지에 둔다. `Port` 접미사를
  강제하지 않으며 `PaymentGateway`, `TokenProvider`처럼 책임을 드러내는 이름을 사용한다. 구현체는
  `infra.<기술>` 패키지에 둔다.
- 병렬 구현 계약은 제공 도메인의 `port.in` 패키지에 `<행위>` 이름으로 둔다. `Interface`나 `Impl` 접미사를
  사용하지 않으며 `AuthorizePayment`처럼 제공할 행위를 드러낸다. 제공 Service는 역할이 드러나는 이름으로
  이 계약을 구현한다.
- UseCase 전용 패키지가 기본 패키지 계층도와 달라지는 경우에는 코드 변경 전에
  이 문서의 패키지 계층도를 먼저 갱신하고 [ADR-0008](adr/0008-evolve-clean-architecture-by-use-case.md)의
  전환 절차에 따라 구조를 확정한다.

## 5. 유지보수 점검 기준

새 기능을 추가하거나 기존 코드를 변경할 때 다음을 확인한다.

- Controller가 하나의 Service 또는 하나의 UseCase만 직접 의존하는가?
- Service의 Repository 인터페이스 의존성이 자신의 도메인 또는 Aggregate에 속한 최대 하나인가?
- Service의 추가 협력자가 단일 도메인 책임을 지원하며 Repository 인터페이스나 다른 Service를 우회하지 않는가?
- Service가 다른 Service를 직접 의존하지 않는가?
- 여러 Repository 또는 Service의 협력이 필요할 때 책임별 Service와 이를 조정하는 UseCase로 분리했는가?
- UseCase가 실질적인 실행 순서, 트랜잭션 또는 협력자 조정을 수행하는가?
- `port.in`이 아직 `dev`에 없는 제공 Service와의 확정된 병렬 Task 경계에만 있고, 기존 Service나 테스트
  편의를 위한 인터페이스가 아닌가?
- 기술 협력자와 외부 연동 Adapter가 Service·UseCase를 역으로 참조하지 않고, 업무 흐름을 시작하는 컴포넌트가
  하나의 Service 또는 UseCase를 진입점으로 사용하는가?
- Controller와 Service에 전달 전용 계층, 비즈니스 규칙 중복, 순환 의존성이 없는가?
- 패키지 구조와 계층별 세부 구현 규칙이 [코드 컨벤션](code-convention.md)을 따르는가?
