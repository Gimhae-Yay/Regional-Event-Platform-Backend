import {
  apiBaseUrl,
  csvEnv,
  env,
  minExpectedOutcomeRate,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { idempotencyKey } from '../lib/data.js';
import { authAcceptHeaders, authHeaders, get, postJson } from '../lib/http.js';
import { recordOutcome, recordUnexpected } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'QR_CHECKIN_CONCURRENCY';
const testTag = 'qr_checkin_concurrency';
const businessCodes = [
  'QR_ISSUE_CONFLICT',
  'QR_VERIFICATION_FAILED',
  'CHECK_IN_CONFLICT',
  'IDEMPOTENCY_KEY_CONFLICT',
  'IDEMPOTENCY_REQUEST_IN_PROGRESS',
];

export const options = {
  vus: scenarioVus(scenarioName),
  duration: scenarioDuration(scenarioName),
  thresholds: {
    expected_outcome_rate: [`rate>=${minExpectedOutcomeRate()}`],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${scenarioP95Threshold(scenarioName)}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const visitorToken = csvEnv('PERF_VISITOR_ACCESS_TOKENS')[0];
const operatorToken = csvEnv('PERF_OPERATOR_ACCESS_TOKENS')[0];
const reservationId = requiredEnv('PERF_RESERVATION_ID');
const commonTags = { test: testTag, reservation_id: reservationId };

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 QR Check-in Concurrency Summary',
    scenario: 'qr-checkin-concurrency',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: options.vus,
    duration: options.duration,
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
      commonTags,
    ),
    { businessCodes },
  );
  if (!qrOutcome.success) {
    throw new Error('QR setup failed. Check PERF_RESERVATION_ID and check-in window fixture.');
  }

  const qrToken = qrOutcome.body && qrOutcome.body.data && qrOutcome.body.data.qrToken;
  if (!qrToken) {
    recordUnexpected('GET /me/reservations/{reservationId}/qr', 'MISSING_QR_TOKEN');
    throw new Error('QR setup response did not include qrToken.');
  }
  return { qrToken };
}

export default function (data) {
  recordOutcome(
    'POST /operator/check-ins',
    postJson(
      apiBase,
      '/operator/check-ins',
      { qrToken: data.qrToken },
      authHeaders(operatorToken, { 'Idempotency-Key': idempotencyKey('qr-check-in') }),
      commonTags,
    ),
    { businessCodes },
  );
}
