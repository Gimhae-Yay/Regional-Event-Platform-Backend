import { fail } from 'k6';
import { Counter } from 'k6/metrics';

import {
  apiBaseUrl,
  csvEnv,
  env,
  numberEnv,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { pickByIteration, requireUniqueValuesAtLeast } from '../lib/data.js';
import { authHeaders, postJson, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'RESERVATION_HOLD_CONCURRENCY';
const testTag = 'reservation_hold_concurrency';
const endpoint = 'reservationCreateHold';
const businessCodes = [
  'RESERVATION_HOLD_CONFLICT',
];
const concurrencyVus = scenarioVus(scenarioName, 10);
const maxDuration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName);

if (!Number.isInteger(concurrencyVus) || concurrencyVus < 2) {
  fail(`PERF_${scenarioName}_VUS must be an integer greater than or equal to 2`);
}

const holdSuccessCount = new Counter('reservation_hold_success_count');
const holdConflictCount = new Counter('reservation_hold_conflict_count');

export const options = {
  scenarios: {
    reservation_hold_last_seat: {
      executor: 'per-vu-iterations',
      vus: concurrencyVus,
      iterations: 1,
      maxDuration,
    },
  },
  thresholds: {
    expected_outcome_rate: ['rate==1'],
    [`expected_outcome_rate{endpoint:${endpoint}}`]: ['rate==1'],
    reservation_hold_success_count: ['count==1'],
    reservation_hold_conflict_count: [`count==${concurrencyVus - 1}`],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95Threshold}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const visitorTokens = csvEnv('PERF_VISITOR_ACCESS_TOKENS');
const sessionId = requiredEnv('PERF_RESERVATION_HOLD_SESSION_ID');
const quantity = numberEnv('PERF_HOLD_QUANTITY', 1);
const commonTags = { test: testTag, session_id: sessionId };
const createHoldTags = requestTags(endpoint, 'POST /api/v1/reservations', commonTags);

requireUniqueValuesAtLeast(
  'PERF_VISITOR_ACCESS_TOKENS',
  visitorTokens,
  concurrencyVus,
  `PERF_${scenarioName}_VUS`,
);

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Reservation Hold Concurrency Summary',
    scenario: 'reservation-hold-concurrency',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: concurrencyVus,
    duration: maxDuration,
    visitorTokens: visitorTokens.length,
    sessionId,
  });
}

export default function () {
  const token = pickByIteration(visitorTokens);

  const outcome = recordOutcome(
    'POST /reservations',
    postJson(
      apiBase,
      '/reservations',
      { sessionId, quantity },
      authHeaders(token),
      createHoldTags,
    ),
    { successStatuses: [201], businessCodes, endpoint },
  );

  if (outcome.success) {
    holdSuccessCount.add(1, commonTags);
  } else if (outcome.code === 'RESERVATION_HOLD_CONFLICT') {
    holdConflictCount.add(1, commonTags);
  }
}
