# Java 코드 컨벤션

## 1. 목적과 적용 범위

이 문서는 로컬스탬프 백엔드의 Java 코드 작성 기준을 정의한다. 신규 코드와 수정 코드에 적용하며,
리뷰·리팩터링·테스트 작성 시 같은 기준을 사용한다.

- 기본 규칙: [NAVER 캠퍼스 핵데이 Java 코딩 컨벤션 v1.2.0](https://naver.github.io/hackday-conventions-java/)
- 프로젝트: Java 21, Spring Boot 4.1, Gradle
- 기본 패키지: `io.regionevent.regioneventbackend`
- 적용 대상: `src/main/java`, `src/test/java`의 Java 소스

NAVER 컨벤션을 기본으로 적용한다. 이 문서에는 NAVER 컨벤션에 없는 프로젝트 고유 규칙과 재정의만 기록하며,
동일한 규칙은 중복해서 적지 않는다.

| 항목 | 프로젝트 규칙 |
| --- | --- |
| 들여쓰기 | 하드 탭 대신 **스페이스 4칸**을 사용한다. |
| import | Spring Boot 4.1의 `jakarta.*`와 프로젝트 기본 패키지에 맞게 그룹을 재정의한다. |
| 패키지 구성 | 계층만으로 나누지 않고 도메인 우선 구조를 사용한다. |
| 약어 표기 | 약어도 하나의 단어처럼 카멜 표기한다. |
| Spring/JPA | 생성자 주입, 트랜잭션 경계, 엔티티 캡슐화 규칙을 추가한다. |
| 테스트 | JUnit 5, Mockito, Testcontainers 기준을 추가한다. |

NAVER 컨벤션과 이 문서가 충돌하면 이 문서의 재정의를 우선한다. 이 문서와 자동 포매터의 결과가 충돌하면
이 문서를 우선하고, 반복되는 충돌은 포매터 설정에 반영한다.

## 2. NAVER 규칙 재정의

### 2.1 들여쓰기

- NAVER 컨벤션의 하드 탭 규칙 대신 스페이스 4칸을 사용한다.
- 탭 문자는 사용하지 않는다.

### 2.2 여러 줄 인수

메서드 인수가 길면 인수마다 한 줄을 사용한다.

```java
Reservation createReservation(
    Long visitorId,
    Long scheduleId,
    int quantity
) {
    // ...
}
```

## 3. 이름 규칙

### 3.1 공통 원칙

- 이름만으로 역할과 의미를 알 수 있게 작성한다.
- 같은 개념에는 프로젝트 전체에서 같은 용어를 사용한다.

### 3.2 카멜 표기와 약어

열거형과 레코드 이름은 NAVER의 클래스 명명 규칙과 동일하게 표기한다.

NAVER 컨벤션이 허용하는 대문자 약어 목록을 별도로 두지 않고 모든 약어를 일반 단어처럼 표기한다.
외부 API가 요구하는 이름이나 라이브러리 타입은 예외다.

| 사용 | 사용하지 않음 |
| --- | --- |
| `ApiResponse` | `APIResponse` |
| `JwtTokenProvider` | `JWTTokenProvider` |
| `QrCode` | `QRCode` |
| `requestId` | `requestID` |
| `HmacSigner` | `HMACSigner` |

### 3.3 클래스와 인터페이스

- 구현체라는 이유만으로 `Impl`을 붙이지 않는다. 역할이나 기술이 드러나는 이름을 사용한다.

```java
PaymentGateway paymentGateway;
PortOnePaymentAdapter portOnePaymentAdapter;
```

Spring 구성요소와 전달 객체는 다음 접미사를 사용한다.

| 역할 | 이름 예시 |
| --- | --- |
| MVC 컨트롤러 | `ReservationController` |
| 애플리케이션 서비스 | `ReservationService` |
| 저장소 | `ReservationRepository` |
| 외부 연동 포트 | `PaymentGateway` |
| 외부 연동 어댑터 | `PortOnePaymentAdapter` |
| 요청 DTO | `CreateReservationRequest` |
| 응답 DTO | `ReservationResponse` |
| 도메인 이벤트 | `ReservationConfirmedEvent` |
| 예외 | `ReservationNotFoundException` |
| 설정 | `SecurityConfig` |

JPA 엔티티에는 불필요하게 `Entity` 접미사를 붙이지 않는다. 도메인 이름 자체를 사용한다.

### 3.4 메서드

- 메서드 이름은 수행하는 동작을 구체적으로 표현한다.
- 조회는 `find`, 존재 여부는 `exists`, 검증은 `validate`, 변환은 `to` 또는 `from`을 사용한다.
- `get`은 값 반환이 보장될 때 사용한다. 없을 수 있는 조회는 `find`를 사용한다.
- 불리언 반환 메서드는 `is`, `has`, `can`, `should` 등으로 시작한다.
- 하나의 메서드는 하나의 추상화 수준과 하나의 책임을 유지한다.

```java
Reservation findReservation(Long reservationId);
boolean existsByPaymentKey(String paymentKey);
boolean canCheckIn(Instant now);
ReservationResponse from(Reservation reservation);
```

### 3.5 필드와 변수

- 컬렉션 이름은 복수형을 사용한다.
- 시간은 의미에 따라 `createdAt`, `expiredAt`, `startsAt`처럼 작성한다.
- 식별자는 `reservationId`, `contentId`처럼 대상 이름과 `Id`를 함께 쓴다.
- 불리언 필드는 긍정형으로 작성한다. 이중 부정 이름을 피한다.
- 단위가 모호한 숫자에는 단위를 이름에 포함한다.

```java
List<Reservation> reservations;
Duration holdDuration;
long timeoutMillis;
boolean active;
```

## 4. 패키지와 소스 구성

### 4.1 도메인 우선 패키지

기본 패키지 아래에서 지역, 콘텐츠, 예약, 결제, 방문, 후기, 쿠폰 등 도메인별로 먼저 나눈다.

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

### 4.2 파일과 멤버 순서

- 파일 이름은 최상위 타입 이름과 같아야 한다.
- 관련성이 높은 작은 타입만 내부 타입으로 선언한다.
- 클래스 멤버는 다음 순서를 기본으로 한다.

1. `static` 상수
2. 인스턴스 필드
3. 생성자와 정적 팩터리
4. 공개 메서드
5. `protected` 메서드
6. 비공개 메서드
7. 내부 타입

가독성을 위해 공개 메서드와 그 메서드만 돕는 비공개 메서드를 가까이 둘 필요가 있으면 예외를 허용한다.

### 4.3 import

- NAVER 컨벤션과 달리 정적 import에도 와일드카드(`*`)를 사용하지 않는다.
- 사용하지 않는 import는 제거한다.
- NAVER 컨벤션의 import 그룹 순서를 다음 순서로 대체한다.

1. `static import`
2. `java.*`
3. `jakarta.*`
4. 외부 라이브러리(`org.*`, `com.*`, 그 밖의 패키지)
5. 프로젝트 패키지(`io.regionevent.regioneventbackend.*`)

```java
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationRequest;
```

## 5. 선언과 서식 추가 규칙

### 5.1 상수와 값 객체

- 매직 넘버와 반복되는 문자열은 의미 있는 상수나 값 객체로 추출한다.

```java
private static final int MAX_RESERVATION_QUANTITY = 10;
```

### 5.2 공백과 빈 줄 재정의

- 타입 캐스팅 뒤에는 공백을 둔다: `(String) value`.
- 빈 줄을 연속 두 줄 이상 사용하지 않는다.

### 5.3 애너테이션

- 클래스, 생성자, 메서드 애너테이션은 파라미터 유무와 관계없이 선언과 별도 줄에 하나씩 작성한다.
- 필드나 매개변수 애너테이션은 짧고 가독성이 좋을 때 같은 줄을 허용한다.
- 여러 Spring 애너테이션을 한 줄에 나열하지 않는다.

```java
@Validated
@RestController
@RequestMapping("/reservations")
public class ReservationController {
}
```

## 6. Java 21 사용 원칙

- Java 21의 정식 기능은 가독성과 안정성을 높일 때 사용한다.
- 프리뷰 기능은 팀 합의와 별도 빌드 설정 없이는 사용하지 않는다.
- 불변 데이터 전달 객체에는 `record`를 우선 검토한다.
- JPA 엔티티에는 `record`를 사용하지 않는다.
- `var`는 우변만으로 타입이 명확하고 코드가 더 읽기 쉬울 때만 지역 변수에 사용한다.
- 조건 분기가 값 생성에 집중될 때 switch expression을 사용할 수 있다.
- 의미가 불명확한 긴 스트림 체인보다 이름 있는 메서드와 단순 반복문을 우선한다.

```java
public record ReservationResponse(
    Long reservationId,
    ReservationStatus status,
    Instant reservedAt
) {
}
```

## 7. Spring 코드 규칙

### 7.1 의존성 주입

- 생성자 주입을 사용한다.
- 필드 주입(`@Autowired` 필드)은 사용하지 않는다.
- 생성자가 하나라면 `@Autowired`를 생략한다.
- 의존성 필드는 `private final`로 선언한다.

```java
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }
}
```

### 7.2 계층별 책임

- 컨트롤러는 입력 검증, 인증 정보 전달, 응답 변환만 담당한다.
- 서비스는 기본적으로 유스케이스 흐름과 트랜잭션 경계를 담당한다. 복잡한 협력자 조정은 7.3의 기준을 따른다.
- 엔티티와 값 객체는 상태 변경 규칙과 도메인 불변식을 스스로 보호한다.
- 저장소는 영속성 조회와 저장에 집중한다.
- 외부 연동은 인터페이스 뒤로 격리하고 도메인 서비스에서 구체 클라이언트에 직접 의존하지 않는다.
- API 요청·응답에 JPA 엔티티를 직접 노출하지 않는다.

### 7.3 Application Service와 Facade

- 단순한 유스케이스는 기존 Service에서 처리한다. 복잡도가 실제로 증가했을 때만 별도 패턴을 도입한다.
- 여러 도메인 객체, Service, Repository 또는 외부 Port를 하나의 유스케이스로 조정해야 하면
  Application Service를 사용할 수 있다.
- Application Service는 실행 순서와 트랜잭션 경계를 책임진다. 도메인 정책은 직접 구현하지 않고 도메인 객체나
  해당 책임을 가진 Service에 위임한다.
- 여러 하위 Service나 기능을 호출자에게 단순한 하나의 진입점으로 제공해야 하면 Facade를 사용할 수 있다.
- Facade는 호출 인터페이스를 단순화하는 역할에 집중한다. 새로운 도메인 규칙을 숨기거나 트랜잭션 경계를
  암묵적으로 바꾸지 않는다.
- 같은 유스케이스를 위해 여러 협력자를 조정하는 것은 그 자체로 SRP 위반이 아니다. 서로 다른 변경 이유가
  한 클래스에 섞이기 시작할 때 책임을 분리한다.
- 전달만 하는 `Facade → ApplicationService → Service` 계층을 관성적으로 만들지 않는다.
- 실질적인 조정, 경계 단순화, 의존성 격리 중 하나가 있을 때만 계층을 추가한다.

클래스 이름은 역할과 유스케이스를 드러내도록 작성한다.

```java
ReservationService reservationService;
CompleteReservationApplicationService completeReservationApplicationService;
ReservationFacade reservationFacade;
```

### 7.4 트랜잭션

- 쓰기 트랜잭션은 유스케이스를 실행하는 서비스의 공개 메서드에 둔다.
- 조회 전용 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 컨트롤러에서 트랜잭션을 시작하지 않는다.
- 외부 API 호출을 DB 트랜잭션 안에서 수행해야 한다면 타임아웃, 재시도, 멱등성과 락 점유 시간을 검토한다.
- 예약·결제·체크인은 중복 요청에서도 같은 결과가 되도록 멱등하게 구현한다.

### 7.5 요청과 응답

- 요청 DTO에 Bean Validation을 선언하고 컨트롤러에서 `@Valid`로 검증한다.
- 형식 검증과 도메인 규칙 검증을 구분한다.
- 요청 DTO는 가능한 한 불변으로 작성한다.
- 응답 DTO에는 클라이언트에 필요한 정보만 명시적으로 매핑한다.
- API 경계에서는 `null`의 의미를 명확히 하고, 컬렉션은 가능한 한 빈 컬렉션으로 반환한다.

## 8. JPA와 도메인 모델

- 엔티티의 기본 생성자는 `protected`로 제한한다.
- 엔티티 생성을 표현하는 생성자나 정적 팩터리에서 필수 값을 검증한다.
- 상태를 무제한으로 바꾸는 공개 setter를 만들지 않는다.
- 상태 변경은 `confirm`, `cancel`, `checkIn`, `use`처럼 의도가 드러나는 메서드로 제공한다.
- 연관관계의 기본 로딩 전략은 지연 로딩으로 명시한다.
- 컬렉션 필드는 선언 시 초기화하고 외부에 변경 가능한 컬렉션을 그대로 노출하지 않는다.
- 엔티티를 로그에 통째로 출력하지 않는다.
- 엔티티에 Lombok의 `@Data`, `@Value`, `@EqualsAndHashCode`, `@ToString`을 사용하지 않는다.
- 엔티티의 동등성 비교가 필요하면 영속성 생명주기를 고려해 명시적으로 구현한다.
- 금액 계산에 `float` 또는 `double`을 사용하지 않는다. 정수 최소 화폐 단위 또는 `BigDecimal`을 사용한다.
- 시간 타입은 의미에 맞춰 `Instant`, `LocalDate`, `LocalDateTime` 등을 선택하고 기준 시간대를 명확히 한다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    public void confirm() {
        validateConfirmable();
        status = ReservationStatus.CONFIRMED;
    }
}
```

## 9. Lombok

- Lombok은 반복 코드를 줄이는 범위에서만 사용한다.
- 클래스 전체 setter를 만드는 `@Setter`와 복합 애너테이션 `@Data`를 사용하지 않는다.
- 서비스와 컨트롤러는 명시적 생성자를 기본으로 한다. 팀 합의 시 `@RequiredArgsConstructor`를 일관되게 사용할 수 있다.
- 엔티티의 빌더는 불완전한 객체를 만들 수 있으므로 기본적으로 사용하지 않는다.
- DTO의 빌더는 필드가 많아 생성 의도가 더 명확해질 때만 사용한다.

## 10. null, Optional, 컬렉션

- 매개변수와 반환값의 `null` 허용 여부를 암묵적으로 두지 않는다.
- 조회 결과가 없을 수 있는 저장소·조회 메서드의 반환값에 `Optional`을 사용할 수 있다.
- 엔티티 필드, DTO 필드, 메서드 매개변수에는 `Optional`을 사용하지 않는다.
- `Optional.get()`을 바로 호출하지 않는다.
- 컬렉션 반환값으로 `null`을 반환하지 않는다.
- 외부에서 수정하면 안 되는 컬렉션은 불변 복사본이나 읽기 전용 뷰로 반환한다.

```java
return reservationRepository.findById(reservationId)
    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
```

## 11. 예외와 로깅

### 11.1 예외

- 예외를 잡고 무시하지 않는다.
- 복구할 수 없는 예외를 단순 로그 출력 후 정상 흐름으로 바꾸지 않는다.
- `Exception`이나 `RuntimeException`을 포괄적으로 잡는 코드는 애플리케이션 경계 외에는 피한다.
- 도메인 실패는 의미 있는 예외 타입과 오류 코드로 표현한다.
- 예외 메시지에는 원인 파악에 필요한 식별자와 상태를 포함하되 개인정보와 비밀값은 포함하지 않는다.
- 원인을 변환해 던질 때 원본 예외를 `cause`로 보존한다.

### 11.2 로깅

- 문자열 연결 대신 자리표시자를 사용한다.
- 로그 레벨을 목적에 맞게 사용하고 정상 비즈니스 거절을 무조건 오류로 기록하지 않는다.
- 모든 요청을 `requestId`로 추적할 수 있게 한다.
- 예약·결제·방문 식별자와 상태 전이는 구조화된 필드로 남긴다.
- 비밀번호, JWT, 결제 비밀키, QR 원문, 개인정보를 로그에 남기지 않는다.

```java
log.info("Reservation confirmed. reservationId={}, paymentId={}", reservationId, paymentId);
```

## 12. 주석과 문서화

- 주석은 코드가 무엇을 하는지보다 왜 그렇게 해야 하는지를 설명한다.
- 코드로 명확히 표현할 수 있는 주석은 이름이나 메서드 추출로 대체한다.
- 공개 API나 복잡한 도메인 규칙에는 필요할 때 Javadoc을 작성한다.
- `TODO`에는 후속 작업을 추적할 이슈 번호나 사유를 함께 남긴다.
- 주석 처리한 코드를 보관하지 않는다. 필요하면 Git 이력을 사용한다.

```java
// 결제 웹훅은 재전송될 수 있으므로 이미 처리한 결제는 기존 결과를 반환한다.
if (payment.isCompleted()) {
    return payment;
}
```

## 13. 테스트 코드

### 13.1 이름과 구조

- 단위 테스트 클래스 이름은 대상 타입 이름을 접두부로 사용한다.
- 테스트 메서드는 `대상메서드_조건_기대결과` 구조의 영문 이름을 권장한다.
- `@DisplayName`에는 실패 시 바로 이해할 수 있는 한글 설명을 사용할 수 있다.
- 테스트 하나는 하나의 행위나 규칙을 검증한다.
- Given-When-Then 또는 Arrange-Act-Assert 구조를 일관되게 사용한다.

```java
@Test
@DisplayName("잔여 정원이 있으면 예약을 확정한다")
void confirm_whenCapacityIsAvailable_changesStatusToConfirmed() {
    // given
    Reservation reservation = Reservation.pending();

    // when
    reservation.confirm();

    // then
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
}
```

### 13.2 테스트 범위

- 도메인 규칙은 빠른 단위 테스트로 우선 검증한다.
- Spring 컨텍스트가 필요 없는 테스트에 `@SpringBootTest`를 사용하지 않는다.
- 컨트롤러, JPA, 보안 등은 목적에 맞는 슬라이스 테스트를 우선 검토한다.
- MySQL, Redis와 실제 동작 차이가 중요한 통합 테스트는 Testcontainers를 사용한다.
- 예약 정원, 결제 웹훅, QR 체크인에는 동시성·중복 요청·실패 재시도 테스트를 포함한다.
- 테스트 간 실행 순서와 공유 상태에 의존하지 않는다.
- 시간에 의존하는 코드는 고정 가능한 `Clock`을 주입해 검증한다.

## 14. 리뷰 체크리스트

코드 리뷰 전에 다음을 확인한다.

- [ ] 패키지와 클래스 이름만으로 책임을 파악할 수 있는가?
- [ ] 스페이스 4칸, 정적 import 와일드카드 금지, 프로젝트 import 그룹을 지켰는가?
- [ ] 컨트롤러·서비스·도메인·인프라의 책임이 섞이지 않았는가?
- [ ] JPA 엔티티를 API에 직접 노출하거나 공개 setter를 추가하지 않았는가?
- [ ] 트랜잭션 범위와 외부 API 호출의 실패 방식을 검토했는가?
- [ ] 예약·결제·체크인의 동시성과 멱등성을 검증했는가?
- [ ] 로그와 예외에 개인정보·토큰·비밀값이 포함되지 않는가?
- [ ] 정상·경계·실패 조건을 테스트했는가?
- [ ] `./gradlew test`와 `./gradlew build`가 통과하는가?
