import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate } from 'k6/metrics';

import { env, jsonArrayEnv, requiredEnv } from '../lib/config.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'API_SUCCESS_COVERAGE';
const apiBaseUrl = `${requiredEnv('PERF_BASE_URL').replace(/\/+$/, '')}/api/v1`;
const accounts = parseAccounts(jsonArrayEnv('PERF_API_TEST_ACCOUNTS_JSON'));
const cases = parseCases(jsonArrayEnv('PERF_API_SUCCESS_CASES_JSON'));
const context = parseContext(env('PERF_API_FIXTURE_CONTEXT_JSON', '{}'));
const successfulResponseRate = new Rate('api_success_coverage_success_rate');
const executedCaseCount = new Counter('api_success_coverage_case_count');

export const options = {
  scenarios: {
    api_success_coverage: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: env('PERF_API_SUCCESS_COVERAGE_MAX_DURATION', '10m'),
    },
  },
  thresholds: {
    api_success_coverage_success_rate: ['rate==1'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const authenticatedAccounts = {};
  accounts.forEach((account) => {
    const response = http.post(
      `${apiBaseUrl}/auth/login`,
      JSON.stringify({ email: account.email, password: account.password }),
      { headers: jsonHeaders(), tags: { endpoint: 'POST /auth/login', role: account.role } },
    );
    assertSuccess(response, 200, `login:${account.role}`);
    const authorization = response.headers.Authorization || response.headers.authorization;
    if (!authorization || !authorization.startsWith('Bearer ')) {
      fail(`Login for ${account.role} did not return an access token.`);
    }
    authenticatedAccounts[account.role] = {
      authorization,
      refreshToken: response.cookies.refreshToken && response.cookies.refreshToken[0]
        ? response.cookies.refreshToken[0].value
        : null,
    };
  });
  return { authenticatedAccounts };
}

export default function (data) {
  const runtimeContext = JSON.parse(JSON.stringify(context));
  cases.forEach((testCase) => {
    const resolved = resolveCase(testCase, runtimeContext);
    const headers = requestHeaders(testCase, data.authenticatedAccounts);
    const response = http.request(
      testCase.method,
      `${apiBaseUrl}${resolved.path}`,
      requestBody(testCase, resolved.body),
      {
        headers,
        tags: {
          endpoint: `${testCase.method} ${testCase.path}`,
          role: testCase.role,
          case: testCase.id,
        },
      },
    );
    assertSuccess(response, testCase.expectedStatus, testCase.id);
    captureResponseData(testCase, response, runtimeContext);
    executedCaseCount.add(1, { endpoint: `${testCase.method} ${testCase.path}`, role: testCase.role });
  });
}

function captureResponseData(testCase, response, runtimeContext) {
  if (!testCase.capture) {
    return;
  }
  const data = safeJson(response).data;
  Object.keys(testCase.capture).forEach((responsePath) => {
    const contextPath = testCase.capture[responsePath];
    if (typeof contextPath !== 'string' || contextPath.trim() === '') {
      fail(`${testCase.id}.capture.${responsePath} must be a context path`);
    }
    const responseValue = findResponseValue(data, responsePath);
    if (responseValue === undefined) {
      fail(`${testCase.id} response data does not include ${responsePath}`);
    }
    setContextValue(runtimeContext, contextPath, responseValue);
  });
}

function findResponseValue(data, path) {
  return path.split('.').reduce((current, part) => {
    if (current === undefined || current === null || !Object.prototype.hasOwnProperty.call(current, part)) {
      return undefined;
    }
    return current[part];
  }, data);
}

function setContextValue(contextObject, path, value) {
  const parts = path.split('.');
  const last = parts.pop();
  const target = parts.reduce((current, part) => {
    if (!current[part] || typeof current[part] !== 'object') {
      current[part] = {};
    }
    return current[part];
  }, contextObject);
  target[last] = value;
}

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 API Success Coverage Summary',
    scenario: 'api-success-coverage',
    testTag: 'api-success-coverage',
    baseUrl: apiBaseUrl,
    authUsers: accounts.length,
    targetCount: cases.length,
  });
}

function parseAccounts(values) {
  const byRole = {};
  values.forEach((account, index) => {
    if (!account || typeof account.role !== 'string' || account.role.trim() === '') {
      fail(`PERF_API_TEST_ACCOUNTS_JSON[${index}].role is required`);
    }
    if (typeof account.email !== 'string' || account.email.trim() === '' || typeof account.password !== 'string' || account.password === '') {
      fail(`PERF_API_TEST_ACCOUNTS_JSON[${index}] requires email and password`);
    }
    if (byRole[account.role]) {
      fail(`PERF_API_TEST_ACCOUNTS_JSON has duplicate role: ${account.role}`);
    }
    byRole[account.role] = true;
  });
  return values;
}

function parseCases(values) {
  const ids = {};
  return values.map((testCase, index) => {
    if (!testCase || typeof testCase !== 'object') {
      fail(`PERF_API_SUCCESS_CASES_JSON[${index}] must be an object`);
    }
    const method = String(testCase.method || '').toUpperCase();
    if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
      fail(`PERF_API_SUCCESS_CASES_JSON[${index}].method is invalid`);
    }
    if (typeof testCase.id !== 'string' || testCase.id.trim() === '' || ids[testCase.id]) {
      fail(`PERF_API_SUCCESS_CASES_JSON[${index}].id must be unique`);
    }
    if (typeof testCase.path !== 'string' || !testCase.path.startsWith('/')) {
      fail(`PERF_API_SUCCESS_CASES_JSON[${index}].path must start with /`);
    }
    if (typeof testCase.role !== 'string' || testCase.role.trim() === '') {
      fail(`PERF_API_SUCCESS_CASES_JSON[${index}].role is required; use PUBLIC for unauthenticated APIs`);
    }
    if (!Number.isInteger(testCase.expectedStatus) || testCase.expectedStatus < 200 || testCase.expectedStatus >= 300) {
      fail(`PERF_API_SUCCESS_CASES_JSON[${index}].expectedStatus must be a 2xx status`);
    }
    ids[testCase.id] = true;
    return { ...testCase, method };
  }).sort((first, second) => (first.sequence || 100) - (second.sequence || 100));
}

function parseContext(raw) {
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      fail('PERF_API_FIXTURE_CONTEXT_JSON must be a JSON object');
    }
    return parsed;
  } catch (error) {
    fail(`PERF_API_FIXTURE_CONTEXT_JSON must be valid JSON: ${error.message}`);
  }
  return {};
}

function resolveCase(testCase, fixtureContext) {
  return {
    path: interpolate(testCase.path, fixtureContext, testCase.id),
    body: interpolate(testCase.body, fixtureContext, testCase.id),
  };
}

function interpolate(value, fixtureContext, caseId) {
  if (typeof value === 'string') {
    return value.replace(/{{([A-Za-z0-9_.-]+)}}/g, (match, key) => {
      const resolved = key.split('.').reduce((current, part) => current && current[part], fixtureContext);
      if (resolved === undefined || resolved === null) {
        fail(`${caseId} requires fixture context value: ${key}`);
      }
      return String(resolved);
    });
  }
  if (Array.isArray(value)) {
    return value.map((item) => interpolate(item, fixtureContext, caseId));
  }
  if (value && typeof value === 'object') {
    return Object.keys(value).reduce((result, key) => ({ ...result, [key]: interpolate(value[key], fixtureContext, caseId) }), {});
  }
  return value;
}

function requestHeaders(testCase, authenticatedAccounts) {
  const headers = { Accept: 'application/json', ...(testCase.headers || {}) };
  if (testCase.body !== undefined && testCase.body !== null) {
    headers['Content-Type'] = 'application/json';
  }
  if (testCase.role !== 'PUBLIC') {
    const session = authenticatedAccounts[testCase.role];
    if (!session) {
      fail(`${testCase.id} requires authenticated role ${testCase.role}, but no matching test account exists`);
    }
    headers.Authorization = session.authorization;
    if (testCase.useRefreshToken) {
      if (!session.refreshToken) {
        fail(`${testCase.id} requires a refresh token from login:${testCase.role}`);
      }
      headers.Cookie = `refreshToken=${session.refreshToken}`;
    }
  }
  return headers;
}

function requestBody(testCase, body) {
  if (body === undefined || body === null || testCase.method === 'GET') {
    return null;
  }
  return JSON.stringify(body);
}

function assertSuccess(response, expectedStatus, label) {
  if (expectedStatus === 204) {
    const success = response.status === expectedStatus;
    successfulResponseRate.add(success, { endpoint: label, expected_status: String(expectedStatus) });
    check(response, {
      [`${label}: expected successful API response`]: () => success,
    });
    return;
  }
  const body = safeJson(response);
  const success = response.status === expectedStatus
    && body
    && body.statusCode === expectedStatus
    && body.code === 'SUCCESS'
    && Object.prototype.hasOwnProperty.call(body, 'data');
  successfulResponseRate.add(success, { endpoint: label, expected_status: String(expectedStatus) });
  check(response, {
    [`${label}: expected successful API response`]: () => success,
  });
}

function safeJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

function jsonHeaders() {
  return { Accept: 'application/json', 'Content-Type': 'application/json' };
}
