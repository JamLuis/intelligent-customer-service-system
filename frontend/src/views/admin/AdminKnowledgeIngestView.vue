<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Database, Eye, FileUp, TableProperties, UploadCloud } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';
import GraphEditor from '../../components/GraphEditor.vue';
import JsonBlock from '../../components/JsonBlock.vue';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const activeInput = ref<'structured' | 'file'>('structured');
const structuredFormat = ref('rule_text');
// 不再硬编码 categoryId 与示例文本；首次渲染时由 UI 拉取本租户 taxonomy 后由用户选择。
const selectedGraphCategoryId = ref('');
const sourceText = ref('');
const selectedFile = ref<File | null>(null);
const taxonomy = ref<Record<string, any> | null>(null);
const source = ref<Record<string, any> | null>(null);
const tasks = ref<Record<string, any> | null>(null);
const graphs = ref<Record<string, any> | null>(null);
const draftResult = ref<Record<string, any> | null>(null);
const activeGraphId = ref('');
const loading = ref(false);

const supportedFiles = ['doc', 'docx', 'xls', 'xlsx', 'pdf', 'jpg', 'png', 'txt', 'md', 'csv', 'json', 'ini', 'log'];
const textLikeFiles = ['txt', 'md', 'csv', 'json', 'ini', 'log'];

const categories = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.categories) ? taxonomy.value.categories : []);
const entityTypes = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.entityTypes) ? taxonomy.value.entityTypes : []);
const relationTypes = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.relationTypes) ? taxonomy.value.relationTypes : []);
const graphItems = computed<Record<string, any>[]>(() => Array.isArray(graphs.value?.items) ? graphs.value.items : []);
const activeGraph = computed(() => graphItems.value.find((item) => item.graphId === activeGraphId.value) || graphItems.value[0] || null);
const selectedCategory = computed(() => categories.value.find((item) => item.categoryId === selectedGraphCategoryId.value));

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function loadTaxonomy() {
  syncRuntime();
  taxonomy.value = await api.listGraphCategories();
}

async function loadPreviewGraphs() {
  syncRuntime();
  graphs.value = await api.queryGraphs({ graphCategoryId: selectedGraphCategoryId.value });
}

async function submitKnowledge() {
  loading.value = true;
  syncRuntime();
  try {
    const body = activeInput.value === 'structured' ? await structuredPayload() : await filePayload();
    source.value = await api.createKnowledgeSource(body);
    tasks.value = await api.getKnowledgeTasks(String(source.value.sourceId));
    await loadPreviewGraphs();
    ElMessage.success('知识源已提交，已刷新入图预览');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识源提交失败');
  } finally {
    loading.value = false;
  }
}

async function structuredPayload() {
  return {
    sourceType: 'text',
    contentMode: 'structured_text',
    structureFormat: structuredFormat.value,
    graphCategoryId: selectedGraphCategoryId.value,
    graphCategoryName: selectedCategory.value?.categoryName,
    rawText: sourceText.value,
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
  if (!supportedFiles.includes(extension)) {
    throw new Error(`暂不支持 ${extension || '未知'} 文件类型`);
  }
  const sourceType = textLikeFiles.includes(extension) ? 'text' : extension;
  const rawText = textLikeFiles.includes(extension) ? await readFileText(file) : undefined;
  return {
    sourceType,
    contentMode: 'unstructured_file',
    graphCategoryId: selectedGraphCategoryId.value,
    graphCategoryName: selectedCategory.value?.categoryName,
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
      editReason: 'knowledge ingestion preview edit',
      graphCategoryId: selectedGraphCategoryId.value,
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

watch(selectedGraphCategoryId, () => {
  loadPreviewGraphs();
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
        <el-select v-model="selectedGraphCategoryId" filterable placeholder="选择图谱分类">
          <el-option v-for="item in categories" :key="item.categoryId" :label="item.categoryName" :value="item.categoryId" />
        </el-select>
        <el-alert v-if="selectedCategory" :title="selectedCategory.description" type="info" :closable="false" />
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
            <input type="file" accept=".doc,.docx,.xls,.xlsx,.pdf,.jpg,.jpeg,.png,.txt,.md,.csv,.json,.ini,.log" @change="onFileChange" />
            <strong>{{ selectedFile?.name || '选择运维文档、截图、日志或表格文件' }}</strong>
            <span>文件会按当前图谱分类进入解析、抽取和入图任务</span>
          </div>
        </el-tab-pane>
      </el-tabs>
      <div class="toolbar">
        <el-button type="primary" :icon="Database" @click="submitKnowledge">提交并生成预览</el-button>
      </div>
      <JsonBlock :value="source" />
      <JsonBlock :value="tasks" />
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
