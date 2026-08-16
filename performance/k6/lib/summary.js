import { env } from './config.js';

export function markdownSummary(data, metadata = {}) {
  const directory = trimTrailingSlash(env('PERF_SUMMARY_DIRECTORY', 'performance/k6/results'));
  const basename = safePathSegment(env('PERF_SUMMARY_BASENAME', metadata.scenario || 'k6-summary'));
  const markdownOutputPath = `${directory}/${basename}-summary.md`;
  const createdAt = new Date().toISOString();
  const summaryMetadata = {
    ...metadata,
    createdAt,
    markdownOutputPath,
  };

  console.log(`k6 summary Markdown: ${markdownOutputPath}`);
  return {
    stdout: consoleSummary(data, summaryMetadata),
    [markdownOutputPath]: renderMarkdownSummary(data, summaryMetadata),
  };
}

function consoleSummary(data, metadata) {
  const checks = metricValues(data, 'checks');
  const failed = metricValues(data, 'http_req_failed');
  const duration = metricValues(data, 'http_req_duration');
  const thresholdStatus = allThresholdsPassed(data) ? 'PASS' : 'FAIL';
  return [
    '',
    `${metadata.title || metadata.scenario || 'k6'} summary`,
    `thresholds: ${thresholdStatus}`,
    `checks: ${formatRate(checks.rate)} (${numberValue(checks.passes)} passed, ${numberValue(checks.fails)} failed)`,
    `http_req_failed: ${formatRate(failed.rate)}`,
    `http_req_duration p95: ${formatDuration(duration['p(95)'])}`,
    `summary file: ${metadata.markdownOutputPath}`,
    '',
  ].join('\n');
}

function renderMarkdownSummary(data, metadata) {
  const checks = metricValues(data, 'checks');
  const failed = metricValues(data, 'http_req_failed');
  const requests = metricValues(data, 'http_reqs');
  const duration = metricValues(data, 'http_req_duration');
  const thresholdStatus = allThresholdsPassed(data) ? 'PASS' : 'FAIL';
  const runRows = [
    ['Generated at', metadata.createdAt],
    ['Base URL', metadata.baseUrl || metadata.apiBase],
    ['Target VUs', metadata.vus],
    ['Duration', metadata.duration],
    ['Iterations', metadata.iterations],
    ['Test tag', metadata.testTag],
    ['Mode', metadata.mode],
    ['Auth users', metadata.authUsers],
    ['Visitor tokens', metadata.visitorTokens],
    ['Operator tokens', metadata.operatorTokens],
    ['Session ID', metadata.sessionId],
    ['Content ID', metadata.contentId],
    ['Reservation ID', metadata.reservationId],
    ['Reservation No', metadata.reservationNo],
    ['Test run duration', formatDuration(data.state && data.state.testRunDurationMs)],
    ['Thresholds', thresholdStatus],
  ].filter(([, value]) => value !== undefined && value !== null && value !== '');
  return [
    `# ${metadata.title || metadata.scenario || 'k6 Summary'}`,
    '',
    '## Run',
    '',
    ...runRows.map(([label, value]) => `- ${label}: ${value}`),
    '',
    '## Highlights',
    '',
    '| Metric | Value |',
    '| --- | ---: |',
    `| HTTP requests | ${numberValue(requests.count)} |`,
    `| HTTP request rate | ${formatNumber(requests.rate)}/s |`,
    `| HTTP failed rate | ${formatRate(failed.rate)} |`,
    `| Checks passed rate | ${formatRate(checks.rate)} |`,
    `| Checks passed | ${numberValue(checks.passes)} |`,
    `| Checks failed | ${numberValue(checks.fails)} |`,
    `| HTTP duration avg | ${formatDuration(duration.avg)} |`,
    `| HTTP duration p95 | ${formatDuration(duration['p(95)'])} |`,
    `| HTTP duration p99 | ${formatDuration(duration['p(99)'])} |`,
    '',
    '## Thresholds',
    '',
    thresholdTable(data),
    '',
    '## HTTP Duration By Endpoint',
    '',
    endpointDurationTable(data),
    '',
    '## Expected Outcome By Endpoint',
    '',
    endpointExpectedOutcomeTable(data),
    '',
  ].join('\n');
}

function thresholdTable(data) {
  const rows = [];
  Object.keys(data.metrics || {})
    .sort()
    .forEach((metricName) => {
      const thresholds = data.metrics[metricName].thresholds || {};
      Object.keys(thresholds).forEach((condition) => {
        rows.push([
          markdownCell(metricName),
          markdownCell(condition),
          thresholds[condition].ok ? 'PASS' : 'FAIL',
        ]);
      });
    });
  if (rows.length === 0) {
    return 'No thresholds were reported.';
  }
  return [
    '| Metric | Condition | Result |',
    '| --- | --- | --- |',
    ...rows.map((row) => `| ${row[0]} | ${row[1]} | ${row[2]} |`),
  ].join('\n');
}

function endpointDurationTable(data) {
  const endpointMetrics = Object.keys(data.metrics || {})
    .filter((metricName) => metricName.startsWith('http_req_duration{endpoint:'))
    .sort();
  if (endpointMetrics.length === 0) {
    return 'No endpoint duration metrics were reported.';
  }

  const rows = endpointMetrics.map((metricName) => {
    const values = metricValues(data, metricName);
    return [
      markdownCell(endpointName(metricName)),
      formatDuration(values.avg),
      formatDuration(values.med),
      formatDuration(values['p(95)']),
      formatDuration(values['p(99)']),
      formatDuration(values.max),
    ];
  });
  return [
    '| Endpoint | Avg | Med | P95 | P99 | Max |',
    '| --- | ---: | ---: | ---: | ---: | ---: |',
    ...rows.map((row) => `| ${row[0]} | ${row[1]} | ${row[2]} | ${row[3]} | ${row[4]} | ${row[5]} |`),
  ].join('\n');
}

function endpointExpectedOutcomeTable(data) {
  const endpointMetrics = Object.keys(data.metrics || {})
    .filter((metricName) => metricName.startsWith('expected_outcome_rate{endpoint:'))
    .sort();
  if (endpointMetrics.length === 0) {
    return 'No endpoint expected outcome metrics were reported.';
  }

  const rows = endpointMetrics.map((metricName) => {
    const values = metricValues(data, metricName);
    return [
      markdownCell(tagValue(metricName, 'endpoint')),
      formatRate(values.rate),
      numberValue(values.passes),
      numberValue(values.fails),
    ];
  });
  return [
    '| Endpoint | Expected rate | Expected | Unexpected |',
    '| --- | ---: | ---: | ---: |',
    ...rows.map((row) => `| ${row[0]} | ${row[1]} | ${row[2]} | ${row[3]} |`),
  ].join('\n');
}

function endpointName(metricName) {
  return tagValue(metricName, 'endpoint');
}

function tagValue(metricName, tagName) {
  const prefix = `${tagName}:`;
  const start = metricName.indexOf(prefix);
  if (start < 0) {
    return metricName;
  }
  const valueStart = start + prefix.length;
  const comma = metricName.indexOf(',', valueStart);
  const closingBrace = metricName.indexOf('}', valueStart);
  const valueEnd = comma >= 0 && comma < closingBrace ? comma : closingBrace;
  return valueEnd < 0 ? metricName.substring(valueStart) : metricName.substring(valueStart, valueEnd);
}

function allThresholdsPassed(data) {
  return Object.keys(data.metrics || {}).every((metricName) => {
    const thresholds = data.metrics[metricName].thresholds || {};
    return Object.keys(thresholds).every((condition) => thresholds[condition].ok);
  });
}

function metricValues(data, metricName) {
  const metric = data.metrics && data.metrics[metricName];
  return (metric && metric.values) || {};
}

function markdownCell(value) {
  return String(value).replace(/\|/g, '\\|');
}

function numberValue(value) {
  if (!Number.isFinite(value)) {
    return '0';
  }
  return String(Math.round(value));
}

function formatNumber(value) {
  if (!Number.isFinite(value)) {
    return '0';
  }
  return value.toFixed(2);
}

function formatRate(value) {
  if (!Number.isFinite(value)) {
    return '0.00%';
  }
  return `${(value * 100).toFixed(2)}%`;
}

function formatDuration(value) {
  if (!Number.isFinite(value)) {
    return '0ms';
  }
  return `${value.toFixed(2)}ms`;
}

function trimTrailingSlash(value) {
  return String(value).replace(/[\\/]+$/, '');
}

function safePathSegment(value) {
  return String(value)
    .trim()
    .replace(/[^A-Za-z0-9_.-]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}
