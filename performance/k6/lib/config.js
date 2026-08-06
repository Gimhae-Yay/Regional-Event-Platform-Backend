import { fail } from 'k6';

export function requiredEnv(name) {
  const value = __ENV[name];
  if (value === undefined || value === null || String(value).trim() === '') {
    fail(`Missing required environment variable: ${name}`);
  }
  return String(value).trim();
}

export function env(name, defaultValue) {
  const value = __ENV[name];
  if (value === undefined || value === null || String(value).trim() === '') {
    return defaultValue;
  }
  return String(value).trim();
}

export function numberEnv(name, defaultValue) {
  const raw = env(name, String(defaultValue));
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    fail(`Environment variable ${name} must be a number: ${raw}`);
  }
  return value;
}

export function csvEnv(name) {
  const values = requiredEnv(name)
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
  if (values.length === 0) {
    fail(`Environment variable ${name} must contain at least one value`);
  }
  return values;
}

export function jsonArrayEnv(name) {
  const raw = requiredEnv(name);
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length === 0) {
      fail(`Environment variable ${name} must be a non-empty JSON array`);
    }
    return parsed;
  } catch (error) {
    fail(`Environment variable ${name} must be valid JSON: ${error.message}`);
  }
  return [];
}

export function apiBaseUrl() {
  return `${requiredEnv('PERF_BASE_URL').replace(/\/+$/, '')}/api/v1`;
}

export function scenarioVus(scenarioName, defaultValue = 1) {
  return numberEnv(`PERF_${scenarioName}_VUS`, numberEnv('PERF_VUS', defaultValue));
}

export function scenarioDuration(scenarioName, defaultValue = '10s') {
  return env(`PERF_${scenarioName}_DURATION`, env('PERF_DURATION', defaultValue));
}

export function scenarioP95Threshold(scenarioName, defaultValue = 5000) {
  return numberEnv(`PERF_${scenarioName}_P95_MS`, numberEnv('PERF_P95_MS', defaultValue));
}

export function minExpectedOutcomeRate(defaultValue = 0.99) {
  return numberEnv('PERF_MIN_EXPECTED_OUTCOME_RATE', defaultValue);
}
