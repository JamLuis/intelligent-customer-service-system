<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Filter, GitBranch, Network } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';
import BudgetBanner from '../../components/BudgetBanner.vue';
import GraphEditor from '../../components/GraphEditor.vue';
import JsonBlock from '../../components/JsonBlock.vue';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const taxonomy = ref<Record<string, any> | null>(null);
const graphs = ref<Record<string, any> | null>(null);
const draftResult = ref<Record<string, any> | null>(null);
const selectedCategoryId = ref('');
const selectedEntityType = ref('');
const selectedRelationType = ref('');
const activeGraphId = ref('');
const loading = ref(false);

const categories = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.categories) ? taxonomy.value.categories : []);
const entityTypes = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.entityTypes) ? taxonomy.value.entityTypes : []);
const relationTypes = computed<Record<string, any>[]>(() => Array.isArray(taxonomy.value?.relationTypes) ? taxonomy.value.relationTypes : []);
const graphItems = computed<Record<string, any>[]>(() => Array.isArray(graphs.value?.items) ? graphs.value.items : []);
const activeGraph = computed(() => graphItems.value.find((item) => item.graphId === activeGraphId.value) || graphItems.value[0] || null);

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function loadTaxonomy() {
  syncRuntime();
  taxonomy.value = await api.listGraphCategories();
}

async function loadGraphs() {
  loading.value = true;
  syncRuntime();
  try {
    graphs.value = await api.queryGraphs({
      graphCategoryId: selectedCategoryId.value || undefined,
      entityType: selectedEntityType.value || undefined,
      relationType: selectedRelationType.value || undefined,
      pageSize: 50
    });
  } finally {
    loading.value = false;
  }
}

function selectGraph(graphId: string) {
  activeGraphId.value = graphId;
}

async function saveGraphDraft(payload: { graphId: string; nodes: unknown[]; edges: unknown[] }) {
  loading.value = true;
  syncRuntime();
  try {
    draftResult.value = await api.updateGraphDraft(payload.graphId, {
      editReason: 'historical graph maintenance',
      graphCategoryId: activeGraph.value?.graphCategoryId,
      nodes: payload.nodes,
      edges: payload.edges
    });
    ElMessage.success('历史图谱草稿已保存');
  } finally {
    loading.value = false;
  }
}

async function applyGraphObjectAction(payload: { objectType: 'entity' | 'relation'; objectId: string; action: 'freeze' | 'unfreeze' }) {
  loading.value = true;
  syncRuntime();
  try {
    const body = { action: payload.action, reason: 'graph maintenance action' };
    draftResult.value = payload.objectType === 'entity'
      ? await api.applyEntityAction(payload.objectId, body)
      : await api.applyRelationAction(payload.objectId, body);
    ElMessage.success(payload.action === 'freeze' ? '已冻结图谱对象' : '已解冻图谱对象');
    await loadGraphs();
  } catch (e: any) {
    if (String(e?.response?.data?.code || '').includes('EMBEDDING_VERSION_MISMATCH')) {
      ElMessage.warning('Embedding 版本不匹配，请先重建向量索引');
    }
    throw e;
  } finally {
    loading.value = false;
  }
}

watch(graphItems, (items) => {
  if (!items.some((item) => item.graphId === activeGraphId.value)) {
    activeGraphId.value = String(items[0]?.graphId || '');
  }
});

onMounted(async () => {
  await loadTaxonomy();
  await loadGraphs();
});
</script>

<template>
  <section class="graph-maintenance-layout" v-loading="loading">
    <el-card shadow="never" class="panel-card graph-library-card">
      <template #header>
        <div class="panel-title"><Filter :size="18" />动态图谱分类</div>
      </template>
      <div class="filter-stack">
        <el-select v-model="selectedCategoryId" clearable filterable placeholder="全部图谱分类">
          <el-option v-for="item in categories" :key="item.categoryId" :label="item.categoryName" :value="item.categoryId" />
        </el-select>
        <el-select v-model="selectedEntityType" clearable filterable placeholder="实体类型">
          <el-option v-for="item in entityTypes" :key="item.type" :label="item.label" :value="item.type" />
        </el-select>
        <el-select v-model="selectedRelationType" clearable filterable placeholder="关系类型">
          <el-option v-for="item in relationTypes" :key="item.type" :label="item.label" :value="item.type" />
        </el-select>
        <el-button type="primary" @click="loadGraphs">查询图谱</el-button>
      </div>

      <div class="graph-category-list">
        <button
          v-for="item in graphItems"
          :key="item.graphId"
          class="graph-list-item"
          :class="{ active: item.graphId === activeGraphId }"
          type="button"
          @click="selectGraph(item.graphId)"
        >
          <strong>{{ item.graphName }}</strong>
          <span>{{ item.graphCategoryName }} · {{ item.nodes?.length || 0 }} 实体 / {{ item.edges?.length || 0 }} 关系</span>
          <small>{{ item.status }} · {{ Math.round((item.confidence || 0) * 100) }}%</small>
        </button>
      </div>
    </el-card>

    <el-card shadow="never" class="panel-card graph-maintenance-card">
      <template #header>
        <div class="panel-title"><GitBranch :size="18" />历史知识图谱维护</div>
      </template>
      <div class="graph-summary-strip" v-if="activeGraph">
        <div>
          <span>分类</span>
          <strong>{{ activeGraph.graphCategoryName }}</strong>
        </div>
        <div>
          <span>版本</span>
          <strong>{{ activeGraph.activeRevisionId }}</strong>
        </div>
        <div>
          <span>状态</span>
          <strong>{{ activeGraph.status }}</strong>
        </div>
        <div>
          <span>来源</span>
          <strong>{{ activeGraph.sourceRefs?.length || 0 }} 个</strong>
        </div>
      </div>
      <BudgetBanner :budget-usage="activeGraph?.budgetUsage" :hybrid-scores="activeGraph?.vectorEvidence || activeGraph?.sourceEvidence" />
      <GraphEditor
        :graph="activeGraph"
        :entity-types="entityTypes"
        :relation-types="relationTypes"
        @save="saveGraphDraft"
        @action="applyGraphObjectAction"
      />
      <JsonBlock :value="draftResult" />
    </el-card>

    <el-card shadow="never" class="panel-card taxonomy-card">
      <template #header>
        <div class="panel-title"><Network :size="18" />分类体系</div>
      </template>
      <div class="taxonomy-grid">
        <div v-for="item in categories" :key="item.categoryId" class="taxonomy-item">
          <strong>{{ item.categoryName }}</strong>
          <span>{{ item.description }}</span>
        </div>
      </div>
    </el-card>
  </section>
</template>
