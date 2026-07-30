# Java 코드 컨벤션

## 1. 목적과 기준 문서

이 문서는 로컬스탬프 백엔드의 Java 작성 규칙을 정의한다. 신규·수정 코드, 리팩터링, 테스트와 리뷰에
적용한다. 기본 규칙은 [NAVER 캠퍼스 핵데이 Java 코딩 컨벤션 v1.2.0](https://naver.github.io/hackday-conventions-java/)이며,
이 문서는 프로젝트 고유의 재정의만 둔다.

| 주제 | 단일 출처 |
| --- | --- |
| 패키지 구조·계층 책임·의존성 | [아키텍처](ARCHITECTURE.md) |
| 유스케이스 경계·Port·Adapter 전환 | [ADR-0008](adr/0008-evolve-clean-architecture-by-use-case.md) |
| API 응답·오류·페이지네이션 | [API 공통 계약](api/common/README.md) |
| 제품 정책 | [PRD 정책 카탈로그](local-stamp-platform-prd.md#8-정책-카탈로그) |
| 실제 버전·의존성 | [build.gradle](../build.gradle) |

- 이 규칙은 `src/main/java`, `src/test/java`에 적용한다.
- NAVER 컨벤션과 충돌하면 이 문서를, 자동 포매터와 충돌하면 이 문서를 우선한다.
- 반복되는 포매터 충돌은 포매터 설정으로 해결한다.

## 2. 이름과 서식

### 2.1 이름

- 이름만으로 역할과 의미를 알 수 있게 작성하고, 같은 개념에는 같은 용어를 사용한다.
- 약어는 일반 단어처럼 카멜 표기한다. 예: `ApiResponse`, `JwtTokenProvider`, `QrCode`, `requestId`.
  외부 API나 라이브러리가 요구하는 이름은 예외다.
- 구현체라는 이유만으로 `Impl`을 붙이지 않는다. 역할 또는 기술이 드러나는 이름을 사용한다.
- 외부 연동은 `PaymentGateway`, `PortOnePaymentAdapter`, 요청·응답 DTO는
  `CreateReservationRequest`, `ReservationResponse`처럼 역할을 드러내는 접미사를 사용한다.
- JPA 엔티티에는 `Entity` 접미사를 붙이지 않는다.
- 메서드 이름은 동작을 구체적으로 드러내며, 조회·존재 확인·검증·변환·불리언 반환에는 각각
  `find`·`exists`·`validate`·`to`/`from`·`is`/`has`/`can`/`should`를 사용한다. `get`은 값이
  반드시 존재할 때만 사용한다.
- 메서드 하나는 하나의 책임과 추상화 수준만 가진다.
- 컬렉션은 복수형, 식별자는 `reservationId`처럼 대상과 `Id`를 함께 사용한다. 시간·단위는
  `createdAt`, `timeoutMillis`처럼 의미를 드러내고, 불리언은 긍정형으로 작성한다.

### 2.2 소스 서식

- 들여쓰기는 스페이스 4칸을 사용하고 탭 문자는 사용하지 않는다.
- 여러 줄 인수는 인수마다 한 줄로 작성한다.
- 파일 이름은 최상위 타입 이름과 같게 한다. 멤버는 `static` 상수, 인스턴스 필드, 생성자·정적 팩터리,
  공개·`protected`·비공개 메서드, 내부 타입 순서로 둔다. 관련 공개·비공개 메서드는 가까이 둘 수 있다.
- 매직 넘버와 반복 문자열은 의미 있는 상수 또는 값 객체로 추출한다.
- 타입 캐스팅 뒤에는 공백을 두고, 빈 줄은 연속 두 줄 이상 사용하지 않는다.
- 클래스·생성자·메서드 애너테이션은 선언과 분리해 한 줄에 하나씩 작성한다. 필드·매개변수 애너테이션은
  짧고 읽기 좋을 때 같은 줄을 허용한다.
- 사용하지 않는 import와 정적 import 와일드카드(`*`)는 사용하지 않는다. import는 `static`, `java`,
  `jakarta`, 외부 라이브러리, 프로젝트 패키지 순서로 그룹화한다.

## 3. Java와 Spring

### 3.1 Java 21

- 정식 기능은 가독성과 안정성을 높일 때만 사용하고, 프리뷰 기능은 팀 합의와 별도 빌드 설정 없이는 사용하지 않는다.
- 불변 DTO에는 `record`를 우선 검토하되 JPA 엔티티에는 사용하지 않는다.
- `var`는 우변만으로 타입이 명확할 때만 지역 변수에 사용한다.
- 값 생성 중심의 분기에는 switch expression을 사용할 수 있다. 긴 스트림 체인보다 이름 있는 메서드나
  단순 반복문을 우선한다.

### 3.2 의존성 주입과 트랜잭션

- 생성자 주입과 `private final` 필드를 사용한다. JPA 엔티티가 아닌 Spring 컴포넌트는 명시적 생성자 또는
  `@RequiredArgsConstructor`를 사용할 수 있다. 필드 주입은 사용하지 않으며, 생성자가 하나면 `@Autowired`를 생략한다.
- 쓰기 트랜잭션은 유스케이스를 실행하는 UseCase 또는 Service의 공개 메서드에 둔다. 조회는
  `@Transactional(readOnly = true)`를 사용한다.
- 외부 API를 트랜잭션 안에서 호출하면 타임아웃, 재시도, 멱등성과 락 점유 시간을 검토한다.
- 도메인별 동시성·멱등성 요구사항은 [PRD 정책 카탈로그](local-stamp-platform-prd.md#8-정책-카탈로그)를 따른다.

### 3.3 도메인과 API 경계

- Controller, Service, UseCase, Repository의 책임과 의존 방향은 [아키텍처](ARCHITECTURE.md)를 따른다.
- 엔티티와 값 객체는 상태 변경 규칙과 도메인 불변식을 스스로 보호한다.
- 요청 DTO에는 Bean Validation을 선언하고 Controller에서 `@Valid`로 검증한다. 형식 검증과 도메인 규칙
  검증을 구분하며, DTO는 가능한 한 불변으로 작성한다.
- API 응답·오류·페이지네이션의 구조와 새 공개 계약은 [API 공통 계약](api/common/README.md)을 먼저 갱신한다.

## 4. JPA, Lombok, null

### 4.1 JPA와 Lombok

- 엔티티의 기본 생성자는 `protected`로 제한하고, 생성자·정적 팩터리에서 필수 값을 검증한다.
- 공개 setter 대신 `confirm`, `cancel`, `checkIn`, `use`처럼 의도가 드러나는 상태 변경 메서드를 제공한다.
- 연관관계는 지연 로딩을 명시하고, 컬렉션은 선언 시 초기화하며 변경 가능한 컬렉션을 외부에 노출하지 않는다.
- 엔티티를 통째로 로그에 출력하지 않는다. 동등성 비교는 영속성 생명주기를 고려해 명시적으로 구현한다.
- 금액에는 `float`·`double` 대신 정수 최소 화폐 단위 또는 `BigDecimal`을 사용하고, 시간은 의미에 맞는
  `Instant`, `LocalDate`, `LocalDateTime`과 기준 시간대를 사용한다.
- Lombok의 `@Setter`, `@Builder`, `@Data`는 사용하지 않는다. 엔티티에는 `@Value`, `@EqualsAndHashCode`,
  `@ToString`, `@RequiredArgsConstructor`를 사용하지 않으며, `@Getter`와
  `@NoArgsConstructor(access = AccessLevel.PROTECTED)`만 허용한다.

### 4.2 null, Optional, 컬렉션

- 매개변수와 반환값의 `null` 허용 여부를 암묵적으로 두지 않는다.
- 없을 수 있는 저장소·조회 결과에는 `Optional`을 사용할 수 있지만, 엔티티·DTO 필드와 매개변수에는 사용하지 않는다.
- `Optional.get()`을 바로 호출하지 않고, 컬렉션 반환값으로 `null`을 반환하지 않는다.
- 외부 수정이 금지된 컬렉션은 불변 복사본 또는 읽기 전용 뷰로 반환한다.

## 5. 예외와 로깅

### 5.1 예외

- 공개 오류 필드·상태·코드·메시지는 [응답·오류 공통 계약](api/common/response-and-error.md), 실패 판정은
  [PRD 정책 카탈로그](local-stamp-platform-prd.md#8-정책-카탈로그)를 따른다. 새 공개 계약은 구현 전에 갱신한다.
- 예상 가능한 정책 위반은 전역 `ErrorCode`를 가진 `BusinessException`으로 표현하며, 오류 코드별 예외 하위 타입이나
  도메인별 오류 enum을 만들지 않는다.
- 도메인 객체는 `ResponseEntity`, `HttpStatus`, 오류 응답 DTO를 직접 생성하지 않는다.
- 프로그래밍 오류, 알 수 없는 데이터 무결성 위반, 인프라 장애를 업무 거절인 `BusinessException`으로 바꾸지 않는다.
- 쓰기 트랜잭션을 중단해야 하는 정책 실패는 unchecked 예외로 전파한다. checked 예외에는 타입 기반 롤백 규칙과
  회귀 테스트를 명시한다.
- 예외를 무시하거나 정상 흐름으로 바꾸지 않으며, 변환해 던질 때 원인을 `cause`로 보존한다.
- DB 제약 위반은 이름과 도메인 의미가 확인된 제약만 변환한다. 포괄 예외 처리는 HTTP·Scheduler·메시지 소비자
  같은 애플리케이션 경계의 마지막 안전망으로만 사용한다.

### 5.2 로깅

- 문자열 연결 대신 자리표시자를 사용하고, 정상 업무 거절을 무조건 오류로 기록하지 않는다.
- 예상 가능한 업무·검증 실패에는 `requestId`, 오류 코드, 필요한 식별자를 구조화해 남기고 기본적으로
  스택 트레이스를 남기지 않는다.
- 처리되지 않은 시스템 예외는 애플리케이션 경계에서 한 번만 `error`로 기록하고 스택 트레이스를 포함한다.
  하위 계층에서 기록한 예외를 다시 기록하지 않는다.
- `requestId`, 도메인 식별자·상태 전이의 구조화 로그와 비밀값·개인정보의 비노출은
  [PRD 정책 카탈로그](local-stamp-platform-prd.md#8-정책-카탈로그)를 따른다. 엔티티 전체는 로그에 남기지 않는다.

## 6. 주석, 테스트와 리뷰

### 6.1 주석

- 주석은 무엇보다 왜 필요한지를 설명한다. 코드로 표현할 수 있으면 이름 개선이나 메서드 추출을 우선한다.
- 공개 API나 복잡한 도메인 규칙에는 필요할 때 Javadoc을 작성한다.
- `TODO`에는 후속 이슈 번호 또는 사유를 남기며, 주석 처리한 코드는 Git 이력으로 관리한다.

### 6.2 테스트

- 테스트 클래스는 대상 타입 이름을 접두부로 하고, 테스트 메서드는 `대상메서드_조건_기대결과` 형식을 권장한다.
  `@DisplayName`은 이해하기 쉬운 한글로 작성할 수 있으며, 테스트 하나는 하나의 행위나 규칙을 검증한다.
- Given-When-Then 또는 Arrange-Act-Assert를 일관되게 사용한다.
- 도메인 규칙은 단위 테스트를 우선하고, 불필요한 `@SpringBootTest` 대신 목적에 맞는 MVC·JPA·보안 슬라이스 테스트를 사용한다.
  MySQL·Redis 차이가 중요한 통합 테스트에는 Testcontainers를 사용하며, 테스트 실행 순서나 공유 상태에 의존하지 않는다.
- PRD의 임계 업무 흐름은 동시성, 중복 요청, 실패 재시도를 검증하고 시간 의존 코드는 주입 가능한 `Clock`으로 고정한다.
- 유스케이스 경계 전환과 Port·Adapter는 [ADR-0008의 전환·검증 기준](adr/0008-evolve-clean-architecture-by-use-case.md#검증-방법)을 따른다.
- API 테스트는 [API 공통 계약](api/common/README.md)과 도메인 API 명세의 오류·응답·페이지네이션 계약을 검증한다.

### 6.3 리뷰 체크리스트

- [ ] 서식·import·이름 규칙을 지켰는가?
- [ ] 패키지·계층·의존성은 [아키텍처](ARCHITECTURE.md)와 ADR을 따르는가?
- [ ] JPA 캡슐화, 트랜잭션, 외부 API 실패와 멱등성을 검토했는가?
- [ ] API·오류·보안·로그 계약을 지켰는가?
- [ ] 정상·경계·실패 조건을 테스트하고 `./gradlew test`, `./gradlew build`를 통과했는가?
