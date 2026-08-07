# ADR-0072: 배포 상태 확인용 Health 엔드포인트만 무인증으로 공개한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-07
- 결정일: 2026-08-07
- 관련 요구사항: [기술 스택](../local-stamp-platform-tech-stack.md)의 관측·배포, [공통 인증·인가 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#470 Docker Compose API·Redis 공동 실행 구성](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/470), [#471 ECR 이미지 기반 EC2 Compose 배포 자동화 및 운영 검증](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/471)
- 대체 대상: 없음

## 맥락

API와 Redis를 Docker Compose로 기동하고 ASG 인스턴스 교체로 배포한다. 새 API 컨테이너가 요청을 처리할 수 있는지는
인증 토큰을 만들거나 보관하지 않는 Compose와 배포 인프라의 상태 확인 절차가 판단해야 한다.

현재 `health`와 `metrics`는 웹 Actuator 노출 목록에 있지만, Spring Security의 그 밖의 모든 요청 인증 규칙에
포함된다. 따라서 무인증 상태 확인은 `401 UNAUTHENTICATED`가 되어 자동 배포 완료 판정에 사용할 수 없다. 반대로
Actuator 전체를 공개하면 지표와 구성 요소 상태를 불필요하게 노출할 수 있다.

## 결정 동인과 불변 조건

- Docker Compose와 ASG 상태 확인은 Access Token, Refresh Token 또는 별도 공유 비밀 없이 API 준비 상태를 판정해야 한다.
- 무인증으로 공개하는 응답은 구성 요소 이름, 예외, 버전, 환경 변수와 개인정보를 포함하지 않는다.
- `metrics`를 포함한 Health 이외의 Actuator 엔드포인트와 모든 업무 API의 인증 경계는 유지한다.
- Health 응답은 Redis 또는 RDS 장애 시 정상으로 위장하지 않고 비정상 상태를 반환해야 한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GET /actuator/health`만 무인증으로 허용하고 상세 정보를 숨긴다 | 기존 단일 애플리케이션 포트에서 Compose·ASG 상태 확인을 바로 수행할 수 있고, 지표와 업무 API는 계속 보호된다. | 외부 호출자가 서비스의 단순 정상·비정상 상태를 알 수 있다. | 낮음. 보안 규칙과 health 설정을 되돌리고 별도 상태 확인 경로로 전환할 수 있다. | 추천안이며 사용자가 채택했다. |
| 2 | 별도 관리 포트와 네트워크 제한으로 Health를 제공한다 | 관리 요청을 업무 API 포트와 분리할 수 있다. | ALB·보안 그룹·포트 노출과 Compose 상태 확인 경로를 함께 설계해야 하며, 현재 저장소에 해당 인프라 계약이 없다. | 중간. 포트·네트워크·배포 구성을 함께 바꿔야 한다. | 향후 운영 네트워크 요구가 생기면 검토한다. |
| 3 | 모든 Actuator 엔드포인트에 Bearer 인증을 요구한다 | 관리 정보를 외부에 노출하지 않는다. | 자동 상태 확인 주체가 토큰을 안전하게 발급·갱신·보관해야 해 배포 경계가 복잡해지고, 현재 ASG 상태 확인을 수행할 수 없다. | 중간. 배포 자격 증명과 회전 체계를 추가해야 한다. | 현재 Compose·ASG 배포 방식에 부적합하다. |

## 결정

Spring Security에서 `GET /actuator/health`만 인증 없이 허용한다. Health 상세 정보는 항상 숨기며, 응답에는 단순
상태만 포함한다. `GET /actuator/metrics/**`를 포함한 그 밖의 Actuator 엔드포인트는 기존처럼 Access Token
인증을 요구한다.

이 결정은 배포 상태 확인에 한정하며, 업무 API의 공개 경로·Bearer 인증·Refresh Cookie 경계를 정한
[ADR-0045](0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md)를 대체하지 않는다.

## 결과와 트레이드오프

### 기대 효과

- Compose healthcheck와 배포 인프라의 상태 확인 절차가 별도 인증 자격 증명 없이 새 인스턴스의 준비 상태를 판정할 수 있다.
- Redis와 RDS를 포함한 Health 상태가 비정상이면 배포 완료로 오인하지 않는다.
- 지표와 업무 API의 인증 경계를 유지한다.

### 수용한 단점과 위험

- 외부 호출자는 애플리케이션의 단순 정상·비정상 상태를 확인할 수 있다.
- Health 정보를 숨겨도 상태 전환 빈도는 제한적으로 운영 상황을 암시할 수 있다.
- 별도 관리 포트·네트워크 격리를 도입하지 않으므로, 인프라 보안 그룹은 API 포트에 불필요한 직접 접근을 허용하지 않아야 한다.

## 전환과 롤백

Security 설정에 Health 전용 허용 규칙을 추가하고, Health 상세 정보 비공개 설정을 명시한다. 인증 통합 테스트,
Compose healthcheck와 배포 상태 확인을 같은 변경에서 검증한다.

Health 응답이 정보를 노출하거나 상태 확인 경로가 부적합하면 Health 허용 규칙과 관련 설정을 되돌린다. 이 경우
자동 배포 완료 판정은 별도 관리 포트·네트워크 제한 방식이 준비될 때까지 사용하지 않는다. 인증된 Actuator와
업무 API의 기존 접근 제어는 롤백 과정에서도 유지한다.

## 검증 방법

- Access Token 없이 `GET /actuator/health`가 상세 정보 없이 정상 또는 비정상 상태를 반환하는지 통합 테스트로 검증한다.
- Access Token 없이 `GET /actuator/metrics/**`와 Health 이외의 보호 경로가 `401 UNAUTHENTICATED`를 반환하는지 검증한다.
- 유효한 Access Token으로 필요한 metrics 조회가 기존과 같이 성공하는지 검증한다.
- Redis 또는 RDS 연결이 실패한 환경에서 Health가 성공 상태를 반환하지 않는지 검증한다.
- Compose와 ASG 배포에서 Health가 정상 상태가 된 인스턴스만 완료로 판정되는지 확인한다.

## 대체 조건

- ALB·보안 그룹·운영 망 분리로 별도 관리 포트를 안전하게 노출할 수 있게 된다.
- Health 상태 노출이 보안 사고 또는 운영 정책 위반으로 확인된다.
- 상태 확인에 인증된 관리 API, 서비스 메시 또는 별도 관측 플랫폼이 도입된다.
