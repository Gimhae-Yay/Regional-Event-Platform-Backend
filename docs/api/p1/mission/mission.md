# 지역 미션 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-03`, `P1-FR-04`, `MSN-01`, `MSN-02`, `MSN-03`, `MSN-04`, `MSN-05`, `P1-AC-03`, `P1-AC-04` |
| 소유 도메인 | 미션 |
| 기준 문서 | [지역 미션](../../../p1/regional-mission.md), [P1 명세](../../../p1-spec.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [ADR-0067](../../../adr/0067-model-stampbook-and-mission-progress-from-immutable-visits.md), [ADR-0068](../../../adr/0068-use-immutable-coupon-lifecycle-and-evidence-sources.md), [ADR-0072](../../../adr/0072-validate-mission-target-content-availability-before-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 지역 미션의 생성·수정·검토 요청·승인·반려·종료, 공개 조회, 참여, 진행도 조회와 완료 보상 수령 계약을 정의한다.
미션은 `DRAFT → PENDING_REVIEW → PUBLISHED → ENDED` 수명주기를 사용하며 반려는 `PENDING_REVIEW → DRAFT`다.
`PUBLISHED` 뒤에는 핵심 값 수정 없이 종료만 허용한다.

완료 조건은 `VISIT_COUNT` 또는 `CONTENT_SET`만 사용한다. 미션은 지역 관리자 승인 성공 시 즉시 공개하며 별도 자동
공개 Scheduler를 두지 않는다. 미션 자동 종료는 HTTP API가 아니므로 Endpoint 없이 Scheduler 실행 계약으로 관리한다.
완료 보상은 미션이 `PUBLISHED`이고 `endsAt` 전일 때만 신규 수령할 수 있으며, 미션 종료 또는 종료 예정 시각 도달과
동시에 미수령 권리는 만료된다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `P1-FR-03`, `MSN-01` | `POST /operator/missions` | `mission`, `mission_target_content` |
| `P1-FR-03`, `MSN-01` | `PATCH /operator/missions/{missionId}` | `mission`, `mission_target_content` |
| `P1-FR-03`, `MSN-01` | `GET /operator/missions`, `GET /operator/missions/{missionId}` | `mission`, `mission_target_content`, `audit_event` |
| `P1-FR-03`, `MSN-01` | `POST /operator/missions/{missionId}/submit` | `mission`, `audit_event` |
| `P1-FR-03`, `MSN-01` | `POST /operator/missions/{missionId}/end` | `mission`, `mission_participation`, `audit_event` |
| `P1-FR-03`, `MSN-01` | `GET /region-admin/missions`, `GET /region-admin/missions/{missionId}` | `mission`, `mission_target_content` |
| `P1-FR-03`, `MSN-01` | `POST /region-admin/missions/{missionId}/approve`, `POST /region-admin/missions/{missionId}/reject` | `mission`, `audit_event` |
| `P1-FR-03`, `MSN-01` | `GET /region-admin/missions/{missionId}/history` | `mission`, `audit_event`, `audit_event_actor_link` |
| `P1-FR-03`, `MSN-01` | 자동 공개 제외. 지역 관리자 승인 API에서 즉시 공개 | `mission.status`, `audit_event` |
| `P1-FR-03`, `MSN-01` | 내부 자동 종료 `scheduler` | `mission.status`, `mission.ended_at`, `mission_participation.status`, `audit_event` |
| `P1-FR-03`, `MSN-01` | `GET /regions/{regionId}/missions`, `GET /missions/{missionId}` | `mission`, `mission_target_content`, `mission_participation` |
| `P1-FR-04`, `MSN-02` | `POST /missions/{missionId}/participations` | `mission`, `mission_participation` |
| `P1-FR-04`, `MSN-03` | 내부 방문 완료 이벤트 처리 | `visit`, `mission`, `mission_participation`, `mission_progress` |
| `P1-FR-04`, `MSN-04`, `MSN-05` | `GET /me/mission-participations`, `GET /me/mission-participations/{participationId}`, `POST /me/mission-participations/{participationId}/rewards/claim` | `mission_participation`, `mission_progress`, `mission_reward_claim`, `coupon_issuance`, `coupon` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. 미션 종료 예정 시각은 `+09:00`, 처리 시각은 UTC `Z`를 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 조회는 인증 선택, 참여·내 조회·보상 수령은 방문자 인증, 운영자 API는 담당 지역 `OPERATOR`, 지역 관리자 API는 담당 지역 `REGION_ADMIN`을 요구한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 미션 도메인 오류 코드를 명시한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API는 공통 `page`, `size`, `totalElements`, `totalPages` 계약을 사용하고 `status` 필터와 고정 정렬을 API별로 정의한다. |

## 3. 공통 상태 변경 감사 계약

이 계약은 미션 생성·수정·검토 요청·승인·반려·조기 종료에 적용한다. 자동 종료는
[자동 종료 Scheduler 명세](end-missions.md)의 더 구체적인 실패 감사 계약을 따른다.

1. 성공한 상태 변경과 `result = SUCCESS` 감사 이벤트는 같은 트랜잭션으로 커밋하거나 함께 롤백한다.
2. 인증 주체, 담당 지역과 대상 미션을 서버 데이터로 안전하게 식별한 뒤 발생한 권한 거부, 상태 충돌,
   도메인 조건 거부 또는 처리 예외는 원래 트랜잭션을 먼저 롤백한다. 그 뒤 같은 `requestId`를 사용해
   `result = FAILURE`, 확인된 이전 상태, `next_state = null`, 공개 오류 코드와 같은 비개인 `reason_code`를
   독립 트랜잭션으로 기록한다.
3. 잘못된 JSON·타입·경로 식별자, 미인증 요청 또는 서버 데이터로 대상을 안전하게 식별하지 못한 요청에는
   실패 감사 이벤트를 만들지 않고 `requestId`와 공개 오류 코드만 구조화 로그로 남긴다.
4. 실패 감사 이벤트에는 확인된 `actor_kind`, 역할과 지역만 사용한다. 활성 사용자 연결이 필요한 경우
   실패 감사 이벤트와 `audit_event_actor_link`를 같은 독립 트랜잭션으로 기록한다.
5. 실패 감사 기록도 실패하면 원래 HTTP 실패 결과를 바꾸지 않고 `requestId`, 대상 식별자와 비개인 오류 코드만
   구조화 로그로 남긴다.

## 4. 공통 미션 표현

| Name | Type | Description |
| --- | --- | --- |
| `missionId` | String | 양의 10진 문자열인 미션 식별자 |
| `regionId` | String | 미션 운영 지역 식별자 |
| `status` | String | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 중 하나 |
| `conditionType` | String | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `requiredVisitCount` | Integer | `VISIT_COUNT` 목표 방문 수. `CONTENT_SET`이면 `null` |
| `targetContents` | Array | `CONTENT_SET` 대상 콘텐츠 목록. `VISIT_COUNT`이면 빈 배열 |
| `rewardCouponPolicyId` | String | 같은 지역의 `MISSION_REWARD` 쿠폰 정책 식별자 |
| `endsAt` | String | 미션 예정 종료 시각. `Asia/Seoul` 기준 ISO 8601 `+09:00` 오프셋 |
| `publishedAt` | String | 공개 승인 처리 시각. 공개 전이면 `null` |
| `endedAt` | String | 종료 처리 시각. 종료 전이면 `null` |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 내 미션 참여 목록 조회 | `GET /me/mission-participations` | [list-my-mission-participations.md](list-my-mission-participations.md) |
| 내 미션 참여·진행도 상세 조회 | `GET /me/mission-participations/{participationId}` | [get-my-mission-participation.md](get-my-mission-participation.md) |
| 미션 완료 보상 수령 | `POST /me/mission-participations/{participationId}/rewards/claim` | [claim-mission-reward.md](claim-mission-reward.md) |
| 지역별 공개 미션 목록 조회 | `GET /regions/{regionId}/missions` | [list-public-region-missions.md](list-public-region-missions.md) |
| 공개 미션 상세 조회 | `GET /missions/{missionId}` | [get-public-mission.md](get-public-mission.md) |
| 미션 참여 | `POST /missions/{missionId}/participations` | [create-mission-participation.md](create-mission-participation.md) |
| 미션 진행도 반영 | 내부 방문 완료 이벤트 | [record-mission-progress.md](record-mission-progress.md) |
| 지역 미션 생성 | `POST /operator/missions` | [create-operator-mission.md](create-operator-mission.md) |
| 지역 미션 수정 | `PATCH /operator/missions/{missionId}` | [update-operator-mission.md](update-operator-mission.md) |
| 내 미션 목록 조회 | `GET /operator/missions` | [list-operator-missions.md](list-operator-missions.md) |
| 내 미션 상세 조회 | `GET /operator/missions/{missionId}` | [get-operator-mission.md](get-operator-mission.md) |
| 미션 검토 요청 | `POST /operator/missions/{missionId}/submit` | [submit-operator-mission.md](submit-operator-mission.md) |
| 지역 미션 조기 종료 | `POST /operator/missions/{missionId}/end` | [end-operator-mission.md](end-operator-mission.md) |
| 지역 미션 목록 조회 | `GET /region-admin/missions` | [list-region-admin-missions.md](list-region-admin-missions.md) |
| 지역 미션 상세 조회 | `GET /region-admin/missions/{missionId}` | [get-region-admin-mission.md](get-region-admin-mission.md) |
| 미션 승인 | `POST /region-admin/missions/{missionId}/approve` | [approve-region-admin-mission.md](approve-region-admin-mission.md) |
| 미션 반려 | `POST /region-admin/missions/{missionId}/reject` | [reject-region-admin-mission.md](reject-region-admin-mission.md) |
| 미션 상태/운영 이력 조회 | `GET /region-admin/missions/{missionId}/history` | [get-region-admin-mission-history.md](get-region-admin-mission-history.md) |
| 미션 자동 공개 제외 | 지역 관리자 승인 API에서 즉시 공개 | [publish-missions.md](publish-missions.md) |
| 미션 자동 종료 | 내부 `scheduler` | [end-missions.md](end-missions.md) |
