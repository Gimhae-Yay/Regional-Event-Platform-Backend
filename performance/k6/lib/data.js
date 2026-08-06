import { fail } from 'k6';

export function pick(values) {
  return values[Math.floor(Math.random() * values.length)];
}

export function pickByIteration(values) {
  return values[(__VU + __ITER - 1) % values.length];
}

export function requireSameLength(leftName, leftValues, rightName, rightValues) {
  if (leftValues.length !== rightValues.length) {
    fail(`${leftName} and ${rightName} must have the same number of comma-separated values`);
  }
}

export function idempotencyKey(prefix) {
  const random = Math.random().toString(36).slice(2, 12);
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}-${random}`;
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
