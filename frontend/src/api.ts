import axios from 'axios';

export interface RuntimeConfig {
  token: string;
  projectId: string;
}

export interface ApiResult<T> {
  code: string;
  message: string;
  requestId: string;
  data: T;
}

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
});

let runtimeConfig: RuntimeConfig = {
  token: 'mock-token',
  projectId: 'P001'
};

export function setRuntimeConfig(config: RuntimeConfig) {
  runtimeConfig = config;
}

function requestId() {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}

function headers(write = false) {
  const result: Record<string, string> = {
    Authorization: `Bearer ${runtimeConfig.token}`,
    'X-Project-Id': runtimeConfig.projectId,
    'X-Request-Id': requestId()
  };
  if (write) {
    result['X-Idempotency-Key'] = requestId();
  }
  return result;
}

async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>) {
  const response = await promise;
  return response.data.data;
}

export const api = {
  createSession(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/support/sessions', body, { headers: headers(true) }));
  },
  updateSessionContext(sessionId: string, body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.patch(`/v1/support/sessions/${sessionId}/context`, body, { headers: headers(true) }));
  },
  startDiagnosis(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/diagnosis/cases', body, { headers: headers(true) }));
  },
  getCase(caseId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/diagnosis/cases/${caseId}`, { headers: headers() }));
  },
  getTrace(traceId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/traces/${traceId}`, { headers: headers(), params: { includeSteps: true, includeMcpCalls: true } }));
  },
  createKnowledgeSource(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/knowledge/sources', body, { headers: headers(true) }));
  },
  getKnowledgeTasks(sourceId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/knowledge/sources/${sourceId}/tasks`, { headers: headers() }));
  },
  retryKnowledge(sourceId: string) {
    return unwrap<Record<string, unknown>>(client.post(`/v1/knowledge/sources/${sourceId}/retry`, {}, { headers: headers(true) }));
  },
  queryGraphs() {
    return unwrap<Record<string, unknown>>(client.get('/v1/graphs/assets', { headers: headers() }));
  },
  listCapabilities() {
    return unwrap<Record<string, unknown>>(client.get('/v1/mcp/capabilities', { headers: headers() }));
  },
  capabilityImpact(capabilityId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/mcp/capabilities/${capabilityId}/impact`, { headers: headers() }));
  },
  updateCapabilityStatus(capabilityId: string, body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.patch(`/v1/mcp/capabilities/${capabilityId}/status`, body, { headers: headers(true) }));
  },
  updateRoute(routeId: string, body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.patch(`/v1/routes/${routeId}/status`, body, { headers: headers(true) }));
  },
  submitFeedback(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/routes/evaluations', body, { headers: headers(true) }));
  }
};
