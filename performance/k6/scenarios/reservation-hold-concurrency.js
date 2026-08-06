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
import { pickByIteration } from '../lib/data.js';
import { authHeaders, postJson } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'RESERVATION_HOLD_CONCURRENCY';
const testTag = 'reservation_hold_concurrency';
const businessCodes = [
  'RESERVATION_HOLD_CONFLICT',
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
const commonTags = { test: testTag, session_id: sessionId };

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Reservation Hold Concurrency Summary',
    scenario: 'reservation-hold-concurrency',
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

  recordOutcome(
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
}
