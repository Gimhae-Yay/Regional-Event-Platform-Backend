import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';

import { env, jsonArrayEnv, numberEnv, requiredEnv, scenarioP95Threshold } from '../lib/config.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'AUTHENTICATED_READ_RESPONSE_TIME';
const testTag = 'authenticated_read_response_time';
const apiBaseUrl = `${requiredEnv('PERF_BASE_URL').replace(/\/+$/, '')}/api/v1`;
const accounts = parseAccounts(jsonArrayEnv('PERF_API_TEST_ACCOUNTS_JSON'));
const cases = parseReadCases(jsonArrayEnv('PERF_AUTHENTICATED_READ_CASES_JSON'));
const fixtureSetupCases = parseFixtureSetupCases(jsonArrayEnv('PERF_AUTHENTICATED_READ_SETUP_CASES_JSON'));
const fixtureContext = parseFixtureContext(env('PERF_API_FIXTURE_CONTEXT_JSON', '{}'));
const requestsPerApi = positiveIntegerEnv('PERF_REQUESTS_PER_API', 1);
const p95Threshold = scenarioP95Threshold(scenarioName);
const expectedOutcomeRate = new Rate('expected_outcome_rate');

export const options = {
  scenarios: {
    authenticated_read_response_time: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: env('PERF_AUTHENTICATED_READ_RESPONSE_TIME_MAX_DURATION', '30m'),
    },
  },
  thresholds: {
    expected_outcome_rate: ['rate==1'],
    ...endpointThresholds(),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const sessionsByRole = {};
  const requiredRoles = new Set(
    [...cases, ...fixtureSetupCases]
      .map((testCase) => testCase.role)
      .filter((role) => role !== 'PUBLIC'),
  );

  requiredRoles.forEach((role) => {
    const account = accounts.find((candidate) => candidate.role === role);
    if (!account) {
      fail(`No test account is configured for required role: ${role}`);
    }
    sessionsByRole[role] = login(account);
  });

  const runtimeContext = JSON.parse(JSON.stringify(fixtureContext));
  fixtureSetupCases.forEach((testCase) => executeFixtureSetup(testCase, sessionsByRole, runtimeContext));

  return { sessionsByRole, runtimeContext };
}

export default function (data) {
  cases.forEach((testCase) => {
    const path = interpolate(testCase.path, data.runtimeContext, testCase.id);
    const headers = requestHeaders(testCase, data.sessionsByRole);

    for (let requestNumber = 1; requestNumber <= requestsPerApi; requestNumber += 1) {
      const response = http.get(`${apiBaseUrl}${path}`, {
        headers,
        tags: {
          endpoint: `GET ${testCase.route}`,
          role: testCase.role,
          test: testTag,
        },
      });
      assertSuccess(response, testCase, requestNumber);
    }
  });
}

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Authenticated Read Response Time Summary',
    scenario: 'authenticated-read-response-time',
    testTag,
    baseUrl: apiBaseUrl,
    authUsers: accounts.length,
    iterations: `${requestsPerApi} requests per API`,
    mode: 'Each GET API is called sequentially before the next API starts.',
  });
}

function parseAccounts(values) {
  const roles = new Set();
  return values.map((account, index) => {
    if (!account || typeof account.role !== 'string' || account.role.trim() === '') {
      fail(`PERF_API_TEST_ACCOUNTS_JSON[${index}].role is required`);
    }
    if (roles.has(account.role)) {
      fail(`PERF_API_TEST_ACCOUNTS_JSON has duplicate role: ${account.role}`);
    }
    if (typeof account.email !== 'string' || account.email.trim() === '' || typeof account.password !== 'string' || account.password === '') {
      fail(`PERF_API_TEST_ACCOUNTS_JSON[${index}] requires email and password`);
    }
    roles.add(account.role);
    return account;
  });
}

function parseReadCases(values) {
  return values.map((testCase, index) => {
    if (!testCase || typeof testCase !== 'object') {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}] must be an object`);
    }
    if (String(testCase.method || '').toUpperCase() !== 'GET') {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}].method must be GET`);
    }
    if (typeof testCase.id !== 'string' || testCase.id.trim() === '') {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}].id is required`);
    }
    if (typeof testCase.route !== 'string' || !testCase.route.startsWith('/')) {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}].route must start with /`);
    }
    if (typeof testCase.path !== 'string' || !testCase.path.startsWith('/')) {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}].path must start with /`);
    }
    if (typeof testCase.role !== 'string' || testCase.role.trim() === '') {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}].role is required`);
    }
    if (!Number.isInteger(testCase.expectedStatus) || testCase.expectedStatus < 200 || testCase.expectedStatus >= 300) {
      fail(`PERF_AUTHENTICATED_READ_CASES_JSON[${index}].expectedStatus must be a 2xx status`);
    }
    return testCase;
  });
}

function parseFixtureSetupCases(values) {
  return values.map((testCase, index) => {
    if (!testCase || typeof testCase !== 'object') {
      fail(`PERF_AUTHENTICATED_READ_SETUP_CASES_JSON[${index}] must be an object`);
    }
    if (String(testCase.method || '').toUpperCase() !== 'POST') {
      fail(`PERF_AUTHENTICATED_READ_SETUP_CASES_JSON[${index}].method must be POST`);
    }
    if (typeof testCase.id !== 'string' || testCase.id.trim() === '') {
      fail(`PERF_AUTHENTICATED_READ_SETUP_CASES_JSON[${index}].id is required`);
    }
    if (typeof testCase.path !== 'string' || !testCase.path.startsWith('/')) {
      fail(`PERF_AUTHENTICATED_READ_SETUP_CASES_JSON[${index}].path must start with /`);
    }
    if (typeof testCase.role !== 'string' || testCase.role.trim() === '') {
      fail(`PERF_AUTHENTICATED_READ_SETUP_CASES_JSON[${index}].role is required`);
    }
    if (!Number.isInteger(testCase.expectedStatus) || testCase.expectedStatus < 200 || testCase.expectedStatus >= 300) {
      fail(`PERF_AUTHENTICATED_READ_SETUP_CASES_JSON[${index}].expectedStatus must be a 2xx status`);
    }
    return testCase;
  });
}

function parseFixtureContext(raw) {
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

function positiveIntegerEnv(name, defaultValue) {
  const value = numberEnv(name, defaultValue);
  if (!Number.isInteger(value) || value <= 0) {
    fail(`Environment variable ${name} must be a positive integer`);
  }
  return value;
}

function endpointThresholds() {
  return cases.reduce((thresholds, testCase) => {
    thresholds[`http_req_duration{endpoint:GET ${testCase.route}}`] = [`p(95)<${p95Threshold}`];
    return thresholds;
  }, {});
}

function login(account) {
  const response = http.post(
    `${apiBaseUrl}/auth/login`,
    JSON.stringify({ email: account.email, password: account.password }),
    {
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      tags: { endpoint: 'POST /auth/login', role: account.role, test: testTag },
    },
  );
  const authorization = response.headers.Authorization || response.headers.authorization;
  if (!authorization || !authorization.startsWith('Bearer ')) {
    fail(`Login for ${account.role} did not return a Bearer token.`);
  }
  return authorization;
}

function requestHeaders(testCase, sessionsByRole) {
  const headers = { Accept: 'application/json' };
  if (testCase.role !== 'PUBLIC') {
    const authorization = sessionsByRole[testCase.role];
    if (!authorization) {
      fail(`${testCase.id} requires an authenticated ${testCase.role} session.`);
    }
    headers.Authorization = authorization;
  }
  return headers;
}

function executeFixtureSetup(testCase, sessionsByRole, runtimeContext) {
  const path = interpolate(testCase.path, runtimeContext, testCase.id);
  const response = http.post(
    `${apiBaseUrl}${path}`,
    JSON.stringify(interpolateValue(testCase.body || {}, runtimeContext, testCase.id)),
    {
      headers: { ...requestHeaders(testCase, sessionsByRole), 'Content-Type': 'application/json' },
      tags: {
        endpoint: `POST ${testCase.route}`,
        role: testCase.role,
        test: 'authenticated_read_fixture',
      },
    },
  );
  const body = safeJson(response);
  const success = response.status === testCase.expectedStatus
    && body
    && body.statusCode === testCase.expectedStatus
    && body.code === 'SUCCESS'
    && Object.prototype.hasOwnProperty.call(body, 'data');
  if (!success) {
    fail(`${testCase.id} fixture setup failed with HTTP ${response.status}.`);
  }
  captureResponseData(testCase, body.data, runtimeContext);
}

function interpolate(value, context, caseId) {
  return value.replace(/{{([A-Za-z0-9_.-]+)}}/g, (match, key) => {
    const resolved = key.split('.').reduce((current, part) => current && current[part], context);
    if (resolved === undefined || resolved === null) {
      fail(`${caseId} requires fixture context value: ${key}`);
    }
    return String(resolved);
  });
}

function interpolateValue(value, context, caseId) {
  if (typeof value === 'string') {
    return interpolate(value, context, caseId);
  }
  if (Array.isArray(value)) {
    return value.map((item) => interpolateValue(item, context, caseId));
  }
  if (value && typeof value === 'object') {
    return Object.entries(value).reduce((result, [key, item]) => {
      result[key] = interpolateValue(item, context, caseId);
      return result;
    }, {});
  }
  return value;
}

function captureResponseData(testCase, responseData, context) {
  if (!testCase.capture) {
    return;
  }
  Object.entries(testCase.capture).forEach(([sourceKey, targetKey]) => {
    if (!Object.prototype.hasOwnProperty.call(responseData, sourceKey)) {
      fail(`${testCase.id} response is missing captured field: ${sourceKey}`);
    }
    setContextValue(context, targetKey, responseData[sourceKey], testCase.id);
  });
}

function setContextValue(context, targetKey, value, caseId) {
  const parts = targetKey.split('.');
  if (parts.length < 2 || parts.some((part) => part === '')) {
    fail(`${caseId} has invalid capture target: ${targetKey}`);
  }
  const finalKey = parts.pop();
  const target = parts.reduce((current, part) => {
    if (!Object.prototype.hasOwnProperty.call(current, part)) {
      current[part] = {};
    }
    if (!current[part] || typeof current[part] !== 'object' || Array.isArray(current[part])) {
      fail(`${caseId} cannot capture into non-object context: ${targetKey}`);
    }
    return current[part];
  }, context);
  target[finalKey] = value;
}

function assertSuccess(response, testCase, requestNumber) {
  const body = safeJson(response);
  const success = response.status === testCase.expectedStatus
    && body
    && body.statusCode === testCase.expectedStatus
    && body.code === 'SUCCESS'
    && Object.prototype.hasOwnProperty.call(body, 'data');
  expectedOutcomeRate.add(success, { endpoint: `GET ${testCase.route}`, role: testCase.role });
  check(response, {
    [`${testCase.id} request ${requestNumber}: expected successful API response`]: () => success,
  });
}

function safeJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}
