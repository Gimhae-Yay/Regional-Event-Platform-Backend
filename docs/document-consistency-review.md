# 로컬스탬프 문서 정합성 검토

| 항목 | 내용 |
| --- | --- |
| 검토일 | 2026-07-22 |
| 검토 범위 | `docs/` 아래 Markdown 문서 |
| 검토 관점 | MVP 범위, 사용자 역할, 상태 모델, 결제·쿠폰 정책, 기술 스택, 문서 간 우선순위 |
| 검토 결과 | 핵심 MVP 방향은 대체로 일치하나 구현 기준에 영향을 줄 수 있는 정합성 문제 10건 확인 |

## 1. 정합성 문제

| 우선순위 | 문제 영역 | 문제가 있는 부분 | 영향 | 권장 정리 |
| --- | --- | --- | --- | --- |
| 높음 | 쿠폰 출시 순서 | [`local-stamp-platform-prd.md:20`](local-stamp-platform-prd.md#L20)은 기획 검토 문서가 쿠폰을 후속 단계로 제안했다고 설명하지만, [`local-stamp-platform-planning-review.md:11`](local-stamp-platform-planning-review.md#L11)은 이미 쿠폰을 MVP에 포함한다. | 문서 우선순위와 결정 이력이 잘못 전달된다. | PRD의 정합성 안내문을 삭제하거나 “모든 기준 문서가 쿠폰 MVP 포함에 합의함”으로 수정한다. |
| 높음 | MVP 공급자 범위 | [`local-engagement-stamp-reward-platform-personas.md:55`](local-engagement-stamp-reward-platform-personas.md#L55)은 지역 소상공인을 우선 공급자로 정의하고, [`local-engagement-stamp-reward-platform-personas.md:59`](local-engagement-stamp-reward-platform-personas.md#L59)은 음식점·카페·전통시장까지 초기 모집 대상으로 포함한다. 반면 [`local-stamp-platform-prd.md:108`](local-stamp-platform-prd.md#L108)과 [`local-stamp-pilot-scope-and-content-model-memo.md:5`](local-stamp-pilot-scope-and-content-model-memo.md#L5)는 예약형 행사·체험 운영자로 한정한다. | 개발 범위와 파트너 모집 범위가 달라질 수 있다. | 페르소나의 MVP 공급자를 예약형 행사·체험 운영자로 수정하고 일반 가게는 후속 대상으로 이동한다. |
| 높음 | 예약·결제 상태 분리 | [`local-stamp-platform-prd.md:281`](local-stamp-platform-prd.md#L281)은 결제와 예약 상태를 분리한다고 명시하지만, [`local-stamp-platform-prd.md:295`](local-stamp-platform-prd.md#L295)은 예약 상태에 `PAYMENT_IN_PROGRESS`, `PAYMENT_FAILED`, `REFUNDED`를 포함한다. | 도메인 모델과 상태 전이 구현이 충돌한다. | `ReservationStatus`, `PaymentStatus`, `Visit`을 별도 상태·엔터티로 정의한다. |
| 높음 | 무료 예약과 결제 모델 | [`local-stamp-platform-planning-review.md:19`](local-stamp-platform-planning-review.md#L19)은 무료·유료 예약을 모두 언급하고 [`local-stamp-platform-prd.md:462`](local-stamp-platform-prd.md#L462)는 미결정 사항으로 남겨둔다. 그러나 [`local-stamp-platform-prd.md:301`](local-stamp-platform-prd.md#L301)은 결제 성공을 예약 확정의 필수 조건으로 정의하고, [`local-stamp-pilot-scope-and-content-model-memo.md:70`](local-stamp-pilot-scope-and-content-model-memo.md#L70)은 예약과 결제를 1:1로 표현한다. | 무료 예약 처리와 결제 재시도 구조를 현재 모델로 명확히 표현할 수 없다. | 무료 예약의 `Payment` 생성 여부와 `Reservation 1:N PaymentAttempt` 필요 여부를 먼저 확정한다. |
| 중간 | 콘텐츠 상태 모델 | [`local-stamp-pilot-scope-and-content-model-memo.md:34`](local-stamp-pilot-scope-and-content-model-memo.md#L34)은 등록 요청·승인·공개·종료·반려를 사용하고, [`local-stamp-platform-proposal.md:253`](local-stamp-platform-proposal.md#L253)은 반려를 상태 목록에서 누락한다. [`local-stamp-platform-prd.md:294`](local-stamp-platform-prd.md#L294)의 `APPROVED/PUBLISHED`도 한 상태인지 두 상태인지 불명확하다. | 상태 enum과 API 동작이 문서마다 달라질 수 있다. | 단일 콘텐츠 상태 전이표와 상태 enum을 기준 문서에 정의한다. |
| 중간 | 오래된 기획 검토 내용 | [`local-stamp-platform-planning-review.md:37`](local-stamp-platform-planning-review.md#L37)은 상세 기획서가 모든 콘텐츠를 인증 대상으로 정의한다고 하지만, 상세 기획서는 이미 행사·체험으로 범위를 좁혔다. [`local-stamp-platform-planning-review.md:39`](local-stamp-platform-planning-review.md#L39)의 관리 화면 관련 설명도 이미 반영된 내용이다. | 해결된 문제가 현재 문제처럼 보인다. | 표의 `현재 서술`을 `변경 전 서술`로 바꾸고 적용 완료 상태를 표시한다. |
| 중간 | 쿠폰 발급 조건 | [`local-stamp-platform-proposal.md:153`](local-stamp-platform-proposal.md#L153)은 “정해진 조건”에 따라 발급한다고 하지만, [`local-stamp-platform-proposal.md:169`](local-stamp-platform-proposal.md#L169)과 [`local-stamp-platform-prd.md:284`](local-stamp-platform-prd.md#L284)은 모든 체크인에 1장 발급하는 것으로 보인다. 반면 [`local-stamp-platform-prd.md:398`](local-stamp-platform-prd.md#L398)은 정책 제외를 전제한다. | 쿠폰 발급률과 구현 조건이 달라질 수 있다. | “모든 정상 체크인에 1장” 또는 “정책 대상 체크인에만 1장” 중 하나로 통일한다. |
| 중간 | 기술 결정 확정 여부 | [`local-stamp-platform-tech-stack.md:11`](local-stamp-platform-tech-stack.md#L11)은 동시성 전략을 확정 기술처럼 표현하지만 [`local-stamp-platform-prd.md:463`](local-stamp-platform-prd.md#L463)은 최종 전략을 오픈 이슈로 둔다. Outbox도 [`local-stamp-platform-tech-stack.md:16`](local-stamp-platform-tech-stack.md#L16)은 MVP 스택으로 확정하지만 [`local-stamp-platform-planning-review.md:130`](local-stamp-platform-planning-review.md#L130)은 예약 흐름 안정화 후 검토 대상으로 둔다. | 구현 순서와 기술 선택 완료 여부가 불분명하다. | 기술별 상태를 `후보`, `선정`, `도입 예정`, `도입 완료`로 구분한다. |
| 중간 | 사용자 역할 누락 | [`local-stamp-platform-prd.md:114`](local-stamp-platform-prd.md#L114)과 [`local-stamp-platform-proposal.md:63`](local-stamp-platform-proposal.md#L63)은 `전체 관리자`를 MVP 역할에 포함하지만 [`local-stamp-platform-tech-stack.md:10`](local-stamp-platform-tech-stack.md#L10)은 방문자·운영자·지역 관리자만 언급한다. | 인증·인가 설계에서 전체 관리자 권한이 빠질 수 있다. | 모든 문서의 역할 집합을 `방문자`, `운영자`, `지역 관리자`, `전체 관리자`로 통일한다. |
| 낮음 | 문서 기준과 출처 | [`local-stamp-platform-prd.md:10`](local-stamp-platform-prd.md#L10)은 존재하지 않는 “문서 허브”를 언급하고, [`local-stamp-platform-prd.md:473`](local-stamp-platform-prd.md#L473)의 참고 자료에는 핵심 결정 문서인 파일럿 범위 메모가 빠져 있다. [`local-stamp-platform-planning-review.md:117`](local-stamp-platform-planning-review.md#L117)의 “참고 문서”도 무엇인지 알 수 없다. | 어느 문서가 최종 기준인지 판단하기 어렵다. | `docs/README.md`를 문서 허브로 만들고 문서별 역할·우선순위·상태·수정일을 기록한다. |

## 2. 일관되게 정리된 사항

- 첫 파일럿의 핵심 콘텐츠는 예약형 행사·체험이다.
- 가게·관광지 현장 인증은 후속 단계로 분리한다.
- 쿠폰은 MVP에 포함하고 스탬프·미션은 후속 단계로 분리한다.
- 현장에서는 운영자가 사용자의 일회성 예약 QR을 스캔한다.
- 최소 관리자 도구는 MVP에 포함하고 고도화된 대시보드는 제외한다.
- 문서의 로컬 링크는 모두 실제 파일을 가리킨다.

## 3. 권장 수정 우선순위

1. 예약·결제 상태 모델 분리
2. 페르소나의 MVP 공급자 및 콘텐츠 범위 정리
3. 무료 예약과 결제 재시도 정책 확정
4. PRD의 잘못된 쿠폰 결정 이력 수정
5. 콘텐츠 상태 모델 통일
6. 오래된 기획 검토 내용에 적용 완료 상태 표시
7. 쿠폰 발급 대상과 예외 조건 확정
8. 기술 선택의 확정·도입 상태 구분
9. 전체 관리자 역할 반영
10. 문서 허브 및 문서 우선순위 정의
