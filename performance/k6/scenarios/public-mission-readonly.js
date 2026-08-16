import { apiBaseUrl, env, minExpectedOutcomeRate, requiredEnv, scenarioDuration, scenarioP95Threshold, scenarioVus } from '../lib/config.js';
import { get, query, requestTags } from '../lib/http.js';
import { recordOutcome, recordUnexpected } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'PUBLIC_MISSION_READONLY';
const testTag = 'public_mission_readonly';
const endpoints = { list: 'publicRegionMissions', detail: 'publicMissionDetail' };
const vus = scenarioVus(scenarioName);
const duration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName);
const expectedRate = minExpectedOutcomeRate();

export const options = {
  vus, duration,
  thresholds: { expected_outcome_rate: [`rate>=${expectedRate}`], [`expected_outcome_rate{endpoint:${endpoints.list}}`]: [`rate>=${expectedRate}`], [`expected_outcome_rate{endpoint:${endpoints.detail}}`]: [`rate>=${expectedRate}`], system_failure_rate: ['rate==0'], unexpected_failure_rate: ['rate==0'], [`http_req_duration{endpoint:${endpoints.list}}`]: [`p(95)<${p95Threshold}`], [`http_req_duration{endpoint:${endpoints.detail}}`]: [`p(95)<${p95Threshold}`] },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const regionId = requiredEnv('PERF_REGION_ID');
const missionId = requiredEnv('PERF_MISSION_ID');
const listTags = requestTags(endpoints.list, 'GET /api/v1/regions/{regionId}/missions', { test: testTag });
const detailTags = requestTags(endpoints.detail, 'GET /api/v1/missions/{missionId}', { test: testTag });

export function handleSummary(data) { return markdownSummary(data, { title: 'k6 Public Mission Readonly Summary', scenario: 'public-mission-readonly', testTag, apiBase, vus, duration }); }

export default function () {
  const listOutcome = recordOutcome(
    'GET /regions/{regionId}/missions',
    get(
      apiBase,
      `/regions/${regionId}/missions${query({
        page: env('PERF_MISSION_PAGE', '0'),
        size: env('PERF_MISSION_SIZE', '20'),
      })}`,
      {},
      listTags,
    ),
    { endpoint: endpoints.list },
  );
  const missions = listOutcome.body && listOutcome.body.data && listOutcome.body.data.content;
  if (listOutcome.success && (!Array.isArray(missions)
      || !missions.some((mission) => String(mission.missionId) === missionId))) {
    recordUnexpected(
      'GET /regions/{regionId}/missions',
      'EXPECTED_MISSION_MISSING',
      { endpoint: endpoints.list },
    );
  }

  const detailOutcome = recordOutcome(
    'GET /missions/{missionId}',
    get(apiBase, `/missions/${missionId}`, {}, detailTags),
    { endpoint: endpoints.detail },
  );
  const detail = detailOutcome.body && detailOutcome.body.data;
  if (detailOutcome.success && (!detail || String(detail.missionId) !== missionId)) {
    recordUnexpected(
      'GET /missions/{missionId}',
      'MISSION_ID_MISMATCH',
      { endpoint: endpoints.detail },
    );
  }
}
