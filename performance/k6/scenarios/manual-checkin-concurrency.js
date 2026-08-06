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
import { authHeaders, postJson } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'MANUAL_CHECKIN_CONCURRENCY';
const testTag = 'manual_checkin_concurrency';
const businessCodes = [
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
const operatorToken = csvEnv('PERF_OPERATOR_ACCESS_TOKENS')[0];
const reservationNo = requiredEnv('PERF_RESERVATION_NO');
const reason = env('PERF_MANUAL_CHECKIN_REASON', 'QR_SCAN_FAILED');
const commonTags = { test: testTag };

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Manual Check-in Concurrency Summary',
    scenario: 'manual-checkin-concurrency',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: options.vus,
    duration: options.duration,
    operatorTokens: 1,
    reservationNo,
  });
}

export default function () {
  recordOutcome(
    'POST /operator/check-ins/manual',
    postJson(
      apiBase,
      '/operator/check-ins/manual',
      { reservationNo, reason },
      authHeaders(operatorToken, { 'Idempotency-Key': idempotencyKey('manual-check-in') }),
      commonTags,
    ),
    { businessCodes },
  );
}
