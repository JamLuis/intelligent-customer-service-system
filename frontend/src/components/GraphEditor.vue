<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import { VueFlow, type Connection, type Edge, type Node } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';

interface GraphNodePayload {
  id?: string;
  label?: string;
  type?: string;
}

interface GraphEdgePayload {
  id?: string;
  source?: string;
  target?: string;
  type?: string;
  label?: string;
}

interface GraphPayload {
  graphId?: string;
  graphName?: string;
  nodes?: GraphNodePayload[];
  edges?: GraphEdgePayload[];
}

type FlowNode = Node;
type FlowEdge = Edge;

const props = defineProps<{
  graph: GraphPayload | null;
}>();

const emit = defineEmits<{
  save: [{ graphId: string; nodes: FlowNode[]; edges: FlowEdge[] }];
}>();

const nodes = shallowRef<FlowNode[]>([]);
const edges = shallowRef<FlowEdge[]>([]);
const selectedNodeId = ref('');
const selectedEdgeId = ref('');
const entityLabel = ref('');
const entityType = ref('system');
const relationSource = ref('');
const relationTarget = ref('');
const relationType = ref('RELATED_TO');

const graphId = computed(() => props.graph?.graphId || 'draft-graph');

const sourceOptions = computed<{ id: string; label: string }[]>(() => {
  return nodes.value.map((node) => ({ id: String(node.id), label: String(node.data?.label || node.id) }));
});

function normalizeNodes(graphNodes: GraphNodePayload[] = []): FlowNode[] {
  return graphNodes.map((item, index) => ({
    id: String(item.id || `entity:${index + 1}`),
    type: 'default',
    position: {
      x: 80 + (index % 4) * 230,
      y: 80 + Math.floor(index / 4) * 150
    },
    data: {
      label: item.label || item.id || `实体 ${index + 1}`,
      entityType: item.type || 'entity'
    }
  }));
}

function normalizeEdges(graphEdges: GraphEdgePayload[] = []): FlowEdge[] {
  return graphEdges
    .filter((item) => item.source && item.target)
    .map((item, index) => {
      const edgeType = item.type || item.label || 'RELATED_TO';
      return {
        id: String(item.id || `${item.source}-${edgeType}-${item.target}-${index}`),
        source: String(item.source),
        target: String(item.target),
        label: edgeType,
        type: 'default',
        animated: false,
        data: { relationType: edgeType }
      };
    });
}

function edgeId(source: string, target: string, type: string) {
  return `${source}-${type}-${target}-${Date.now()}`;
}

function resetSelection() {
  selectedNodeId.value = '';
  selectedEdgeId.value = '';
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target) {
    return;
  }
  const type = relationType.value || 'RELATED_TO';
  edges.value = [
    ...edges.value,
    {
      id: edgeId(connection.source, connection.target, type),
      source: connection.source,
      target: connection.target,
      label: type,
      type: 'default',
      data: { relationType: type }
    }
  ];
}

function onNodeClick(event: { node: FlowNode }) {
  selectedNodeId.value = event.node.id;
  selectedEdgeId.value = '';
}

function onEdgeClick(event: { edge: FlowEdge }) {
  selectedEdgeId.value = event.edge.id;
  selectedNodeId.value = '';
}

function addEntity() {
  const label = entityLabel.value.trim();
  if (!label) {
    return;
  }
  const id = `${entityType.value}:${label.replace(/\s+/g, '-').toLowerCase()}-${Date.now()}`;
  nodes.value = [
    ...nodes.value,
    {
      id,
      type: 'default',
      position: { x: 120 + nodes.value.length * 24, y: 120 + nodes.value.length * 16 },
      data: { label, entityType: entityType.value }
    }
  ];
  entityLabel.value = '';
}

function deleteSelectedEntity() {
  if (!selectedNodeId.value) {
    return;
  }
  const id = selectedNodeId.value;
  nodes.value = nodes.value.filter((node) => node.id !== id);
  edges.value = edges.value.filter((edge) => edge.source !== id && edge.target !== id);
  resetSelection();
}

function addRelation() {
  if (!relationSource.value || !relationTarget.value || relationSource.value === relationTarget.value) {
    return;
  }
  const type = relationType.value.trim() || 'RELATED_TO';
  edges.value = [
    ...edges.value,
    {
      id: edgeId(relationSource.value, relationTarget.value, type),
      source: relationSource.value,
      target: relationTarget.value,
      label: type,
      type: 'default',
      data: { relationType: type }
    }
  ];
}

function deleteSelectedRelation() {
  if (!selectedEdgeId.value) {
    return;
  }
  edges.value = edges.value.filter((edge) => edge.id !== selectedEdgeId.value);
  resetSelection();
}

function saveDraft() {
  emit('save', { graphId: graphId.value, nodes: nodes.value, edges: edges.value });
}

watch(
  () => props.graph,
  (graph) => {
    nodes.value = normalizeNodes(graph?.nodes || []);
    edges.value = normalizeEdges(graph?.edges || []);
    relationSource.value = nodes.value[0]?.id || '';
    relationTarget.value = nodes.value[1]?.id || '';
    resetSelection();
  },
  { immediate: true }
);
</script>

<template>
  <div class="graph-editor">
    <div class="graph-toolbar">
      <div class="graph-meta">
        <strong>{{ graph?.graphName || '关系子图' }}</strong>
        <span>{{ nodes.length }} 个实体 / {{ edges.length }} 条关系</span>
      </div>
      <div class="graph-actions">
        <el-button @click="deleteSelectedEntity" :disabled="!selectedNodeId">删除实体</el-button>
        <el-button @click="deleteSelectedRelation" :disabled="!selectedEdgeId">删除关系</el-button>
        <el-button type="primary" @click="saveDraft">保存草稿</el-button>
      </div>
    </div>

    <div class="graph-workbench">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        class="graph-flow"
        fit-view-on-init
        @connect="onConnect"
        @node-click="onNodeClick"
        @edge-click="onEdgeClick"
      />
      <aside class="graph-side-panel">
        <div class="side-section">
          <strong>新增实体</strong>
          <el-input v-model="entityLabel" placeholder="实体名称，如 设备 TC-003" />
          <el-select v-model="entityType" placeholder="实体类型">
            <el-option label="设备" value="device" />
            <el-option label="告警规则" value="alarmRule" />
            <el-option label="船舶" value="vessel" />
            <el-option label="配置" value="config" />
            <el-option label="系统" value="system" />
          </el-select>
          <el-button type="primary" plain @click="addEntity">添加实体</el-button>
        </div>

        <div class="side-section">
          <strong>重建关系</strong>
          <el-select v-model="relationSource" filterable placeholder="起点实体">
            <el-option v-for="item in sourceOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
          <el-input v-model="relationType" placeholder="关系类型，如 BOUND_TO" />
          <el-select v-model="relationTarget" filterable placeholder="终点实体">
            <el-option v-for="item in sourceOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
          <el-button type="primary" plain @click="addRelation">建立关系</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>
