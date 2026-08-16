import { fail } from 'k6';
import { Counter } from 'k6/metrics';

import {
  apiBaseUrl,
  csvEnv,
  env,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { idempotencyKey } from '../lib/data.js';
import {
  authAcceptHeaders,
  authHeaders,
  get,
  postJson,
  requestTags,
} from '../lib/http.js';
import { recordOutcome, recordUnexpected } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'QR_CHECKIN_CONCURRENCY';
const testTag = 'qr_checkin_concurrency';
const setupTestTag = 'qr_checkin_concurrency_setup';
const endpoints = {
  issueQr: 'myReservationQrIssue',
  checkIn: 'qrCheckIn',
};
const businessCodes = [
  'QR_ISSUE_CONFLICT',
  'QR_VERIFICATION_FAILED',
  'IDEMPOTENCY_KEY_CONFLICT',
  'IDEMPOTENCY_REQUEST_IN_PROGRESS',
];
const concurrencyVus = scenarioVus(scenarioName, 50);
const maxDuration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName);

if (!Number.isInteger(concurrencyVus) || concurrencyVus < 2) {
  fail(`PERF_${scenarioName}_VUS must be an integer greater than or equal to 2`);
}

const qrCheckinSuccessCount = new Counter('qr_checkin_success_count');

export const options = {
  scenarios: {
    qr_checkin_same_reservation: {
      executor: 'per-vu-iterations',
      vus: concurrencyVus,
      iterations: 1,
      maxDuration,
    },
  },
  thresholds: {
    expected_outcome_rate: ['rate==1'],
    [`expected_outcome_rate{endpoint:${endpoints.issueQr}}`]: ['rate==1'],
    [`expected_outcome_rate{endpoint:${endpoints.checkIn}}`]: ['rate==1'],
    qr_checkin_success_count: [`count==${concurrencyVus}`],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.issueQr}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.checkIn}}`]: [`p(95)<${p95Threshold}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const visitorToken = csvEnv('PERF_VISITOR_ACCESS_TOKENS')[0];
const operatorToken = csvEnv('PERF_OPERATOR_ACCESS_TOKENS')[0];
const reservationId = requiredEnv('PERF_RESERVATION_ID');
const commonTags = { test: testTag, reservation_id: reservationId };
const setupTags = requestTags(
  endpoints.issueQr,
  'GET /api/v1/me/reservations/{reservationId}/qr',
  { test: setupTestTag, reservation_id: reservationId, phase: 'setup' },
);
const checkInTags = requestTags(
  endpoints.checkIn,
  'POST /api/v1/operator/check-ins',
  commonTags,
);

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 QR Check-in Concurrency Summary',
    scenario: 'qr-checkin-concurrency',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: concurrencyVus,
    duration: maxDuration,
    visitorTokens: 1,
    operatorTokens: 1,
    reservationId,
  });
}

export function setup() {
  const qrOutcome = recordOutcome(
    'GET /me/reservations/{reservationId}/qr',
    get(
      apiBase,
      `/me/reservations/${reservationId}/qr`,
      authAcceptHeaders(visitorToken),
      setupTags,
    ),
    { businessCodes, endpoint: endpoints.issueQr },
  );
  if (!qrOutcome.success) {
    throw new Error('QR setup failed. Check PERF_RESERVATION_ID and check-in window fixture.');
  }

  const qrToken = qrOutcome.body && qrOutcome.body.data && qrOutcome.body.data.qrToken;
  if (!qrToken) {
    recordUnexpected(
      'GET /me/reservations/{reservationId}/qr',
      'MISSING_QR_TOKEN',
      { endpoint: endpoints.issueQr },
    );
    throw new Error('QR setup response did not include qrToken.');
  }
  return { qrToken };
}

export default function (data) {
  const outcome = recordOutcome(
    'POST /operator/check-ins',
    postJson(
      apiBase,
      '/operator/check-ins',
      { qrToken: data.qrToken },
      authHeaders(operatorToken, { 'Idempotency-Key': idempotencyKey('qr-check-in') }),
      checkInTags,
    ),
    { businessCodes, endpoint: endpoints.checkIn },
  );
  if (outcome.success) {
    qrCheckinSuccessCount.add(1, commonTags);
  }
}
