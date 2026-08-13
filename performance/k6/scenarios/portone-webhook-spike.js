import { fail } from 'k6';
import { Counter } from 'k6/metrics';

import { apiBaseUrl, env, numberEnv, requiredEnv, scenarioP95Threshold } from '../lib/config.js';
import { postRawJson, requestTags } from '../lib/http.js';
import { recordOutcome } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'PORTONE_WEBHOOK_SPIKE';
const testTag = 'portone_webhook_spike';
const endpoint = 'portoneWebhook';
const vus = numberEnv('PERF_PORTONE_WEBHOOK_SPIKE_VUS', 10);
const iterations = numberEnv('PERF_PORTONE_WEBHOOK_SPIKE_ITERATIONS', vus);
const duration = env('PERF_PORTONE_WEBHOOK_SPIKE_DURATION', '40s');
const p95Threshold = scenarioP95Threshold(scenarioName, 35000);
const successCount = new Counter('portone_webhook_success_count');

export const options = {
  scenarios: { duplicate_webhook_spike: { executor: 'shared-iterations', vus, iterations, maxDuration: duration } },
  thresholds: { expected_outcome_rate: ['rate==1'], [`expected_outcome_rate{endpoint:${endpoint}}`]: ['rate==1'], portone_webhook_success_count: [`count==${iterations}`], system_failure_rate: ['rate==0'], unexpected_failure_rate: ['rate==0'], [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95Threshold}`] },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const body = requiredEnv('PERF_PORTONE_WEBHOOK_BODY');
try {
  JSON.parse(body);
} catch (error) {
  fail(`PERF_PORTONE_WEBHOOK_BODY must be valid JSON: ${error.message}`);
}
const headers = { 'webhook-id': requiredEnv('PERF_PORTONE_WEBHOOK_ID'), 'webhook-timestamp': requiredEnv('PERF_PORTONE_WEBHOOK_TIMESTAMP'), 'webhook-signature': requiredEnv('PERF_PORTONE_WEBHOOK_SIGNATURE') };
const tags = requestTags(endpoint, 'POST /api/v1/webhooks/portone', { test: testTag });

export function handleSummary(data) { return markdownSummary(data, { title: 'k6 PortOne Webhook Spike Summary', scenario: 'portone-webhook-spike', testTag, apiBase, vus, duration }); }

export default function () {
  const outcome = recordOutcome(
    'POST /webhooks/portone',
    postRawJson(apiBase, '/webhooks/portone', body, headers, tags),
    { endpoint },
  );
  if (outcome.success) {
    successCount.add(1, tags);
  }
}
