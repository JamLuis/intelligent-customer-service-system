<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Database, FileUp, GitBranch, TableProperties, UploadCloud } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';
import GraphEditor from '../../components/GraphEditor.vue';
import JsonBlock from '../../components/JsonBlock.vue';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const activeInput = ref<'structured' | 'file'>('structured');
const structuredFormat = ref('rule_text');
const sourceText = ref('设备告警规则：TC-003 绑定 AR-17，超载阈值 90%，启用状态 true。');
const selectedFile = ref<File | null>(null);
const source = ref<Record<string, any> | null>(null);
const tasks = ref<Record<string, any> | null>(null);
const graphs = ref<Record<string, any> | null>(null);
const draftResult = ref<Record<string, any> | null>(null);
const activeGraphId = ref('');
const loading = ref(false);

const supportedFiles = ['doc', 'docx', 'xls', 'xlsx', 'pdf', 'jpg', 'png', 'txt', 'md', 'csv', 'json', 'ini', 'log'];
const textLikeFiles = ['txt', 'md', 'csv', 'json', 'ini', 'log'];

const graphItems = computed<Record<string, any>[]>(() => {
  const items = graphs.value?.items;
  return Array.isArray(items) ? items : [];
});

const activeGraph = computed(() => graphItems.value.find((item) => item.graphId === activeGraphId.value) || graphItems.value[0] || null);

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function submitKnowledge() {
  loading.value = true;
  syncRuntime();
  try {
    const body = activeInput.value === 'structured' ? await structuredPayload() : await filePayload();
    source.value = await api.createKnowledgeSource(body);
    tasks.value = await api.getKnowledgeTasks(String(source.value.sourceId));
    graphs.value = await api.queryGraphs();
    ElMessage.success('知识源已提交');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识源提交失败');
  } finally {
    loading.value = false;
  }
}

async function loadGraphs() {
  syncRuntime();
  graphs.value = await api.queryGraphs();
}

async function structuredPayload() {
  return {
    sourceType: 'text',
    contentMode: 'structured_text',
    structureFormat: structuredFormat.value,
    rawText: sourceText.value,
    sensitivityLevel: 'internal'
  };
}

function fileExtension(fileName: string) {
  const extension = fileName.split('.').pop()?.toLowerCase() || '';
  if (extension === 'jpeg') {
    return 'jpg';
  }
  return extension;
}

function readFileText(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}

async function filePayload() {
  if (!selectedFile.value) {
    throw new Error('请选择非结构化文件');
  }
  const file = selectedFile.value;
  const extension = fileExtension(file.name);
  if (!supportedFiles.includes(extension)) {
    throw new Error(`暂不支持 ${extension || '未知'} 文件类型`);
  }
  const sourceType = extension === 'txt' || extension === 'md' || extension === 'csv' || extension === 'json' || extension === 'ini' || extension === 'log' ? 'text' : extension;
  const rawText = textLikeFiles.includes(extension) ? await readFileText(file) : undefined;
  return {
    sourceType,
    contentMode: 'unstructured_file',
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type || 'application/octet-stream',
    rawText,
    sensitivityLevel: 'internal'
  };
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] || null;
}

async function saveGraphDraft(payload: { graphId: string; nodes: unknown[]; edges: unknown[] }) {
  loading.value = true;
  syncRuntime();
  try {
    draftResult.value = await api.updateGraphDraft(payload.graphId, {
      editReason: 'knowledge graph visual edit',
      nodes: payload.nodes,
      edges: payload.edges
    });
    ElMessage.success('图谱草稿已保存');
  } finally {
    loading.value = false;
  }
}

watch(graphItems, (items) => {
  if (!activeGraphId.value && items[0]?.graphId) {
    activeGraphId.value = String(items[0].graphId);
  }
});

onMounted(loadGraphs);
</script>

<template>
  <section class="admin-grid" v-loading="loading">
    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><UploadCloud :size="18" />知识库录入</div>
      </template>
      <el-tabs v-model="activeInput">
        <el-tab-pane name="structured">
          <template #label>
            <span class="tab-label"><TableProperties :size="16" />结构化文本</span>
          </template>
          <div class="knowledge-form-grid">
            <el-select v-model="structuredFormat" placeholder="结构类型">
              <el-option label="规则文本" value="rule_text" />
              <el-option label="键值配置" value="key_value" />
              <el-option label="JSON 片段" value="json" />
              <el-option label="CSV 表格" value="csv" />
            </el-select>
            <el-input v-model="sourceText" type="textarea" :rows="7" placeholder="粘贴规则、配置、接口协议、SQL 字段说明等结构化文本" />
          </div>
        </el-tab-pane>
        <el-tab-pane name="file">
          <template #label>
            <span class="tab-label"><FileUp :size="16" />非结构化文件</span>
          </template>
          <div class="file-drop-zone">
            <input type="file" accept=".doc,.docx,.xls,.xlsx,.pdf,.jpg,.jpeg,.png,.txt,.md,.csv,.json,.ini,.log" @change="onFileChange" />
            <strong>{{ selectedFile?.name || '选择运维文档、截图、日志或表格文件' }}</strong>
            <span>支持 doc/docx/xls/xlsx/pdf/jpg/png 以及 txt/md/csv/json/ini/log 文本类文件</span>
          </div>
        </el-tab-pane>
      </el-tabs>
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
      <div class="graph-header-actions">
        <el-select v-model="activeGraphId" placeholder="选择子图">
          <el-option v-for="item in graphItems" :key="item.graphId" :label="item.graphName || item.graphId" :value="item.graphId" />
        </el-select>
        <el-button @click="loadGraphs">刷新图谱资产</el-button>
      </div>
      <GraphEditor :graph="activeGraph" @save="saveGraphDraft" />
      <JsonBlock :value="draftResult" />
    </el-card>
  </section>
</template>
