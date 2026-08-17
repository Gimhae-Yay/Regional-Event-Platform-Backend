import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

import {
  env,
  numberEnv,
  requiredEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import { requestTags } from '../lib/http.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'ALL_API_RESPONSE_TIME';
const testTag = 'all_api_response_time';
const p95Threshold = scenarioP95Threshold(scenarioName);
const targets = parseTargets(requiredEnv('PERF_API_RESPONSE_TARGETS_JSON'));
const vus = scenarioVus(scenarioName);
const duration = scenarioDuration(scenarioName);
const iterations = numberEnv('PERF_ITERATIONS', 0);
const responseReceivedRate = new Rate('api_response_received_rate');
const successfulResponseRate = new Rate('api_successful_response_rate');

if (!Number.isInteger(iterations) || iterations < 0) {
  throw new Error('PERF_ITERATIONS must be a non-negative integer');
}

export const options = {
  vus,
  ...(iterations > 0 ? { iterations } : { duration }),
  thresholds: {
    api_response_received_rate: ['rate==1'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${p95Threshold}`],
    ...endpointThresholds(),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const baseUrl = requiredEnv('PERF_BASE_URL').replace(/\/+$/, '');

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 All API Response Time Summary',
    scenario: 'all-api-response-time',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    vus: options.vus,
    duration: iterations > 0 ? undefined : duration,
    iterations: iterations > 0 ? iterations : undefined,
    targetCount: targets.length,
  });
}

export default function () {
  targets.forEach((target) => {
    const tags = requestTags(target.endpoint, `${target.method} ${target.path}`, { test: testTag });
    const response = http.request(
      target.method,
      `${baseUrl}${target.path}`,
      requestBody(target),
      {
        headers: requestHeaders(target),
        tags,
      },
    );
    const responseReceived = response.status > 0;
    const successfulResponse = response.status >= 200 && response.status < 300;
    const metricTags = { endpoint: target.endpoint, method: target.method };

    responseReceivedRate.add(responseReceived, metricTags);
    successfulResponseRate.add(successfulResponse, metricTags);
    check(response, {
      [`${target.method} ${target.path}: HTTP response received`]: () => responseReceived,
    });
  });
}

function parseTargets(raw) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`PERF_API_RESPONSE_TARGETS_JSON must be valid JSON: ${error.message}`);
  }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('PERF_API_RESPONSE_TARGETS_JSON must contain at least one API target');
  }

  return parsed.map((target, index) => {
    if (!target || typeof target !== 'object') {
      throw new Error(`PERF_API_RESPONSE_TARGETS_JSON[${index}] must be an object`);
    }
    const method = String(target.method || '').toUpperCase();
    const path = String(target.path || '');
    if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
      throw new Error(`PERF_API_RESPONSE_TARGETS_JSON[${index}].method is invalid`);
    }
    if (!path.startsWith('/')) {
      throw new Error(`PERF_API_RESPONSE_TARGETS_JSON[${index}].path must start with /`);
    }

    return {
      endpoint: String(target.endpoint || `${method} ${path}`),
      method,
      path,
      body: target.body,
    };
  });
}

function endpointThresholds() {
  return targets.reduce((thresholds, target) => {
    thresholds[`http_req_duration{endpoint:${target.endpoint}}`] = [`p(95)<${p95Threshold}`];
    return thresholds;
  }, {});
}

function requestBody(target) {
  if (target.body === undefined || target.body === null || target.method === 'GET') {
    return null;
  }
  return JSON.stringify(target.body);
}

function requestHeaders(target) {
  const headers = { Accept: 'application/json' };
  if (target.body !== undefined && target.body !== null && target.method !== 'GET') {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}
