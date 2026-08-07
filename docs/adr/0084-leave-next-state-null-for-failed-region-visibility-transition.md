# ADR-0084: 실패한 지역 공개 여부 전환의 다음 상태를 비워 둔다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-07
- 결정일: 2026-08-07
- 관련 요구사항: [전체관리자](../p1/platform-admin.md)의 `P1-FR-09`, `ADM-02`, `ADM-05`
- 관련 단계: 단계 3. 확장 의사결정
- 관련 이슈: 없음
- 대체 대상: [ADR-0082](0082-store-evidence-reference-in-region-visibility-failure-audits.md)의 실패 감사 현재·목표 공개 여부 저장 범위

## 맥락

[ADR-0082](0082-store-evidence-reference-in-region-visibility-failure-audits.md)은 비삭제 콘텐츠 조건으로 거부된
지역 공개 여부 변경의 실패 감사에 현재 공개 여부와 요청 목표를 포함하도록 정했지만, 이를
`audit_event.previous_state`와 `next_state`에 어떻게 매핑할지는 확정하지 않았다.

P0 `audit_event`는 상태 전이 기준 기록이며, [ADR-0063](0063-record-failed-session-creation-after-ended-content-check.md)은
상태가 바뀌지 않은 실패 감사에서 확인한 현재 상태를 `previous_state`에 저장하고 `next_state = NULL`을 사용한다.
지역 전환 실패에서 `next_state = false`를 저장하면 실제로 전이하지 않은 값을 다음 상태로 기록해 성공 전이와 같은
형태로 오해할 수 있다.

## 결정 동인과 불변 조건

- 실패 감사는 실제 지역 상태가 변경되지 않았음을 필드 자체로 표현해야 한다.
- 성공·실패 감사의 `next_state` 의미를 다르게 사용하지 않아야 한다.
- 현재 확인한 상태와 서버 실패 원인만으로 거부 사건을 재현할 수 있어야 한다.
- 공통 감사 스키마에 시도 목표 상태를 위한 새 열을 추가하지 않아야 한다.
- 실패 감사의 증빙·처리자·트랜잭션·응답 계약은 ADR-0082·0083을 유지해야 한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 추천안: `previous_state = true`, `next_state = NULL` | 실제 상태 미변경을 명확히 표현하고 기존 실패 감사 선례와 공통 의미를 유지한다. | 요청 목표 `false`가 별도 필드에 직접 남지 않는다. | 낮음. 문서와 매핑 테스트만 확정하면 된다. | 실패 코드가 `true → false` 거부에만 쓰이는 현재 P1 계약에 적합하다. |
| 2 | `previous_state = true`, `next_state = false` | 요청한 목표 상태를 감사 행에서 직접 볼 수 있다. | 실패 이벤트의 `next_state`가 실제 결과가 아닌 시도 값이 되어 성공 감사와 의미가 달라지고 공통 조회·집계가 오해할 수 있다. | 중간. 공통 ERD와 모든 감사 소비자의 의미를 함께 바꿔야 한다. | 시도 상태 전용 모델이 없는 현재 감사 구조에 부적합하다. |

## 결정

비삭제 콘텐츠 조건으로 `409 REGION_AVAILABILITY_CONFLICT`가 발생한 지역 공개 여부 변경의 실패 감사는 다음 상태
필드를 사용한다.

| 필드 | 값 |
| --- | --- |
| `previous_state` | `true` |
| `next_state` | `NULL` |
| `result` | `FAILURE` |
| `reason_code` | `REGION_AVAILABILITY_CONFLICT` |

`next_state = NULL`은 지역이 요청 목표 상태로 전이하지 않았다는 뜻이다. 이 실패 코드는 현재 계약에서 비삭제
콘텐츠가 있는 공개 지역의 `true → false` 요청에만 사용하므로 요청 목표 `false`는 오류 코드와 API 문맥에서
추론한다. 요청 목표를 `next_state`에 저장하지 않는다.

ADR-0082의 검증된 요청 `evidence_reference`, 처리자·대상·시각·`requestId` 저장과 ADR-0083의 감사 저장 장애
처리는 변경하지 않는다. 실제 상태 전이에 성공한 감사는 이전·이후 `is_public` 값을 각각 `previous_state`,
`next_state`에 계속 저장한다.

## 결과와 트레이드오프

### 기대 효과

- 실패 감사 행만 보고도 실제 다음 상태가 없음을 구분할 수 있다.
- 기존 P0 실패 감사와 같은 `next_state = NULL` 의미를 유지한다.
- 성공 전이 집계가 실패 시도 목표를 실제 전이로 잘못 계산하지 않는다.

### 수용한 단점과 위험

- 감사 행에 요청 목표를 위한 별도 필드가 없으므로 오류 코드와 API 계약을 함께 해석해야 한다.
- 향후 같은 오류 코드가 다른 목표 상태에도 사용되면 현재 추론 규칙을 재검토해야 한다.

## 전환과 롤백

P1 지역 공개 여부 변경 API가 구현 전이므로 기존 감사 데이터 이관은 없다. API 명세, P1 ERD, 정책과 테스트에
명시적 필드 매핑을 반영한 뒤 구현한다. 공통 `audit_event` 스키마와 기존 실패 감사 데이터는 변경하지 않는다.

요청 목표 상태 자체를 독립적으로 검색해야 하면 이 ADR을 수정하지 않고 후속 ADR에서 `attempted_state` 같은 별도
필드 또는 별도 시도 이벤트 모델, 기존 소비자 호환성과 데이터 이관을 함께 정한다.

## 검증 방법

- 비삭제 콘텐츠가 있는 공개 지역의 `true → false` 요청이 409이고 지역 상태가 `true`로 유지되는지 확인한다.
- 실패 감사가 `previous_state = true`, `next_state = NULL`, `result = FAILURE`인지 확인한다.
- 실패 감사의 서버 코드, 증빙 참조, 처리자·대상·시각·`requestId`가 ADR-0082와 일치하는지 확인한다.
- 성공한 `false → true`, `true → false` 전이는 실제 이전·이후 값을 `previous_state`, `next_state`에 저장하는지 확인한다.
- 감사 조회·집계가 `result = SUCCESS`인 행만 실제 전이로 계산하는지 확인한다.

## 대체 조건

- 실패 요청의 목표 상태를 오류 코드와 무관하게 독립적으로 검색해야 한다.
- `REGION_AVAILABILITY_CONFLICT`가 `true → false` 외의 상태 조건 실패에도 사용된다.
- 공통 감사 모델에 시도 상태를 위한 별도 필드나 이벤트 유형이 도입된다.
