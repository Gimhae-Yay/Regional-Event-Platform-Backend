import { Counter } from 'k6/metrics';
import { fail } from 'k6';

import { apiBaseUrl, csvEnv, requiredEnv, scenarioDuration, scenarioP95Threshold, scenarioVus } from '../lib/config.js';
import { authAcceptHeaders, postNoBody, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'MISSION_REWARD_CLAIM_CONCURRENCY';
const testTag = 'mission_reward_claim_concurrency';
const endpoint = 'missionRewardClaim';
const vus = scenarioVus(scenarioName, 2);
const duration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName);
const successCount = new Counter('mission_reward_claim_success_count');

if (!Number.isInteger(vus) || vus < 2) {
  fail(`PERF_${scenarioName}_VUS must be an integer greater than or equal to 2`);
}

export const options = {
  scenarios: { claim_same_reward: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: duration } },
  thresholds: { expected_outcome_rate: ['rate==1'], [`expected_outcome_rate{endpoint:${endpoint}}`]: ['rate==1'], mission_reward_claim_success_count: [`count==${vus}`], system_failure_rate: ['rate==0'], unexpected_failure_rate: ['rate==0'], [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95Threshold}`] },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const token = csvEnv('PERF_VISITOR_ACCESS_TOKENS')[0];
const participationId = requiredEnv('PERF_MISSION_PARTICIPATION_ID');
const tags = requestTags(endpoint, 'POST /api/v1/me/mission-participations/{participationId}/rewards/claim', { test: testTag });

export function handleSummary(data) { return markdownSummary(data, { title: 'k6 Mission Reward Claim Concurrency Summary', scenario: 'mission-reward-claim-concurrency', testTag, apiBase, vus, duration }); }

export default function () {
  const outcome = recordOutcome('POST /me/mission-participations/{participationId}/rewards/claim', postNoBody(apiBase, `/me/mission-participations/${participationId}/rewards/claim`, authAcceptHeaders(token), tags), { successStatuses: [201], businessCodes: ['MISSION_REWARD_CLAIM_CONFLICT'], endpoint });
  if (outcome.success) successCount.add(1, tags);
}
