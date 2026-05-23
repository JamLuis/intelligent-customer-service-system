<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { Activity, Database, GitBranch, MessagesSquare, PlugZap, RefreshCw, Route, Send, UploadCloud } from 'lucide-vue-next';
import { ElMessage } from 'element-plus';
import { api, setRuntimeConfig } from './api';

interface RuntimeState {
  token: string;
  projectId: string;
}

const runtime = reactive<RuntimeState>({ token: 'mock-token', projectId: 'P001' });
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
  <el-config-provider size="default">
    <main class="app-shell" v-loading="loading">
      <section class="topbar">
        <div>
          <h1>Smart Support Engineering Console</h1>
          <p>Mock API 联调工作台</p>
        </div>
        <div class="runtime-panel">
          <el-input v-model="runtime.projectId" placeholder="Project ID" />
          <el-input v-model="runtime.token" placeholder="Bearer token" show-password />
        </div>
      </section>

      <el-alert v-if="lastError" :title="lastError" type="error" show-icon :closable="false" />

      <section class="workspace-grid">
        <el-card shadow="never" class="panel chat-panel">
          <template #header>
            <div class="panel-title"><MessagesSquare :size="18" />问答诊断</div>
          </template>
          <el-input v-model="questionText" type="textarea" :rows="4" />
          <div class="toolbar">
            <el-button type="primary" :icon="Send" @click="createSession">创建会话</el-button>
            <el-button :disabled="!session" @click="supplementContext">补充上下文</el-button>
            <el-button type="success" :disabled="!session" @click="startDiagnosis">发起诊断</el-button>
          </div>
          <el-input v-model="followUpAnswer" class="mt" />
          <pre>{{ session }}</pre>
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-title"><Activity :size="18" />诊断结果</div>
          </template>
          <el-descriptions v-if="caseDetail" :column="1" border>
            <el-descriptions-item label="Case">{{ caseDetail.caseId }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ caseDetail.status }}</el-descriptions-item>
            <el-descriptions-item label="根因">{{ caseDetail.rootCause }}</el-descriptions-item>
            <el-descriptions-item label="置信度">{{ caseDetail.confidenceScore }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="等待诊断结果" />
        </el-card>

        <el-card shadow="never" class="panel wide">
          <template #header>
            <div class="panel-title"><GitBranch :size="18" />执行轨迹</div>
          </template>
          <div v-if="trace" class="trace-grid">
            <div>
              <h3>Graph Paths</h3>
              <pre>{{ trace.graphPaths }}</pre>
            </div>
            <div>
              <h3>MCP Calls</h3>
              <pre>{{ trace.mcpCalls }}</pre>
            </div>
            <div>
              <h3>Reasoning Steps</h3>
              <pre>{{ trace.reasoningSteps }}</pre>
            </div>
          </div>
          <el-empty v-else description="暂无执行轨迹" />
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-title"><UploadCloud :size="18" />知识录入</div>
          </template>
          <el-input v-model="sourceText" type="textarea" :rows="5" />
          <div class="toolbar">
            <el-button type="primary" :icon="Database" @click="createKnowledge">提交知识源</el-button>
            <el-button :icon="RefreshCw" :disabled="!knowledgeSource" @click="retryKnowledge">重跑任务</el-button>
          </div>
          <pre>{{ knowledgeSource }}</pre>
          <pre>{{ knowledgeTasks }}</pre>
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-title"><PlugZap :size="18" />MCP 能力</div>
          </template>
          <div class="toolbar">
            <el-button type="primary" @click="loadCapabilities">加载能力</el-button>
            <el-button :disabled="!capabilities" @click="disableFirstCapability">停用首个能力</el-button>
          </div>
          <el-table v-if="capabilities" :data="capabilities.items" height="220">
            <el-table-column prop="capabilityCode" label="能力" min-width="150" />
            <el-table-column prop="riskLevel" label="风险" width="80" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
          <pre>{{ capabilityImpact }}</pre>
        </el-card>

        <el-card shadow="never" class="panel wide">
          <template #header>
            <div class="panel-title"><Route :size="18" />路径反馈</div>
          </template>
          <div class="feedback-row">
            <el-radio-group v-model="feedbackRating">
              <el-radio-button label="valid">valid</el-radio-button>
              <el-radio-button label="partial">partial</el-radio-button>
              <el-radio-button label="invalid">invalid</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :disabled="!currentCaseId || !currentTraceId" @click="submitRouteFeedback">提交反馈</el-button>
          </div>
          <div class="trace-grid">
            <pre>{{ feedbackResult }}</pre>
            <pre>{{ routeResult }}</pre>
            <pre>{{ graphs }}</pre>
          </div>
        </el-card>
      </section>
    </main>
  </el-config-provider>
</template>
