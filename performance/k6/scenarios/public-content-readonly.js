import {
  apiBaseUrl,
  env,
  minExpectedOutcomeRate,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { get, query, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'PUBLIC_CONTENT_READONLY';
const testTag = 'public_content_readonly';
const p95Threshold = scenarioP95Threshold(scenarioName);
const expectedRateThreshold = minExpectedOutcomeRate();
const endpoints = {
  list: 'publicContentsList',
  detail: 'publicContentDetail',
  sessions: 'publicContentSessions',
};

export const options = {
  vus: scenarioVus(scenarioName),
  duration: scenarioDuration(scenarioName),
  thresholds: {
    expected_outcome_rate: [`rate>=${expectedRateThreshold}`],
    [`expected_outcome_rate{endpoint:${endpoints.list}}`]: [`rate>=${expectedRateThreshold}`],
    [`expected_outcome_rate{endpoint:${endpoints.detail}}`]: [`rate>=${expectedRateThreshold}`],
    [`expected_outcome_rate{endpoint:${endpoints.sessions}}`]: [`rate>=${expectedRateThreshold}`],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.list}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.detail}}`]: [`p(95)<${p95Threshold}`],
    [`http_req_duration{endpoint:${endpoints.sessions}}`]: [`p(95)<${p95Threshold}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const regionId = requiredEnv('PERF_REGION_ID');
const contentId = requiredEnv('PERF_CONTENT_ID');
const reservationAvailable = env('PERF_RESERVATION_AVAILABLE', 'true');
const commonTags = { test: testTag };
const listTags = requestTags(endpoints.list, 'GET /api/v1/contents', commonTags);
const detailTags = requestTags(
  endpoints.detail,
  'GET /api/v1/contents/{contentId}',
  commonTags,
);
const sessionsTags = requestTags(
  endpoints.sessions,
  'GET /api/v1/contents/{contentId}/sessions',
  commonTags,
);

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
      listTags,
    ),
    { endpoint: endpoints.list },
  );

  recordOutcome(
    'GET /contents/{contentId}',
    get(apiBase, `/contents/${contentId}`, {}, detailTags),
    { endpoint: endpoints.detail },
  );

  recordOutcome(
    'GET /contents/{contentId}/sessions',
    get(apiBase, `/contents/${contentId}/sessions`, {}, sessionsTags),
    { endpoint: endpoints.sessions },
  );
}
