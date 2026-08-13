import { Counter } from 'k6/metrics';

import {
  apiBaseUrl,
  csvEnv,
  env,
  minExpectedOutcomeRate,
  numberEnv,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { idempotencyKey, pickByIteration } from '../lib/data.js';
import {
  authAcceptHeaders,
  authHeaders,
  get,
  postJson,
  postNoBody,
  requestTags,
} from '../lib/http.js';
import { recordOutcome, recordUnexpected } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'RESERVATION_FLOW';
const testTag = 'reservation_flow';
const p95Threshold = scenarioP95Threshold(scenarioName);
const expectedRateThreshold = minExpectedOutcomeRate();
const endpoints = {
  createHold: 'reservationCreateHold',
  confirm: 'reservationConfirm',
  myReservations: 'myReservationsList',
};
const businessCodes = [
  'RESERVATION_HOLD_CONFLICT',
  'RESERVATION_CONFIRM_CONFLICT',
  'IDEMPOTENCY_KEY_CONFLICT',
  'IDEMPOTENCY_REQUEST_IN_PROGRESS',
];
const reservationFlowSuccessCount = new Counter('reservation_flow_success_count');

export const options = {
  vus: scenarioVus(scenarioName),
  duration: scenarioDuration(scenarioName),
  thresholds: {
    expected_outcome_rate: [`rate>=${expectedRateThreshold}`],
    [`expected_outcome_rate{endpoint:${endpoints.createHold}}`]: [`rate>=${expectedRateThreshold}`],
    [`expected_outcome_rate{endpoint:${endpoints.confirm}}`]: [`rate>=${expectedRateThreshold}`],
    [`expected_outcome_rate{endpoint:${endpoints.myReservations}}`]: [`rate>=${expectedRateThreshold}`],
    reservation_flow_success_count: ['count>0'],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.createHold}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.confirm}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.myReservations}}`]: [`p(95)<${p95Threshold}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const visitorTokens = csvEnv('PERF_VISITOR_ACCESS_TOKENS');
const sessionId = requiredEnv('PERF_SESSION_ID');
const quantity = numberEnv('PERF_HOLD_QUANTITY', 1);
const commonTags = { test: testTag };
const createHoldTags = requestTags(
  endpoints.createHold,
  'POST /api/v1/reservations',
  commonTags,
);
const confirmTags = requestTags(
  endpoints.confirm,
  'POST /api/v1/reservation-holds/{holdId}/confirm',
  commonTags,
);
const myReservationsTags = requestTags(
  endpoints.myReservations,
  'GET /api/v1/me/reservations',
  commonTags,
);

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Reservation Flow Summary',
    scenario: 'reservation-flow',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: options.vus,
    duration: options.duration,
    visitorTokens: visitorTokens.length,
    sessionId,
  });
}

export default function () {
  const token = pickByIteration(visitorTokens);

  const holdOutcome = recordOutcome(
    'POST /reservations',
    postJson(
      apiBase,
      '/reservations',
      { sessionId, quantity },
      authHeaders(token),
      createHoldTags,
    ),
    { successStatuses: [201], businessCodes, endpoint: endpoints.createHold },
  );
  if (!holdOutcome.success) {
    return;
  }

  const holdId = holdOutcome.body && holdOutcome.body.data && holdOutcome.body.data.holdId;
  if (!holdId) {
    recordUnexpected(
      'POST /reservations',
      'MISSING_HOLD_ID',
      { endpoint: endpoints.createHold },
    );
    return;
  }

  const confirmOutcome = recordOutcome(
    'POST /reservation-holds/{holdId}/confirm',
    postNoBody(
      apiBase,
      `/reservation-holds/${holdId}/confirm`,
      authAcceptHeaders(token, { 'Idempotency-Key': idempotencyKey('reservation-confirm') }),
      confirmTags,
    ),
    { successStatuses: [201], businessCodes, endpoint: endpoints.confirm },
  );
  if (!confirmOutcome.success) {
    return;
  }

  const listOutcome = recordOutcome(
    'GET /me/reservations',
    get(apiBase, '/me/reservations', authAcceptHeaders(token), myReservationsTags),
    { endpoint: endpoints.myReservations },
  );
  if (listOutcome.success) {
    reservationFlowSuccessCount.add(1, commonTags);
  }
}
