<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppTopbar from '../components/AppTopbar.vue';
import DiagnosisPanel from '../components/DiagnosisPanel.vue';
import DiagnosisResultPanel from '../components/DiagnosisResultPanel.vue';
import KnowledgePanel from '../components/KnowledgePanel.vue';
import McpCapabilityPanel from '../components/McpCapabilityPanel.vue';
import RouteFeedbackPanel from '../components/RouteFeedbackPanel.vue';
import TracePanel from '../components/TracePanel.vue';
import { api, setRuntimeConfig, type RuntimeConfig } from '../api';

const runtime = reactive<RuntimeConfig>({ token: 'mock-token', projectId: 'P001' });
const loading = ref(false);
const questionText = ref('3号塔吊昨天超载了但没有报警');
const followUpAnswer = ref('设备 TC-003，时间范围为昨天 08:00-18:00');
const sourceText = ref('设备告警规则：TC-003 绑定 AR-17，超载阈值 90%，启用状态 true。');
const feedbackRating = ref<'valid' | 'invalid' | 'partial'>('valid');
const lastError = ref('');

const session = ref<Record<string, any> | null>(null);
const diagnosis = ref<Record<string, any> | null>(null);
const caseDetail = ref<Record<string, any> | null>(null);
const trace = ref<Record<string, any> | null>(null);
const knowledgeSource = ref<Record<string, any> | null>(null);
const knowledgeTasks = ref<Record<string, any> | null>(null);
const graphs = ref<Record<string, any> | null>(null);
const capabilities = ref<Record<string, any> | null>(null);
const capabilityImpact = ref<Record<string, any> | null>(null);
const routeResult = ref<Record<string, any> | null>(null);
const feedbackResult = ref<Record<string, any> | null>(null);

const currentCaseId = computed(() => String(diagnosis.value?.caseId || caseDetail.value?.caseId || ''));
const currentTraceId = computed(() => String(diagnosis.value?.traceId || trace.value?.traceId || ''));
const canSubmitFeedback = computed(() => Boolean(currentCaseId.value && currentTraceId.value));

function syncRuntime() {
  setRuntimeConfig({ token: runtime.token, projectId: runtime.projectId });
}

async function runAction(action: () => Promise<void>) {
  syncRuntime();
  loading.value = true;
  lastError.value = '';
  try {
    await action();
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || '请求失败';
    lastError.value = message;
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
}

async function createSession() {
  await runAction(async () => {
    session.value = await api.createSession({ questionText: questionText.value, projectId: runtime.projectId, deviceId: 'TC-003' });
    ElMessage.success('会话已创建');
  });
}

async function supplementContext() {
  if (!session.value?.sessionId) return;
  await runAction(async () => {
    session.value = await api.updateSessionContext(String(session.value?.sessionId), { followUpAnswer: followUpAnswer.value, contextPatch: { deviceId: 'TC-003' } });
    ElMessage.success('上下文已补充');
  });
}

async function startDiagnosis() {
  if (!session.value?.sessionId) return;
  await runAction(async () => {
    diagnosis.value = await api.startDiagnosis({ sessionId: session.value?.sessionId, mockScenario: 'success' });
    await loadCaseAndTrace();
    ElMessage.success('诊断已完成');
  });
}

async function loadCaseAndTrace() {
  if (currentCaseId.value) {
    caseDetail.value = await api.getCase(currentCaseId.value);
  }
  if (currentTraceId.value) {
    trace.value = await api.getTrace(currentTraceId.value);
  }
}

async function createKnowledge() {
  await runAction(async () => {
    knowledgeSource.value = await api.createKnowledgeSource({ sourceType: 'text', rawText: sourceText.value, sensitivityLevel: 'internal' });
    knowledgeTasks.value = await api.getKnowledgeTasks(String(knowledgeSource.value.sourceId));
    graphs.value = await api.queryGraphs();
    ElMessage.success('知识源已提交并生成 Mock 入图任务');
  });
}

async function retryKnowledge() {
  if (!knowledgeSource.value?.sourceId) return;
  await runAction(async () => {
    knowledgeTasks.value = await api.retryKnowledge(String(knowledgeSource.value?.sourceId));
    ElMessage.success('已触发重跑');
  });
}

async function loadCapabilities() {
  await runAction(async () => {
    capabilities.value = await api.listCapabilities();
    const first = (capabilities.value.items || [])[0];
    if (first) {
      capabilityImpact.value = await api.capabilityImpact(first.capabilityId);
    }
  });
}

async function disableFirstCapability() {
  const first = (capabilities.value?.items || [])[0];
  if (!first) return;
  await runAction(async () => {
    await api.updateCapabilityStatus(first.capabilityId, { targetStatus: 'disabled', reason: '联调停用验证', impactConfirmed: true });
    await loadCapabilities();
    ElMessage.success('能力状态已更新');
  });
}

async function submitRouteFeedback() {
  await runAction(async () => {
    feedbackResult.value = await api.submitFeedback({ caseId: currentCaseId.value, traceId: currentTraceId.value, routeId: 'route-device-alarm', rating: feedbackRating.value, comment: '联调反馈' });
    routeResult.value = await api.updateRoute('route-device-alarm', { action: feedbackRating.value === 'valid' ? 'activate' : 'rebuild', reason: '根据答案反馈更新路径' });
    ElMessage.success('路径反馈已提交');
  });
}
</script>

<template>
  <main class="app-shell" v-loading="loading">
    <AppTopbar :runtime="runtime" />

    <el-alert v-if="lastError" :title="lastError" type="error" show-icon :closable="false" />

    <section class="workspace-grid">
      <DiagnosisPanel
        v-model:question-text="questionText"
        v-model:follow-up-answer="followUpAnswer"
        :session="session"
        @create-session="createSession"
        @supplement-context="supplementContext"
        @start-diagnosis="startDiagnosis"
      />
      <DiagnosisResultPanel :case-detail="caseDetail" />
      <TracePanel :trace="trace" />
      <KnowledgePanel
        v-model:source-text="sourceText"
        :knowledge-source="knowledgeSource"
        :knowledge-tasks="knowledgeTasks"
        @create-knowledge="createKnowledge"
        @retry-knowledge="retryKnowledge"
      />
      <McpCapabilityPanel
        :capabilities="capabilities"
        :capability-impact="capabilityImpact"
        @load-capabilities="loadCapabilities"
        @disable-first-capability="disableFirstCapability"
      />
      <RouteFeedbackPanel
        v-model:feedback-rating="feedbackRating"
        :can-submit="canSubmitFeedback"
        :feedback-result="feedbackResult"
        :route-result="routeResult"
        :graphs="graphs"
        @submit-route-feedback="submitRouteFeedback"
      />
    </section>
  </main>
</template>
