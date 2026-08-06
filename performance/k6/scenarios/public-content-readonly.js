import {
  apiBaseUrl,
  env,
  minExpectedOutcomeRate,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { get, query } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'PUBLIC_CONTENT_READONLY';
const testTag = 'public_content_readonly';

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
const regionId = requiredEnv('PERF_REGION_ID');
const contentId = requiredEnv('PERF_CONTENT_ID');
const reservationAvailable = env('PERF_RESERVATION_AVAILABLE', 'true');
const commonTags = { test: testTag };

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Public Content Readonly Summary',
    scenario: 'public-content-readonly',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: options.vus,
    duration: options.duration,
    contentId,
  });
}

export default function () {
  recordOutcome(
    'GET /contents',
    get(
      apiBase,
      `/contents${query({
        regionId,
        contentType: 'EVENT_EXPERIENCE',
        reservationAvailable,
      })}`,
      {},
      commonTags,
    ),
  );

  recordOutcome(
    'GET /contents/{contentId}',
    get(apiBase, `/contents/${contentId}`, {}, commonTags),
  );

  recordOutcome(
    'GET /contents/{contentId}/sessions',
    get(apiBase, `/contents/${contentId}/sessions`, {}, commonTags),
  );
}
