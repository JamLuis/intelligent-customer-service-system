<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Database, Eye, FileUp, RefreshCw, TableProperties, UploadCloud } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';
import GraphEditor from '../../components/GraphEditor.vue';
import JsonBlock from '../../components/JsonBlock.vue';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const activeInput = ref<'structured' | 'file'>('structured');
const structuredFormat = ref('rule_text');
const sourceText = ref('');
const selectedFile = ref<File | null>(null);
const taxonomy = ref<Record<string, any> | null>(null);
const source = ref<Record<string, any> | null>(null);
const tasks = ref<Record<string, any> | null>(null);
const blocks = ref<Record<string, any> | null>(null);
const candidates = ref<Record<string, any> | null>(null);
const graphs = ref<Record<string, any> | null>(null);
const graphDetails = ref<Record<string, Record<string, any>>>({});
const draftResult = ref<Record<string, any> | null>(null);
const activeGraphId = ref('');
const loading = ref(false);
const overwriteDuplicate = ref(true);

const textLikeFiles = ['txt', 'md', 'csv', 'json', 'ini', 'log', 'sql', 'ddl'];

const categories = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.categories) ? taxonomy.value.categories : []);
const entityTypes = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.entityTypes) ? taxonomy.value.entityTypes : []);
const relationTypes = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.relationTypes) ? taxonomy.value.relationTypes : []);
const graphItems = computed<Record<string, any>[]>(() => Array.isArray(graphs.value?.items) ? graphs.value.items : []);
const activeGraph = computed(() => {
  const summary = graphItems.value.find((item) => item.graphId === activeGraphId.value) || graphItems.value[0] || null;
  if (!summary) {
    return null;
  }
  return graphDetails.value[String(summary.graphId)] || summary;
});
const uncategorizedCategory = computed(() => categories.value.find((item) => item.categoryId === 'uncategorized'));

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function loadTaxonomy() {
  syncRuntime();
  taxonomy.value = await api.listGraphCategories();
}

async function loadPreviewGraphs(sourceId?: string) {
  syncRuntime();
  graphs.value = await api.queryGraphs(sourceId ? { sourceId } : undefined);
  const firstGraphId = String(graphItems.value[0]?.graphId || '');
  if (firstGraphId) {
    activeGraphId.value = firstGraphId;
    await loadGraphDetail(firstGraphId);
  } else {
    activeGraphId.value = '';
  }
}

async function loadGraphDetail(graphId: string) {
  if (!graphId) {
    return;
  }
  graphDetails.value = {
    ...graphDetails.value,
    [graphId]: await api.getGraph(graphId)
  };
}

async function submitKnowledge() {
  loading.value = true;
  syncRuntime();
  try {
    const body = activeInput.value === 'structured' ? await structuredPayload() : await filePayload();
    ElMessage.info('正在解析并生成图谱，文档较大时可能需要几十秒');
    source.value = await api.createKnowledgeSource(body);
    tasks.value = await api.getKnowledgeTasks(String(source.value.sourceId));
    blocks.value = await api.getKnowledgeBlocks(String(source.value.sourceId));
    candidates.value = await api.getKnowledgeCandidates(String(source.value.sourceId));
    await loadPreviewGraphs(String(source.value.sourceId));
    ElMessage.success('知识源已提交，已刷新入图预览');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识源提交失败');
  } finally {
    loading.value = false;
  }
}

async function regeneratePreview() {
  if (!source.value?.sourceId) {
    ElMessage.warning('请先提交知识源');
    return;
  }
  loading.value = true;
  syncRuntime();
  try {
    await api.retryKnowledge(String(source.value.sourceId));
    tasks.value = await api.getKnowledgeTasks(String(source.value.sourceId));
    blocks.value = await api.getKnowledgeBlocks(String(source.value.sourceId));
    candidates.value = await api.getKnowledgeCandidates(String(source.value.sourceId));
    await loadPreviewGraphs(String(source.value.sourceId));
    ElMessage.success('已重新解析并生成候选关系');
  } finally {
    loading.value = false;
  }
}

async function structuredPayload() {
  if (!sourceText.value.trim()) {
    throw new Error('请输入结构化文本内容');
  }
  return {
    sourceType: 'text',
    contentMode: 'structured_text',
    structureFormat: structuredFormat.value,
    rawText: sourceText.value,
    overwriteDuplicate: overwriteDuplicate.value,
    sensitivityLevel: 'internal'
  };
}

function fileExtension(fileName: string) {
  const extension = fileName.split('.').pop()?.toLowerCase() || '';
  return extension === 'jpeg' ? 'jpg' : extension;
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
  if (!textLikeFiles.includes(extension)) {
    throw new Error('当前直连入图仅支持 txt、md、csv、json、ini、log、sql、ddl 文本类文件；图片、PDF、Office 文件需接入 OCR/文档解析服务后再提交');
  }
  const sourceType = 'text';
  const rawText = await readFileText(file);
  return {
    sourceType,
    contentMode: 'unstructured_file',
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type || 'application/octet-stream',
    rawText,
    overwriteDuplicate: overwriteDuplicate.value,
    sensitivityLevel: 'internal'
  };
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] || null;
  if (selectedFile.value) {
    source.value = null;
    tasks.value = null;
    blocks.value = null;
    candidates.value = null;
    graphs.value = null;
    graphDetails.value = {};
    draftResult.value = null;
    activeGraphId.value = '';
  }
}

async function saveGraphDraft(payload: { graphId: string; nodes: unknown[]; edges: unknown[] }) {
  loading.value = true;
  syncRuntime();
  try {
    draftResult.value = await api.updateGraphDraft(payload.graphId, {
      editReason: 'knowledge ingestion preview edit',
      graphCategoryId: activeGraph.value?.graphCategoryId,
      nodes: payload.nodes,
      edges: payload.edges
    });
    ElMessage.success('预览图谱草稿已保存');
  } finally {
    loading.value = false;
  }
}

watch(graphItems, (items) => {
  activeGraphId.value = String(items[0]?.graphId || '');
});

watch(activeGraphId, (graphId) => {
  if (graphId) {
    loadGraphDetail(graphId);
  }
});

onMounted(async () => {
  await loadTaxonomy();
  await loadPreviewGraphs();
});
</script>

<template>
  <section class="knowledge-two-column" v-loading="loading">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-title"><UploadCloud :size="18" />知识录入</div>
      </template>

      <div class="knowledge-form-grid">
        <el-alert
          :title="uncategorizedCategory?.description || '系统会先解析并存储证据块、候选实体和候选关系，再由模型检索或后续治理流程判断更贴近的知识范围。'"
          type="info"
          :closable="false"
        />
      </div>

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
            <el-input v-model="sourceText" type="textarea" :rows="9" placeholder="粘贴规则、配置、接口协议、SQL 字段说明等结构化文本" />
          </div>
        </el-tab-pane>
        <el-tab-pane name="file">
          <template #label>
            <span class="tab-label"><FileUp :size="16" />非结构化文件</span>
          </template>
          <div class="file-drop-zone">
            <input type="file" accept=".txt,.md,.csv,.json,.ini,.log,.sql,.ddl" @change="onFileChange" />
            <strong>{{ selectedFile?.name || '选择日志、配置、CSV、JSON、SQL 或 Markdown 文件' }}</strong>
            <span>当前直连入图支持文本类文件；图片、PDF、Office 文件需接入 OCR/文档解析服务</span>
          </div>
        </el-tab-pane>
      </el-tabs>
      <div class="toolbar">
        <el-button type="primary" :icon="Database" @click="submitKnowledge">提交并生成预览</el-button>
        <el-button :icon="RefreshCw" :disabled="!source?.sourceId" @click="regeneratePreview">重新生成关系</el-button>
        <el-checkbox v-model="overwriteDuplicate">重复资料自动覆盖重建</el-checkbox>
      </div>
      <JsonBlock :value="source" />
      <JsonBlock :value="tasks" />
      <JsonBlock :value="blocks" />
      <JsonBlock :value="candidates" />
    </el-card>

    <el-card shadow="never" class="panel-card graph-preview-card">
      <template #header>
        <div class="panel-title"><Eye :size="18" />入图预览</div>
      </template>
      <div class="graph-header-actions">
        <el-select v-model="activeGraphId" placeholder="选择预览子图">
          <el-option v-for="item in graphItems" :key="item.graphId" :label="item.graphName || item.graphId" :value="item.graphId" />
        </el-select>
        <el-button @click="loadPreviewGraphs">刷新预览</el-button>
      </div>
      <GraphEditor :graph="activeGraph" :entity-types="entityTypes" :relation-types="relationTypes" @save="saveGraphDraft" />
      <JsonBlock :value="draftResult" />
    </el-card>
  </section>
</template>
