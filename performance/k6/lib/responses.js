import { check } from 'k6';
import {
  businessFailureCount,
  businessFailureRate,
  expectedOutcomeRate,
  successfulResponseRate,
  systemFailureCount,
  systemFailureRate,
  unexpectedFailureCount,
  unexpectedFailureRate,
} from './metrics.js';

export function safeJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

export function isApiResponseEnvelope(response) {
  const body = safeJson(response);
  return body !== null
    && typeof body.statusCode === 'number'
    && typeof body.code === 'string'
    && Object.prototype.hasOwnProperty.call(body, 'message')
    && Object.prototype.hasOwnProperty.call(body, 'data');
}

export function isSuccessfulApiResponse(response) {
  const body = safeJson(response);
  return response.status >= 200
    && response.status < 300
    && body !== null
    && body.code === 'SUCCESS'
    && isApiResponseEnvelope(response);
}

export function requireApiData(response, context) {
  const body = safeJson(response);
  if (!body || body.code !== 'SUCCESS') {
    throw new Error(`${context} response is not successful`);
  }
  return body.data;
}

export function recordOutcome(label, response, options = {}) {
  const successStatuses = options.successStatuses || [200];
  const businessCodes = options.businessCodes || [];
  const systemCodes = options.systemCodes || [];
  const body = safeJson(response);
  const code = body && typeof body.code === 'string' ? body.code : 'NO_CODE';
  const hasEnvelope = isApiResponseEnvelope(response);

  let classification = 'unexpected';
  if (response.error || response.status >= 500 || systemCodes.includes(code)) {
    classification = 'system';
  } else if (successStatuses.includes(response.status) && code === 'SUCCESS' && hasEnvelope) {
    classification = 'success';
  } else if (businessCodes.includes(code)) {
    classification = 'business';
  }

  const tags = { endpoint: label, code, classification };
  const expected = classification === 'success' || classification === 'business';
  expectedOutcomeRate.add(expected, tags);
  successfulResponseRate.add(classification === 'success', tags);
  businessFailureRate.add(classification === 'business', tags);
  systemFailureRate.add(classification === 'system', tags);
  unexpectedFailureRate.add(classification === 'unexpected', tags);

  if (classification === 'business') {
    businessFailureCount.add(1, tags);
  } else if (classification === 'system') {
    systemFailureCount.add(1, tags);
  } else if (classification === 'unexpected') {
    unexpectedFailureCount.add(1, tags);
  }

  check(response, {
    [`${label}: API response envelope`]: () => hasEnvelope,
    [`${label}: expected business or success outcome`]: () => expected,
  });

  return {
    response,
    body,
    code,
    classification,
    expected,
    success: classification === 'success',
  };
}

export function recordUnexpected(label, reason) {
  const tags = { endpoint: label, code: reason, classification: 'unexpected' };
  expectedOutcomeRate.add(false, tags);
  successfulResponseRate.add(false, tags);
  businessFailureRate.add(false, tags);
  systemFailureRate.add(false, tags);
  unexpectedFailureRate.add(true, tags);
  unexpectedFailureCount.add(1, tags);
  check(null, {
    [`${label}: ${reason}`]: () => false,
  });
}
