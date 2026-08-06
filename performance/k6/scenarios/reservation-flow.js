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
import { authAcceptHeaders, authHeaders, get, postJson, postNoBody } from '../lib/http.js';
import { recordOutcome, recordUnexpected } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'RESERVATION_FLOW';
const testTag = 'reservation_flow';
const businessCodes = [
  'RESERVATION_HOLD_CONFLICT',
  'RESERVATION_CONFIRM_CONFLICT',
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
const visitorTokens = csvEnv('PERF_VISITOR_ACCESS_TOKENS');
const sessionId = requiredEnv('PERF_SESSION_ID');
const quantity = numberEnv('PERF_HOLD_QUANTITY', 1);
const commonTags = { test: testTag };

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
      commonTags,
    ),
    { successStatuses: [201], businessCodes },
  );
  if (!holdOutcome.success) {
    return;
  }

  const holdId = holdOutcome.body && holdOutcome.body.data && holdOutcome.body.data.holdId;
  if (!holdId) {
    recordUnexpected('POST /reservations', 'MISSING_HOLD_ID');
    return;
  }

  const confirmOutcome = recordOutcome(
    'POST /reservation-holds/{holdId}/confirm',
    postNoBody(
      apiBase,
      `/reservation-holds/${holdId}/confirm`,
      authAcceptHeaders(token, { 'Idempotency-Key': idempotencyKey('reservation-confirm') }),
      commonTags,
    ),
    { successStatuses: [201], businessCodes },
  );
  if (!confirmOutcome.success) {
    return;
  }

  recordOutcome(
    'GET /me/reservations',
    get(apiBase, '/me/reservations', authAcceptHeaders(token), commonTags),
  );
}
