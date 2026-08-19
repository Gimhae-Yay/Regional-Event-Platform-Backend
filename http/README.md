# HTTP 시나리오 실행 방법

`http/p0`, `http/p1`은 로컬 환경에서 API 업무 흐름을 재현하는 IntelliJ HTTP Client 시나리오다. 각 디렉터리의 `scripts`에는 해당 시나리오에 필요한 MySQL·Redis 준비와 전체 실행 도구가 함께 있다.

## 사전 조건

- Docker Desktop이 실행 중이어야 한다. 실행 도구는 `docker compose`로 MySQL과 Redis를 준비하고, `jetbrains/intellij-http-client` 이미지로 `.http` 파일을 실행한다. 이 저장소에서는 공유받은 로컬 `compose.yaml`을 저장소 루트에 별도로 둔다. 해당 파일은 Git에서 의도적으로 제외되어 있으므로, 받지 못한 경우 실행 전에 팀의 로컬 개발 환경 Compose 파일을 받아 배치해야 한다.
- 애플리케이션은 별도로 실행해야 한다. 기본 주소는 `http://localhost:8080`이며, 다른 주소를 사용하면 `-BaseUrl` 또는 `--base-url`로 전달한다.
- 시드는 로컬 개발 데이터 전용이다. 실행하면 `regional_event` 데이터베이스의 P0·P1 fixture와 Redis 데이터가 초기화되므로 운영 환경이나 공유 환경에서는 실행하지 않는다.

## 빠른 실행

PowerShell에서는 저장소 루트에서 다음 명령을 실행한다.

```powershell
# P0 전체 시나리오
.\http\p0\scripts\run.ps1

# P1 전체 시나리오
.\http\p1\scripts\run.ps1
```

Bash에서는 다음 명령을 실행한다.

```bash
# P0 전체 시나리오
bash http/p0/scripts/run.sh

# P1 전체 시나리오
bash http/p1/scripts/run.sh
```

P1은 P1 전용 fixture를 정리한 뒤 P0 공통 시드와 P1 시드를 차례로 적용한다. 따라서 새 로컬 데이터에서는 P0을 먼저, P1을 다음에 실행하는 순서를 권장한다.

## 준비 작업만 실행

애플리케이션을 기동하기 전 fixture만 확인하려면 `-PrepareOnly` 또는 `--prepare-only`를 사용한다. 이 옵션은 HTTP 요청을 보내지 않는다.

```powershell
.\http\p0\scripts\run.ps1 -PrepareOnly
.\http\p1\scripts\run.ps1 -PrepareOnly
```

```bash
bash http/p0/scripts/run.sh --prepare-only
bash http/p1/scripts/run.sh --prepare-only
```

준비 스크립트만 직접 실행할 수도 있다. 이미 MySQL·Redis 컨테이너가 기동되어 있다면 `-SkipCompose` 또는 `--skip-compose`로 기동 단계를 건너뛴다.

```powershell
.\http\p0\scripts\prepare.ps1 -SkipCompose
.\http\p1\scripts\prepare.ps1 -SkipCompose
```

```bash
bash http/p0/scripts/prepare.sh --skip-compose
bash http/p1/scripts/prepare.sh --skip-compose
```

## 다른 애플리케이션 주소 사용

예를 들어 애플리케이션이 8081 포트에서 실행 중이면 다음과 같이 실행한다.

```powershell
.\http\p0\scripts\run.ps1 -BaseUrl http://localhost:8081
.\http\p1\scripts\run.ps1 -BaseUrl http://localhost:8081
```

```bash
bash http/p0/scripts/run.sh --base-url http://localhost:8081
bash http/p1/scripts/run.sh --base-url http://localhost:8081
```

## 시나리오 범위

| 경로 | 범위 |
| --- | --- |
| `http/p0` | 인증, 운영자·지역 관리자 심사, 콘텐츠, 예약, 체크인, 후기 등 P0 흐름 |
| `http/p1` | 스탬프북, 쿠폰, 미션, 결제·환불, 플랫폼 관리자·지역 관리자 흐름 |

개별 요청을 확인할 때는 IntelliJ IDEA에서 원하는 `.http` 파일을 열어 실행할 수 있다. 이 경우에도 먼저 해당 단계의 `prepare` 스크립트로 fixture와 Redis를 준비해야 한다.

## PortOne fake 웹훅 시나리오

P1 전체 실행에는 PortOne 웹훅 요청을 포함하지 않는다. 기본 설정은 실제 서명 검증을 사용하므로, 고정된 로컬 헤더로는 웹훅 성공을 검증할 수 없기 때문이다.

웹훅 요청은 애플리케이션을 `PORTONE_FAKE_ENABLED=true`로 기동한 전용 로컬 환경에서만 아래 옵션으로 실행한다. 이 fake 환경은 실제 PortOne 연동이나 운영 승인 검증에 사용할 수 없다.

```powershell
.\http\p1\scripts\run.ps1 -RunPortOneFakeWebhook
```

```bash
bash http/p1/scripts/run.sh --run-portone-fake-webhook
```
