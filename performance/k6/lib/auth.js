import { fail } from 'k6';
import http from 'k6/http';
import { check } from 'k6';
import { csvEnv, env, numberEnv } from './config.js';
import {
  authHeaders as baseAuthHeaders,
  jsonHeaders as baseJsonHeaders,
  postJson,
  postNoBody,
} from './http.js';
import { isSuccessfulApiResponse, requireApiData } from './responses.js';

export function credentialPoolFromEnv(options = {}) {
  const count = options.count || 1;
  const emails = optionalCsvEnv(options.emailsEnv || 'AUTH_EMAILS');
  const passwords = optionalCsvEnv(options.passwordsEnv || 'AUTH_PASSWORDS');
  const names = optionalCsvEnv(options.namesEnv || 'AUTH_NAMES');
  const phones = optionalCsvEnv(options.phonesEnv || 'AUTH_PHONES');
  const requestedRoles = optionalCsvEnv(options.requestedRolesEnv || 'AUTH_REQUESTED_ROLES');
  const password = env(options.passwordEnv || 'AUTH_PASSWORD', options.defaultPassword || 'PerfPassword!1');
  const requestedRole = env(
    options.requestedRoleEnv || 'AUTH_REQUESTED_ROLE',
    options.defaultRequestedRole || 'VISITOR',
  );
  const requestedRegionId = env(
    options.requestedRegionIdEnv || 'AUTH_REQUESTED_REGION_ID',
    options.defaultRequestedRegionId || '',
  );
  const businessInformation = env(
    options.businessInformationEnv || 'AUTH_BUSINESS_INFORMATION',
    options.defaultBusinessInformation || '',
  );
  const sequenceStart = positiveIntegerEnv(
    options.sequenceStartEnv || 'PERF_USER_SEQUENCE_START',
    options.defaultSequenceStart || 1,
  );

  if (emails.length > 0) {
    return emails.map((email, index) => credential(
      email,
      passwords[index] || password,
      names[index],
      phones[index],
      requestedRoles[index] || requestedRole,
      sequenceStart + index,
      requestedRegionId,
      businessInformation,
    ));
  }

  const runKey = env(options.runKeyEnv || 'PERF_RUN_KEY', '');
  if (runKey) {
    return Array.from({ length: count }, (_, index) => credential(
      perfUserEmail(runKey, sequenceStart + index),
      password,
      names[index],
      phones[index],
      requestedRoles[index] || requestedRole,
      sequenceStart + index,
      requestedRegionId,
      businessInformation,
    ));
  }

  const email = env(options.emailEnv || 'AUTH_EMAIL', options.defaultEmail || '');
  if (!email) {
    return [];
  }
  return [credential(
    email,
    password,
    names[0],
    phones[0],
    requestedRoles[0] || requestedRole,
    sequenceStart,
    requestedRegionId,
    businessInformation,
  )];
}

export function requireCredentialCount(credentials, count, context) {
  if (credentials.length < count) {
    throw new Error(`${context} requires at least ${count} credentials, but got ${credentials.length}.`);
  }
}

export function validateAuthUsers(users) {
  users.forEach((user, index) => {
    if (!user || typeof user.email !== 'string' || user.email.trim() === '') {
      fail(`PERF_AUTH_USERS_JSON[${index}].email is required`);
    }
    if (!user || typeof user.password !== 'string' || user.password === '') {
      fail(`PERF_AUTH_USERS_JSON[${index}].password is required`);
    }
  });
  return users;
}

export function signupUser(baseUrl, credential, endpointTag) {
  const data = signupPayload(credential);
  const response = http.post(
    `${authApiBase(baseUrl)}/auth/signup`,
    JSON.stringify(data),
    {
      headers: jsonHeaders(),
      tags: endpointTags(endpointTag, 'authSignup'),
    },
  );

  if (response.status !== 201 || !isSuccessfulApiResponse(response)) {
    throw new Error(
      `signup failed for ${credential.email}. status=${response.status}, body=${bodyPreview(response)}`,
    );
  }

  const responseData = requireApiData(response, `signup ${credential.email}`);
  if (!responseData || !responseData.userId) {
    throw new Error(`signup response for ${credential.email} did not include data.userId`);
  }
  return responseData;
}

export function signupUsers(baseUrl, credentials, endpointTag) {
  return credentials.map((credential) => signupUser(baseUrl, credential, endpointTag));
}

export function loginRequest(baseUrl, credential, endpointTag) {
  return postJson(authApiBase(baseUrl), '/auth/login', {
    email: credential.email,
    password: credential.password,
  }, {}, endpointTags(endpointTag, 'authLogin'));
}

export function loginUser(baseUrl, credential, endpointTag) {
  const tags = endpointTags(endpointTag, 'authLogin');
  const endpoint = tags.endpoint;
  const response = loginRequest(baseUrl, credential, tags);

  check(response, {
    [`${endpoint} status is 200`]: (res) => res.status === 200,
    [`${endpoint} response is successful`]: isSuccessfulApiResponse,
  });

  const data = requireApiData(response, `login ${credential.email}`);
  const accessToken = extractBearerToken(response);
  const cookie = refreshCookie(response);
  if (!data || !data.userId) {
    throw new Error(`login response for ${credential.email} did not include data.userId`);
  }
  if (!accessToken) {
    throw new Error(`login response for ${credential.email} did not include Authorization bearer token`);
  }
  if (!cookie) {
    throw new Error(`login response for ${credential.email} did not include refresh token cookie`);
  }

  return {
    email: credential.email,
    userId: data.userId,
    accessToken,
    refreshCookie: cookie,
  };
}

export function loginUsers(baseUrl, credentials, endpointTag) {
  return credentials.map((credential) => loginUser(baseUrl, credential, endpointTag));
}

export function authHeaders(accessToken) {
  if (!accessToken) {
    fail('accessToken is required to build Authorization header');
  }
  return baseAuthHeaders(accessToken);
}

export function jsonHeaders() {
  return baseJsonHeaders();
}

export function refreshRequest(baseUrl, refreshToken, endpointTag) {
  return postNoBody(
    authApiBase(baseUrl),
    '/auth/refresh',
    refreshCookieHeader(refreshToken),
    endpointTags(endpointTag, 'authRefresh'),
  );
}

export function refreshCookieHeader(refreshToken) {
  return { Cookie: `${env('REFRESH_COOKIE_NAME', 'refreshToken')}=${refreshToken}` };
}

export function extractRefreshToken(response) {
  const cookie = refreshCookie(response);
  if (!cookie) {
    return null;
  }
  return cookie.substring(cookie.indexOf('=') + 1);
}

export function refreshCookie(response) {
  const cookieName = env('REFRESH_COOKIE_NAME', 'refreshToken');
  const cookies = response.cookies && response.cookies[cookieName];
  if (Array.isArray(cookies) && cookies.length > 0 && cookies[0].value) {
    return `${cookieName}=${cookies[0].value}`;
  }

  const header = response.headers['Set-Cookie'] || response.headers['set-cookie'];
  if (!header) {
    return '';
  }
  const match = header.match(new RegExp(`${cookieName}=([^;]+)`));
  if (!match) {
    return '';
  }
  return `${cookieName}=${match[1]}`;
}

export function extractBearerToken(response) {
  let body;
  try {
    body = response.json();
  } catch (error) {
    return null;
  }
  const accessToken = body?.data?.accessToken;
  if (typeof accessToken !== 'string' || accessToken.trim() === '') {
    return null;
  }
  return `Bearer ${accessToken.trim()}`;
}

function optionalCsvEnv(name) {
  if (!env(name, '')) {
    return [];
  }
  return csvEnv(name);
}

function positiveIntegerEnv(name, defaultValue) {
  const value = numberEnv(name, defaultValue);
  if (!Number.isInteger(value) || value <= 0) {
    fail(`Environment variable ${name} must be a positive integer`);
  }
  return value;
}

function perfUserEmail(runKey, sequence) {
  return `perf-user-${runKey}-${String(sequence).padStart(5, '0')}@example.com`;
}

function credential(
  email,
  password,
  name,
  phone,
  requestedRole,
  sequence,
  requestedRegionId,
  businessInformation,
) {
  return {
    email,
    password,
    name: name || `Perf User ${String(sequence).padStart(5, '0')}`,
    phone: phone || perfUserPhone(sequence),
    requestedRole,
    requestedRegionId: requestedRegionId || undefined,
    businessInformation: businessInformation || undefined,
  };
}

function perfUserPhone(sequence) {
  return `010${String(sequence).padStart(8, '0')}`;
}

function signupPayload(credential) {
  const requiredFields = ['email', 'password', 'name', 'phone', 'requestedRole'];
  requiredFields.forEach((field) => {
    if (!credential[field]) {
      fail(`signup credential ${credential.email || ''} is missing required field: ${field}`);
    }
  });

  const payload = {
    email: credential.email,
    password: credential.password,
    name: credential.name,
    phone: credential.phone,
    requestedRole: credential.requestedRole,
  };

  if (credential.requestedRole === 'OPERATOR') {
    if (!credential.requestedRegionId) {
      fail(`signup credential ${credential.email} is missing requestedRegionId for OPERATOR signup`);
    }
    if (!credential.businessInformation) {
      fail(`signup credential ${credential.email} is missing businessInformation for OPERATOR signup`);
    }
    payload.requestedRegionId = credential.requestedRegionId;
    payload.businessInformation = credential.businessInformation;
  }

  return payload;
}

function bodyPreview(response) {
  const body = response.body || '';
  if (body.length <= 500) {
    return body;
  }
  return `${body.substring(0, 500)}...`;
}

function endpointTags(endpointTag, defaultEndpoint) {
  if (!endpointTag) {
    return { endpoint: defaultEndpoint };
  }
  if (typeof endpointTag === 'string') {
    return { endpoint: endpointTag };
  }
  return { endpoint: defaultEndpoint, ...endpointTag };
}

function authApiBase(baseUrl) {
  const normalized = String(baseUrl).replace(/\/+$/, '');
  if (normalized.endsWith('/api/v1')) {
    return normalized;
  }
  return `${normalized}/api/v1`;
}
