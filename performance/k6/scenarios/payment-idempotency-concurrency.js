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
import { authHeaders, postJson, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'PAYMENT_IDEMPOTENCY_CONCURRENCY';
const testTag = 'payment_idempotency_concurrency';
const endpoint = 'paymentCreate';
const vus = scenarioVus(scenarioName, 2);
const duration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName);
const successCount = new Counter('payment_create_success_count');
const inProgressCount = new Counter('payment_create_in_progress_count');

if (!Number.isInteger(vus) || vus < 2) {
  fail(`PERF_${scenarioName}_VUS must be an integer greater than or equal to 2`);
}

export const options = {
  scenarios: {
    payment_same_command: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: duration },
  },
  thresholds: {
    expected_outcome_rate: ['rate==1'],
    [`expected_outcome_rate{endpoint:${endpoint}}`]: ['rate==1'],
    payment_create_success_count: ['count>=1'],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95Threshold}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const token = csvEnv('PERF_VISITOR_ACCESS_TOKENS')[0];
const holdId = requiredEnv('PERF_PAYMENT_HOLD_ID');
const idempotencyKey = requiredEnv('PERF_PAYMENT_IDEMPOTENCY_KEY');
const couponId = env('PERF_PAYMENT_COUPON_ID', '');
const tags = requestTags(endpoint, 'POST /api/v1/me/reservation-holds/{holdId}/payments', { test: testTag });

export function handleSummary(data) {
  return markdownSummary(data, { title: 'k6 Payment Idempotency Concurrency Summary', scenario: 'payment-idempotency-concurrency', testTag, apiBase, vus, duration });
}

export default function () {
  const outcome = recordOutcome(
    'POST /me/reservation-holds/{holdId}/payments',
    postJson(apiBase, `/me/reservation-holds/${holdId}/payments`, { couponId: couponId || null }, authHeaders(token, { 'Idempotency-Key': idempotencyKey }), tags),
    { successStatuses: [201], businessCodes: ['IDEMPOTENCY_REQUEST_IN_PROGRESS'], endpoint },
  );
  if (outcome.success) {
    successCount.add(1, tags);
  } else if (outcome.code === 'IDEMPOTENCY_REQUEST_IN_PROGRESS') {
    inProgressCount.add(1, tags);
  }
}
