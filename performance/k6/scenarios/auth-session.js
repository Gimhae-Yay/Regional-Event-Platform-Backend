import { Counter } from 'k6/metrics';

import {
  apiBaseUrl,
  env,
  jsonArrayEnv,
  minExpectedOutcomeRate,
  numberEnv,
  scenarioDuration,
  scenarioP95Threshold,
  scenarioVus,
} from '../lib/config.js';
import {
  extractBearerToken,
  extractRefreshToken,
  loginRequest,
  refreshRequest,
  validateAuthUsers,
} from '../lib/auth.js';
import { pickByIteration } from '../lib/data.js';
import { recordOutcome, recordUnexpected } from '../lib/responses.js';
import { markdownSummary } from '../lib/summary.js';

const scenarioName = 'AUTH_SESSION';
const testTag = 'auth_session';
const authUsers = validateAuthUsers(jsonArrayEnv('PERF_AUTH_USERS_JSON'));
const authLoginP95Threshold = numberEnv('PERF_AUTH_LOGIN_P95_MS', 1500);
const authLoginP99Threshold = numberEnv('PERF_AUTH_LOGIN_P99_MS', 3000);
const authRefreshP95Threshold = numberEnv('PERF_AUTH_REFRESH_P95_MS', 500);
const authRefreshP99Threshold = numberEnv('PERF_AUTH_REFRESH_P99_MS', 1000);
const businessCodes = [
  'INVALID_CREDENTIALS',
  'UNAUTHENTICATED',
  'REFRESH_TOKEN_CONFLICT',
];
const systemCodes = ['AUTH_SERVICE_UNAVAILABLE'];
const loginSuccessCount = new Counter('auth_login_success_count');
const refreshSuccessCount = new Counter('auth_refresh_success_count');

export const options = {
  vus: scenarioVus(scenarioName),
  duration: scenarioDuration(scenarioName),
  thresholds: {
    expected_outcome_rate: [`rate>=${minExpectedOutcomeRate()}`],
    auth_login_success_count: ['count>0'],
    auth_refresh_success_count: ['count>0'],
    system_failure_rate: ['rate==0'],
    unexpected_failure_rate: ['rate==0'],
    [`http_req_duration{test:${testTag}}`]: [`p(95)<${scenarioP95Threshold(scenarioName)}`],
    'http_req_duration{endpoint:authLogin}': [
      `p(95)<${authLoginP95Threshold}`,
      `p(99)<${authLoginP99Threshold}`,
    ],
    'http_req_duration{endpoint:authRefresh}': [
      `p(95)<${authRefreshP95Threshold}`,
      `p(99)<${authRefreshP99Threshold}`,
    ],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const apiBase = apiBaseUrl();
const commonTags = { test: testTag };
const loginTags = { ...commonTags, endpoint: 'authLogin' };
const refreshTags = { ...commonTags, endpoint: 'authRefresh' };

export function handleSummary(data) {
  return markdownSummary(data, {
    title: 'k6 Auth Session Summary',
    scenario: 'auth-session',
    testTag,
    baseUrl: env('PERF_BASE_URL', ''),
    apiBase,
    vus: options.vus,
    duration: options.duration,
    authUsers: authUsers.length,
  });
}

export default function () {
  const user = pickByIteration(authUsers);
  const loginOutcome = recordOutcome(
    'POST /auth/login',
    loginRequest(apiBase, user, loginTags),
    { businessCodes, systemCodes },
  );
  if (!loginOutcome.success) {
    return;
  }
  loginSuccessCount.add(1, commonTags);

  const accessToken = extractBearerToken(loginOutcome.response);
  const refreshToken = extractRefreshToken(loginOutcome.response);
  if (!accessToken) {
    recordUnexpected('POST /auth/login', 'MISSING_ACCESS_TOKEN_HEADER');
    return;
  }
  if (!refreshToken) {
    recordUnexpected('POST /auth/login', 'MISSING_REFRESH_TOKEN_COOKIE');
    return;
  }

  const refreshOutcome = recordOutcome(
    'POST /auth/refresh',
    refreshRequest(apiBase, refreshToken, refreshTags),
    { businessCodes, systemCodes },
  );
  if (refreshOutcome.success) {
    refreshSuccessCount.add(1, commonTags);
  }
}
