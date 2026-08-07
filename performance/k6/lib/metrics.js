import { Counter, Rate } from 'k6/metrics';

export const expectedOutcomeRate = new Rate('expected_outcome_rate');
export const successfulResponseRate = new Rate('successful_response_rate');
export const businessFailureRate = new Rate('business_failure_rate');
export const systemFailureRate = new Rate('system_failure_rate');
export const unexpectedFailureRate = new Rate('unexpected_failure_rate');
export const businessFailureCount = new Counter('business_failure_count');
export const systemFailureCount = new Counter('system_failure_count');
export const unexpectedFailureCount = new Counter('unexpected_failure_count');
