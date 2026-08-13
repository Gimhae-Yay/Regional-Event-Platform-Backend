import { Counter } from 'k6/metrics';
import { fail } from 'k6';

import { apiBaseUrl, csvEnv, requiredEnv, scenarioDuration, scenarioP95Threshold, scenarioVus } from '../lib/config.js';
import { authHeaders, postJson, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'COUPON_ISSUE_CONCURRENCY';
const testTag = 'coupon_issue_concurrency';
const endpoint = 'couponIssue';
const vus = scenarioVus(scenarioName, 2);
const duration = scenarioDuration(scenarioName);
const p95Threshold = scenarioP95Threshold(scenarioName);
const successCount = new Counter('coupon_issue_success_count');

if (!Number.isInteger(vus) || vus < 2) {
  fail(`PERF_${scenarioName}_VUS must be an integer greater than or equal to 2`);
}

export const options = {
  scenarios: { issue_same_coupon: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: duration } },
  thresholds: { expected_outcome_rate: ['rate==1'], [`expected_outcome_rate{endpoint:${endpoint}}`]: ['rate==1'], coupon_issue_success_count: [`count==${vus}`], system_failure_rate: ['rate==0'], unexpected_failure_rate: ['rate==0'], [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95Threshold}`] },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const token = csvEnv('PERF_VISITOR_ACCESS_TOKENS')[0];
const policyId = requiredEnv('PERF_COUPON_POLICY_ID');
const issueSourceType = requiredEnv('PERF_COUPON_ISSUE_SOURCE_TYPE');
const sourceId = requiredEnv('PERF_COUPON_ISSUE_SOURCE_ID');
const tags = requestTags(endpoint, 'POST /api/v1/coupon-policies/{couponPolicyId}/coupons', { test: testTag });

export function handleSummary(data) { return markdownSummary(data, { title: 'k6 Coupon Issue Concurrency Summary', scenario: 'coupon-issue-concurrency', testTag, apiBase, vus, duration }); }

export default function () {
  const outcome = recordOutcome('POST /coupon-policies/{couponPolicyId}/coupons', postJson(apiBase, `/coupon-policies/${policyId}/coupons`, { issueSourceType, sourceId }, authHeaders(token), tags), { successStatuses: [201], businessCodes: ['COUPON_ISSUE_CONFLICT', 'COUPON_POLICY_NOT_PUBLISHED'], endpoint });
  if (outcome.success) successCount.add(1, tags);
}
