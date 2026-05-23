<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { CheckCircle2, CircleHelp, LoaderCircle, Send, Sparkles } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../api';

type ChatMode = 'expert' | 'guided';
type Role = 'user' | 'assistant' | 'system';

interface Message {
  id: string;
  role: Role;
  text: string;
}

interface GuidedContext {
  issue: string;
  region: string;
  targetObject: string;
  occurredDate: string;
  extra: string;
}

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const mode = ref<ChatMode>('guided');
const expertPrompt = ref('');
const guided = ref<GuidedContext>({ issue: '', region: '', targetObject: '', occurredDate: '', extra: '' });
const loading = ref(false);
const session = ref<Record<string, any> | null>(null);
const diagnosis = ref<Record<string, any> | null>(null);
const caseDetail = ref<Record<string, any> | null>(null);
const trace = ref<Record<string, any> | null>(null);

const messages = ref<Message[]>([
  {
    id: 'welcome',
    role: 'assistant',
    text: '请选择专家模式或引导式客服模式。专家模式适合一次性输入完整上下文；引导模式会逐步补齐必要参数。'
  }
]);

const missingFields = computed(() => {
  if (mode.value === 'expert') return [];
  return [
    ['region', '片区'],
    ['targetObject', '对象'],
    ['occurredDate', '发生日期']
  ].filter(([key]) => !guided.value[key as keyof GuidedContext]).map(([, label]) => label);
});

const questionText = computed(() => {
  if (mode.value === 'expert') return expertPrompt.value;
  return [
    `问题：${guided.value.issue}`,
    guided.value.region ? `片区：${guided.value.region}` : '',
    guided.value.targetObject ? `对象：${guided.value.targetObject}` : '',
    guided.value.occurredDate ? `日期：${guided.value.occurredDate}` : '',
    guided.value.extra ? `补充：${guided.value.extra}` : ''
  ].filter(Boolean).join('\n');
});

const executionSteps = computed(() => {
  const steps = trace.value?.reasoningSteps || [];
  if (Array.isArray(steps) && steps.length > 0) return steps;
  return [
    { stepName: '参数收集', status: session.value ? 'success' : 'waiting', outputSummary: session.value ? '已生成会话' : '等待输入' },
    { stepName: '知识检索', status: diagnosis.value ? 'success' : 'waiting', outputSummary: diagnosis.value ? '已调用 AI Service' : '等待诊断' },
    { stepName: 'MCP 取证', status: trace.value ? 'success' : 'waiting', outputSummary: trace.value ? '已返回工具证据' : '等待工具调用' }
  ];
});

const results = computed(() => {
  if (!caseDetail.value) return [];
  const confidence = Number(caseDetail.value.confidenceScore || 0);
  return [
    {
      title: String(caseDetail.value.rootCause || ''),
      confidence,
      evidence: '知识图谱路径、RAG 推理步骤、MCP 工具证据'
    }
  ];
});

function append(role: Role, text: string) {
  messages.value.push({ id: crypto.randomUUID(), role, text });
}

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function runDiagnosis() {
  syncRuntime();
  if (mode.value === 'guided' && missingFields.value.length > 0) {
    append('assistant', `还需要补充：${missingFields.value.join('、')}。`);
    return;
  }

  loading.value = true;
  session.value = null;
  diagnosis.value = null;
  caseDetail.value = null;
  trace.value = null;
  try {
    append('user', questionText.value);
    session.value = await api.createSession({ questionText: questionText.value, projectId: props.runtime.projectId });
    diagnosis.value = await api.startDiagnosis({ sessionId: session.value.sessionId, mockScenario: 'success' });
    caseDetail.value = await api.getCase(String(diagnosis.value.caseId));
    trace.value = await api.getTrace(String(diagnosis.value.traceId));
    append('assistant', `已完成诊断，最高可信度 ${caseDetail.value.confidenceScore}%，请查看右侧结果列表。`);
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || '诊断失败';
    append('assistant', message);
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="chat-layout" v-loading="loading">
    <div class="chat-main panel-surface">
      <div class="chat-mode-bar">
        <el-segmented v-model="mode" :options="[{ label: '引导式客服模式', value: 'guided' }, { label: '专家模式', value: 'expert' }]" />
      </div>

      <div class="message-list">
        <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
          <div class="message-bubble">{{ message.text }}</div>
        </div>
      </div>

      <div v-if="mode === 'guided'" class="guided-form">
        <el-input v-model="guided.issue" placeholder="问题类型，例如：告警不准、设备离线、统计异常" />
        <el-input v-model="guided.region" placeholder="片区" />
        <el-input v-model="guided.targetObject" placeholder="对象（如设备 / 资产 / 人员等）" />
        <el-date-picker v-model="guided.occurredDate" type="date" value-format="YYYY-MM-DD" placeholder="发生日期" />
        <el-input v-model="guided.extra" type="textarea" :rows="3" placeholder="补充现象" />
        <el-alert v-if="missingFields.length" :title="`待补充：${missingFields.join('、')}`" type="warning" :closable="false" show-icon />
      </div>

      <div v-else class="expert-form">
        <el-input v-model="expertPrompt" type="textarea" :rows="6" placeholder="一次性输入完整问题、对象、时间、约束和上下文" />
      </div>

      <div class="chat-actions">
        <el-button type="primary" :icon="Send" @click="runDiagnosis">发送并诊断</el-button>
      </div>
    </div>

    <aside class="result-side">
      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-title"><LoaderCircle :size="18" />执行过程</div>
        </template>
        <el-timeline>
          <el-timeline-item v-for="(step, index) in executionSteps" :key="index" :type="step.status === 'success' ? 'success' : 'info'">
            <strong>{{ step.stepName }}</strong>
            <p>{{ step.outputSummary }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-title"><Sparkles :size="18" />可能结果</div>
        </template>
        <el-empty v-if="results.length === 0" description="完成诊断后显示结果" />
        <div v-for="item in results" v-else :key="item.title" class="result-item">
          <div class="result-title"><CheckCircle2 :size="16" />{{ item.title }}</div>
          <el-progress :percentage="item.confidence" :stroke-width="8" />
          <p>{{ item.evidence }}</p>
        </div>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-title"><CircleHelp :size="18" />工单追踪</div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Session">{{ session?.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Case">{{ diagnosis?.caseId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Trace">{{ diagnosis?.traceId || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </aside>
  </section>
</template>
