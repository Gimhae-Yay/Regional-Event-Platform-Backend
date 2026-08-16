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

const scenarioName = 'PUBLIC_API_RESPONSE_TIME';
const testTag = 'public_api_response_time';
const p95Threshold = scenarioP95Threshold(scenarioName);
const expectedRateThreshold = minExpectedOutcomeRate();
const endpoints = {
  publicRegions: 'publicRegions',
  regionHome: 'regionHome',
  publicContents: 'publicContents',
  publicContentDetail: 'publicContentDetail',
  publicContentSessions: 'publicContentSessions',
  publicContentReviews: 'publicContentReviews',
  sessionReservationInfo: 'sessionReservationInfo',
  publicRegionMissions: 'publicRegionMissions',
  publicMissionDetail: 'publicMissionDetail',
};

export const options = {
  vus: scenarioVus(scenarioName),
  duration: scenarioDuration(scenarioName),
  thresholds: {
    expected_outcome_rate: [`rate>=${expectedRateThreshold}`],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${p95Threshold}`],
    ...endpointThresholds(),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const regionId = requiredEnv('PERF_REGION_ID');
const contentId = requiredEnv('PERF_CONTENT_ID');
const sessionId = requiredEnv('PERF_SESSION_ID');
const missionId = requiredEnv('PERF_MISSION_ID');
const reservationAvailable = env('PERF_RESERVATION_AVAILABLE', 'true');
const commonTags = { test: testTag };
const publicRegionsTags = requestTags(endpoints.publicRegions, 'GET /api/v1/regions', commonTags);
const regionHomeTags = requestTags(endpoints.regionHome, 'GET /api/v1/regions/{regionId}/home', commonTags);
const publicContentsTags = requestTags(endpoints.publicContents, 'GET /api/v1/contents', commonTags);
const publicContentDetailTags = requestTags(
  endpoints.publicContentDetail,
  'GET /api/v1/contents/{contentId}',
  commonTags,
);
const publicContentSessionsTags = requestTags(
  endpoints.publicContentSessions,
  'GET /api/v1/contents/{contentId}/sessions',
  commonTags,
);
const publicContentReviewsTags = requestTags(
  endpoints.publicContentReviews,
  'GET /api/v1/contents/{contentId}/reviews',
  commonTags,
);
const sessionReservationInfoTags = requestTags(
  endpoints.sessionReservationInfo,
  'GET /api/v1/sessions/{sessionId}',
  commonTags,
);
const publicRegionMissionsTags = requestTags(
  endpoints.publicRegionMissions,
  'GET /api/v1/regions/{regionId}/missions',
  commonTags,
);
const publicMissionDetailTags = requestTags(
  endpoints.publicMissionDetail,
  'GET /api/v1/missions/{missionId}',
  commonTags,
);

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Public API Response Time Summary',
    scenario: 'public-api-response-time',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: options.vus,
    duration: options.duration,
    contentId,
    sessionId,
  });
}

export default function () {
  recordOutcome(
    'GET /regions',
    get(apiBase, '/regions', {}, publicRegionsTags),
    { endpoint: endpoints.publicRegions },
  );
  recordOutcome(
    'GET /regions/{regionId}/home',
    get(apiBase, `/regions/${regionId}/home`, {}, regionHomeTags),
    { endpoint: endpoints.regionHome },
  );
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
      publicContentsTags,
    ),
    { endpoint: endpoints.publicContents },
  );
  recordOutcome(
    'GET /contents/{contentId}',
    get(apiBase, `/contents/${contentId}`, {}, publicContentDetailTags),
    { endpoint: endpoints.publicContentDetail },
  );
  recordOutcome(
    'GET /contents/{contentId}/sessions',
    get(apiBase, `/contents/${contentId}/sessions`, {}, publicContentSessionsTags),
    { endpoint: endpoints.publicContentSessions },
  );
  recordOutcome(
    'GET /contents/{contentId}/reviews',
    get(
      apiBase,
      `/contents/${contentId}/reviews${query({ page: 0, size: 20 })}`,
      {},
      publicContentReviewsTags,
    ),
    { endpoint: endpoints.publicContentReviews },
  );
  recordOutcome(
    'GET /sessions/{sessionId}',
    get(apiBase, `/sessions/${sessionId}`, {}, sessionReservationInfoTags),
    { endpoint: endpoints.sessionReservationInfo },
  );
  recordOutcome(
    'GET /regions/{regionId}/missions',
    get(
      apiBase,
      `/regions/${regionId}/missions${query({
        page: env('PERF_MISSION_PAGE', '0'),
        size: env('PERF_MISSION_SIZE', '20'),
      })}`,
      {},
      publicRegionMissionsTags,
    ),
    { endpoint: endpoints.publicRegionMissions },
  );
  recordOutcome(
    'GET /missions/{missionId}',
    get(apiBase, `/missions/${missionId}`, {}, publicMissionDetailTags),
    { endpoint: endpoints.publicMissionDetail },
  );
}

function endpointThresholds() {
  return Object.values(endpoints).reduce((thresholds, endpoint) => {
    thresholds[`expected_outcome_rate{endpoint:${endpoint}}`] = [`rate>=${expectedRateThreshold}`];
    thresholds[`http_req_duration{endpoint:${endpoint}}`] = [`p(95)<${p95Threshold}`];
    return thresholds;
  }, {});
}
