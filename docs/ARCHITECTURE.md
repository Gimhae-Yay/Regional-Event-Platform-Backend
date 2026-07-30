# 아키텍처

## 1. 목적

이 문서는 로컬스탬프 백엔드가 변경에 유연하고 책임을 쉽게 추적할 수 있는 구조를 유지하기 위한
아키텍처 기준을 정의한다. 패키지는 업무 도메인을 우선하고, 클래스 간 의존성은 Controller, UseCase,
Service, Repository의 책임 경계를 넘지 않도록 관리한다.

패키지 구성과 코드 작성의 세부 기준은 [코드 컨벤션](code-convention.md)을 단일 출처로 삼는다.
유스케이스 경계의 도입·전환·롤백 기준은 [ADR-0008](adr/0008-evolve-clean-architecture-by-use-case.md)을
따른다.

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
- 특정 도메인에서만 쓰는 코드를 편의를 이유로 `global`에 올리지 않는다.
- 순환 패키지 의존성을 만들지 않는다.

도메인 패키지 안의 기본 의존 방향은 다음과 같다.

```text
Controller → Service A → Repository A
                       ↓
                     Entity
```

위 구조에서 Service 하나는 자신과 같은 도메인 책임을 가진 Repository 하나에만 의존한다. 예를 들어
`OrderService`는 `OrderRepository`에만 의존할 수 있으며, `UserRepository`처럼 다른 도메인의 Repository를
직접 의존해서는 안 된다. 여러 Repository를 함께 조정해야 하는 경우에도 Service에 Repository를 추가하지
않는다. 대신 Repository별 Service를 분리하고, `UseCase`를 진입점으로 추가한다.

```text
Controller → UseCase
                 ├── Service A → Repository A
                 └── Service B → Repository B
```

`UseCase`는 여러 Service를 의존할 수 있지만, Repository를 직접 의존하지 않는다.

## 3. 계층별 책임

### 3.1 Controller

Controller는 HTTP 입출력 경계다. 입력 검증, 인증 정보 전달, 요청 DTO와 응답 DTO 변환만 담당하며,
비즈니스 흐름과 트랜잭션을 직접 처리하지 않는다.

Controller 하나는 다음 둘 중 하나의 진입점 하나에만 의존한다.

- 단일 Repository로 완결되는 단순 흐름에서는 하나의 Service
- 여러 Service의 조정이 필요한 복합 흐름에서는 하나의 UseCase

### 3.2 Service

Service는 자신이 담당한 도메인 또는 Aggregate의 Repository 인터페이스 하나만 의존한다. 하나의 Service에
둘 이상의 Repository 인터페이스를 주입하지 않으며, 다른 도메인의 Repository를 주입하지 않는다.

Service는 엔티티와 값 객체의 도메인 규칙을 호출하고, 단일 Repository 범위에서 필요한 조회·저장 작업을
수행한다. HTTP 요청·응답 타입, 다른 도메인의 Repository, 구체적인 외부 시스템 구현에 직접 의존하지
않는다.

```text
ReservationService → ReservationRepository
PaymentService     → PaymentRepository
```

다음 의존성은 Service 하나가 Repository 하나만 참조하더라도 도메인 책임이 맞지 않으므로 허용하지 않는다.

```text
OrderService → UserRepository
```

### 3.3 UseCase

하나의 업무 흐름에서 둘 이상의 Repository가 필요하면, 그 Repository를 각각 담당하는 Service로
책임을 분리하고 해당 Service들을 조정하는 UseCase를 구현한다. UseCase는 유즈케이스 파사드로서 Controller에
하나의 단순한 진입점을 제공한다. UseCase가 여러 Repository를 직접 주입받거나, 특정 Service에 Repository를
추가 주입해서는 안 된다.

UseCase는 실행 순서, 필요한 권한 범위, 여러 Service의 협력과 트랜잭션 경계를 책임진다. 도메인 규칙을
중복 구현하거나 단순히 한 Service를 전달 호출하는 계층으로 만들지 않는다. 실질적인 유스케이스 조정이
없다면 UseCase를 추가하지 않고 Controller가 해당 Service를 직접 의존한다.

```text
CompleteReservationUseCase
├── ReservationService → ReservationRepository
└── PaymentService     → PaymentRepository
```

### 3.4 Repository

Repository는 영속성 조회와 저장에 집중한다. Service는 Repository의 인터페이스에 의존하며, JPA,
Redis 또는 외부 시스템의 구체 구현은 `infra`와 같은 기술 경계 뒤로 격리한다.

## 4. 의존성 규칙

### 4.1 단순 유스케이스

단일 Repository로 처리할 수 있는 유스케이스는 Controller, Service, Repository의 세 계층으로 구성한다.

```text
ReservationController → ReservationService → ReservationRepository
```

### 4.2 복합 유스케이스

여러 Repository의 협력이 필요한 경우, 하나의 Service가 여러 Repository를 직접 의존하게 만들지
않는다. Repository별 책임을 Service로 분리하고, UseCase가 여러 Service를 조정한다.

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
```

### 4.3 이름과 배치

- Controller는 `<도메인>Controller`로 이름 짓고 `controller` 패키지에 둔다.
- Service는 `<도메인>Service`로 이름 짓고 `service` 패키지에 둔다.
- 복합 흐름의 UseCase는 수행하는 행위가 드러나도록 `<행위>UseCase`로 이름 짓고, 관련 도메인의
  `service` 패키지 또는 유스케이스 전용 하위 패키지에 둔다.
- Repository는 `<도메인>Repository`로 이름 짓고 `repository` 패키지에 둔다.
- UseCase 전용 패키지가 기본 패키지 계층도와 달라지는 경우에는 코드 변경 전에
  [ADR-0008](adr/0008-evolve-clean-architecture-by-use-case.md)의 전환 절차에 따라 구조를 확정한다.

## 5. 유지보수 점검 기준

새 기능을 추가하거나 기존 코드를 변경할 때 다음을 확인한다.

- Controller가 하나의 Service 또는 하나의 UseCase만 직접 의존하는가?
- Service가 자신의 도메인 또는 Aggregate에 속한 Repository 인터페이스 하나만 의존하는가?
- 여러 Repository의 협력이 필요할 때 책임별 Service와 이를 조정하는 UseCase로 분리했는가?
- UseCase가 실질적인 실행 순서, 트랜잭션 또는 협력자 조정을 수행하는가?
- Controller와 Service에 전달 전용 계층, 비즈니스 규칙 중복, 순환 의존성이 없는가?
- 패키지 구조와 계층별 세부 구현 규칙이 [코드 컨벤션](code-convention.md)을 따르는가?
