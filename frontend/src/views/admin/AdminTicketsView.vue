<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { GitPullRequest, RefreshCw } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';
import JsonBlock from '../../components/JsonBlock.vue';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const tickets = ref<Record<string, any>[]>([
  { ticketId: 'TCK-1001', issue: '告警不准', object: 'TC-003', status: 'concluded', confidence: 88, traceId: 'mock-trace-1001' },
  { ticketId: 'TCK-1002', issue: '设备离线', object: 'AIS-21', status: 'waiting_context', confidence: 0, traceId: '-' },
  { ticketId: 'TCK-1003', issue: '统计异常', object: '月报任务', status: 'reviewing', confidence: 72, traceId: 'mock-trace-1003' }
]);
const activeTicket = ref<Record<string, any> | null>(tickets.value[0]);
const trace = ref<Record<string, any> | null>(null);
const loading = ref(false);

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function createMockTicket() {
  loading.value = true;
  syncRuntime();
  try {
    const session = await api.createSession({ questionText: '告警不准，片区东港，船舶测试船，日期今天', deviceId: 'TC-003' });
    const diagnosis = await api.startDiagnosis({ sessionId: session.sessionId, mockScenario: 'success' });
    const item = {
      ticketId: `TCK-${String(tickets.value.length + 1001)}`,
      issue: '告警不准',
      object: 'TC-003',
      status: diagnosis.caseStatus,
      confidence: 88,
      caseId: diagnosis.caseId,
      traceId: diagnosis.traceId
    };
    tickets.value.unshift(item);
    activeTicket.value = item;
    trace.value = await api.getTrace(String(diagnosis.traceId));
    ElMessage.success('已生成模拟问题工单');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="admin-grid" v-loading="loading">
    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><GitPullRequest :size="18" />用户问题工单</div>
      </template>
      <div class="toolbar top-toolbar">
        <el-button type="primary" :icon="RefreshCw" @click="createMockTicket">生成联调工单</el-button>
      </div>
      <el-table :data="tickets" highlight-current-row @current-change="activeTicket = $event">
        <el-table-column prop="ticketId" label="工单" width="120" />
        <el-table-column prop="issue" label="问题" min-width="120" />
        <el-table-column prop="object" label="对象" width="130" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column prop="confidence" label="可信度" width="100" />
        <el-table-column prop="traceId" label="调用链" min-width="220" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title">工单追溯</div>
      </template>
      <JsonBlock :value="activeTicket" />
      <JsonBlock :value="trace" />
    </el-card>
  </section>
</template>
