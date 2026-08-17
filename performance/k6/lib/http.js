import http from 'k6/http';

export function query(params) {
  const entries = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && String(value) !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  return entries.length === 0 ? '' : `?${entries.join('&')}`;
}

export function jsonHeaders(extra = {}) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...extra,
  };
}

export function acceptHeaders(extra = {}) {
  return {
    Accept: 'application/json',
    ...extra,
  };
}

export function authHeaders(token, extra = {}) {
  const value = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  return {
    ...jsonHeaders(extra),
    Authorization: value,
  };
}

export function authAcceptHeaders(token, extra = {}) {
  const value = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  return {
    ...acceptHeaders(extra),
    Authorization: value,
  };
}

export function requestTags(endpoint, name, extra = {}) {
  return {
    ...extra,
    endpoint,
    name,
  };
}

export function get(apiBase, path, headers = {}, tags = {}) {
  return http.get(`${apiBase}${path}`, { headers: acceptHeaders(headers), tags });
}

export function postJson(apiBase, path, body, headers = {}, tags = {}) {
  return http.post(`${apiBase}${path}`, JSON.stringify(body), {
    headers: jsonHeaders(headers),
    tags,
  });
}

export function postRawJson(apiBase, path, body, headers = {}, tags = {}) {
  return http.post(`${apiBase}${path}`, body, {
    headers: jsonHeaders(headers),
    tags,
  });
}

export function postNoBody(apiBase, path, headers = {}, tags = {}) {
  return http.post(`${apiBase}${path}`, null, {
    headers: acceptHeaders(headers),
    tags,
  });
}
