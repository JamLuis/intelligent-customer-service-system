<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Database, GitBranch, UploadCloud } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';
import JsonBlock from '../../components/JsonBlock.vue';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const sourceText = ref('设备告警规则：TC-003 绑定 AR-17，超载阈值 90%，启用状态 true。');
const source = ref<Record<string, any> | null>(null);
const tasks = ref<Record<string, any> | null>(null);
const graphs = ref<Record<string, any> | null>(null);
const loading = ref(false);

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function submitKnowledge() {
  loading.value = true;
  syncRuntime();
  try {
    source.value = await api.createKnowledgeSource({ sourceType: 'text', rawText: sourceText.value, sensitivityLevel: 'internal' });
    tasks.value = await api.getKnowledgeTasks(String(source.value.sourceId));
    graphs.value = await api.queryGraphs();
    ElMessage.success('知识源已提交');
  } finally {
    loading.value = false;
  }
}

async function loadGraphs() {
  syncRuntime();
  graphs.value = await api.queryGraphs();
}

onMounted(loadGraphs);
</script>

<template>
  <section class="admin-grid" v-loading="loading">
    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><UploadCloud :size="18" />知识库录入</div>
      </template>
      <el-input v-model="sourceText" type="textarea" :rows="6" />
      <div class="toolbar">
        <el-button type="primary" :icon="Database" @click="submitKnowledge">提交并入图</el-button>
      </div>
      <JsonBlock :value="source" />
      <JsonBlock :value="tasks" />
    </el-card>

    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><GitBranch :size="18" />知识图谱维护</div>
      </template>
      <el-button @click="loadGraphs">刷新图谱资产</el-button>
      <JsonBlock :value="graphs" />
    </el-card>
  </section>
</template>
