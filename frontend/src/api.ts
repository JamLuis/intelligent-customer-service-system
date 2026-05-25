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

const KNOWLEDGE_WRITE_TIMEOUT_MS = 180000;

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

function headers(write = false, permissions?: string[]) {
  const result: Record<string, string> = {
    Authorization: `Bearer ${runtimeConfig.token}`,
    'X-Project-Id': runtimeConfig.projectId,
    'X-Request-Id': requestId()
  };
  if (write) {
    result['X-Idempotency-Key'] = requestId();
  }
  if (permissions?.length) {
    result['X-Permissions'] = permissions.join(',');
  }
  return result;
}

async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>) {
  try {
    const response = await promise;
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ApiResult<unknown>>(error)) {
      throw new Error(error.response?.data?.message || error.message);
    }
    throw error;
  }
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
    return unwrap<Record<string, unknown>>(client.post('/v1/knowledge/sources', body, { headers: headers(true), timeout: KNOWLEDGE_WRITE_TIMEOUT_MS }));
  },
  getKnowledgeTasks(sourceId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/knowledge/sources/${sourceId}/tasks`, { headers: headers() }));
  },
  getKnowledgeBlocks(sourceId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/knowledge/sources/${sourceId}/blocks`, { headers: headers(), params: { pageSize: 200 } }));
  },
  getKnowledgeCandidates(sourceId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/knowledge/sources/${sourceId}/candidates`, { headers: headers() }));
  },
  retryKnowledge(sourceId: string) {
    return unwrap<Record<string, unknown>>(client.post(`/v1/knowledge/sources/${sourceId}/retry`, {}, { headers: headers(true), timeout: KNOWLEDGE_WRITE_TIMEOUT_MS }));
  },
  queryGraphs(params?: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.get('/v1/graphs/assets', { headers: headers(), params }));
  },
  getGraph(graphId: string) {
    return unwrap<Record<string, unknown>>(client.get(`/v1/graphs/assets/${graphId}`, { headers: headers() }));
  },
  listGraphCategories() {
    return unwrap<Record<string, unknown>>(client.get('/v1/graphs/assets/categories', { headers: headers() }));
  },
  updateGraphDraft(graphId: string, body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.patch(`/v1/graphs/assets/${graphId}/draft`, body, { headers: headers(true) }));
  },
  deleteGraph(graphId: string) {
    return unwrap<Record<string, unknown>>(client.delete(`/v1/graphs/assets/${graphId}`, { headers: headers(true) }));
  },
  applyEntityAction(entityId: string, body: Record<string, unknown>) {
    const action = String(body.action || '');
    return unwrap<Record<string, unknown>>(client.post(`/v1/graphs/entities/${entityId}/actions`, body, {
      headers: headers(true, [action === 'unfreeze' ? 'graph:unfreeze' : 'graph:freeze'])
    }));
  },
  applyRelationAction(relationId: string, body: Record<string, unknown>) {
    const action = String(body.action || '');
    return unwrap<Record<string, unknown>>(client.post(`/v1/graphs/relations/${relationId}/actions`, body, {
      headers: headers(true, [action === 'unfreeze' ? 'graph:unfreeze' : 'graph:freeze'])
    }));
  },
  searchDiagnosisGraph(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/graphs/search/diagnosis', body, { headers: headers() }));
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
  getKnowledgeModelConfig() {
    return unwrap<Record<string, unknown>>(client.get('/v1/admin/knowledge/model-config', { headers: headers() }));
  },
  saveKnowledgeModelConfig(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.put('/v1/admin/knowledge/model-config', body, { headers: headers(true) }));
  },
  checkKnowledgeModelConfig() {
    return unwrap<Record<string, unknown>>(client.post('/v1/admin/knowledge/model-config/check', {}, { headers: headers(true) }));
  },
  listModelProfiles(purpose: string) {
    return unwrap<Record<string, unknown>[]>(client.get('/v1/admin/model-profiles', { headers: headers(), params: { purpose } }));
  },
  discoverLocalModels(purpose: string) {
    return unwrap<Record<string, unknown>>(client.get('/v1/admin/model-profiles/local-discovery', { headers: headers(), params: { purpose } }));
  },
  startLocalRuntime(runtime: string) {
    return unwrap<Record<string, unknown>>(client.post(`/v1/admin/model-profiles/local-runtime/${runtime}/start`, {}, { headers: headers(true) }));
  },
  saveModelProfile(purpose: string, providerMode: string, profileKey: string, body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.put(`/v1/admin/model-profiles/${purpose}/${providerMode}/${profileKey}`, body, { headers: headers(true) }));
  },
  checkModelProfile(purpose: string, providerMode: string, profileKey: string) {
    return unwrap<Record<string, unknown>>(client.post(`/v1/admin/model-profiles/${purpose}/${providerMode}/${profileKey}/check`, {}, { headers: headers(true) }));
  },
  activateModelProfile(purpose: string, providerMode: string, profileKey: string) {
    return unwrap<Record<string, unknown>>(client.post(`/v1/admin/model-profiles/${purpose}/${providerMode}/${profileKey}/activate`, {}, { headers: headers(true) }));
  },
  preloadModelForPurpose(purpose: string) {
    return unwrap<Record<string, unknown>>(client.post(`/v1/admin/model-profiles/preload/${purpose}`, {}, { headers: headers(true), timeout: 180000 }));
  },
  answerFromKnowledge(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/chat/knowledge-answer', body, { headers: headers(), timeout: 60000 }));
  },
  updateRoute(routeId: string, body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.patch(`/v1/routes/${routeId}/status`, body, { headers: headers(true) }));
  },
  submitFeedback(body: Record<string, unknown>) {
    return unwrap<Record<string, unknown>>(client.post('/v1/routes/evaluations', body, { headers: headers(true) }));
  }
};
