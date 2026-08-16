import { Counter } from 'k6/metrics';
import { fail } from 'k6';

import { apiBaseUrl, csvEnv, requiredEnv, scenarioDuration, scenarioP95Threshold, scenarioVus } from '../lib/config.js';
import { authAcceptHeaders, postNoBody, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'PAID_RESERVATION_CANCEL_CONCURRENCY';
const testTag = 'paid_reservation_cancel_concurrency';
const endpoint = 'paidReservationCancel';
const vus = scenarioVus(scenarioName, 2);
const duration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName, 35000);
const successCount = new Counter('paid_reservation_cancel_success_count');

if (!Number.isInteger(vus) || vus < 2) {
  fail(`PERF_${scenarioName}_VUS must be an integer greater than or equal to 2`);
}

export const options = {
  scenarios: { cancel_same_reservation: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: duration } },
  thresholds: {
    expected_outcome_rate: ['rate==1'],
    [`expected_outcome_rate{endpoint:${endpoint}}`]: ['rate==1'],
    paid_reservation_cancel_success_count: [`count==${vus}`],
    system_failure_rate: ['rate==0'], unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95Threshold}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const token = csvEnv('PERF_VISITOR_ACCESS_TOKENS')[0];
const reservationId = requiredEnv('PERF_PAID_RESERVATION_ID');
const tags = requestTags(endpoint, 'POST /api/v1/me/reservations/{reservationId}/cancel', { test: testTag });

export function handleSummary(data) {
  return markdownSummary(data, { title: 'k6 Paid Reservation Cancel Concurrency Summary', scenario: 'paid-reservation-cancel-concurrency', testTag, apiBase, vus, duration, reservationId });
}

export default function () {
  const outcome = recordOutcome('POST /me/reservations/{reservationId}/cancel', postNoBody(apiBase, `/me/reservations/${reservationId}/cancel`, authAcceptHeaders(token), tags), { businessCodes: ['RESERVATION_CANCEL_CONFLICT'], endpoint });
  if (outcome.success) successCount.add(1, tags);
}
