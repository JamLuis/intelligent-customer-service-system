<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, watch } from 'vue';
import { Position, VueFlow, useVueFlow, type Connection, type Edge, type Node } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';

interface GraphNodePayload {
  id?: string;
  entityId?: string;
  label?: string;
  type?: string;
  entityType?: string;
  status?: string;
  evidenceRefs?: EvidenceRef[];
  data?: {
    label?: string;
    rawLabel?: string;
    entityType?: string;
    status?: string;
    evidenceRefs?: EvidenceRef[];
  };
}

interface GraphEdgePayload {
  id?: string;
  relationId?: string;
  source?: string;
  target?: string;
  type?: string;
  label?: string;
  relationType?: string;
  status?: string;
  evidenceRefs?: EvidenceRef[];
  data?: {
    relationId?: string;
    relationType?: string;
    status?: string;
    evidenceRefs?: EvidenceRef[];
  };
}

interface EvidenceRef {
  blockId?: string;
  weight?: number;
  sourceType?: string;
}

interface GraphPayload {
  graphId?: string;
  graphName?: string;
  graphCategoryName?: string;
  nodes?: GraphNodePayload[];
  edges?: GraphEdgePayload[];
}

interface GraphTypeOption {
  type?: string;
  entityType?: string;
  relationType?: string;
  label?: string;
}

type FlowNode = Node;
type FlowEdge = Edge;
type FlowNodeStyle = NonNullable<FlowNode['style']>;
type LayoutMode = 'tree' | 'layered' | 'grid';
type EdgeMode = 'structure' | 'all' | 'minimal';

const NODE_WIDTH = 190;
const NODE_HEIGHT = 82;
const COLUMN_GAP = 280;
const ROW_GAP = 124;
const LAYOUT_LEFT = 80;
const LAYOUT_TOP = 80;
const TREE_COLUMN_GAP = 230;
const TREE_ROW_GAP = 132;

const props = defineProps<{
  graph: GraphPayload | null;
  entityTypes?: GraphTypeOption[];
  relationTypes?: GraphTypeOption[];
}>();

const emit = defineEmits<{
  save: [{ graphId: string; nodes: FlowNode[]; edges: FlowEdge[] }];
  action: [{ objectType: 'entity' | 'relation'; objectId: string; action: 'freeze' | 'unfreeze' }];
}>();

const nodes = shallowRef<FlowNode[]>([]);
const edges = shallowRef<FlowEdge[]>([]);
const { fitView } = useVueFlow();
const selectedNodeId = ref('');
const selectedEdgeId = ref('');
const entityLabel = ref('');
const entityType = ref('system');
const relationSource = ref('');
const relationTarget = ref('');
const relationType = ref('RELATED_TO');
const editRelationSource = ref('');
const editRelationTarget = ref('');
const editRelationType = ref('');
const layoutMode = ref<LayoutMode>('tree');
const edgeMode = ref<EdgeMode>('structure');

const graphId = computed(() => props.graph?.graphId || 'draft-graph');
// 类型选项必须来自父组件 props（最终来自 GET /api/v1/graphs/assets/categories 返回的本租户 taxonomy）。
// 严禁在前端硬编码任何业务实体类型 / 关系类型；缺失时只回退到空列表，由 UI 提示用户先注册 taxonomy。
const entityTypeOptions = computed(() => props.entityTypes ?? []);
const relationTypeOptions = computed(() => props.relationTypes ?? []);
const graphHasDocumentStructure = computed(() => {
  const types = new Set(nodes.value.map((node) => String(node.data?.entityType || '')));
  return types.has('Document') && (types.has('Section') || types.has('SourceBlock'));
});
const hasNonEvidenceEdges = computed(() => edges.value.some((edge) => String(edge.data?.relationType || edge.label || '') !== 'HAS_EVIDENCE'));

function optionValue(item: GraphTypeOption) {
  return item.type || item.entityType || item.relationType || '';
}

function readableNodeLabel(label: string, entityTypeValue: string) {
  const normalized = label.replace(/\s+/g, ' ').trim();
  const maxLength = entityTypeValue === 'SourceBlock' ? 86 : 56;
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized;
}

function nodeStyle(entityTypeValue: string): FlowNodeStyle {
  const palette: Record<string, FlowNodeStyle> = {
    Document: { background: '#f7fbff', border: '1px solid #4f8fb8', width: `${NODE_WIDTH + 28}px`, minHeight: '74px', fontWeight: 700 },
    Section: { background: '#fffaf1', border: '1px solid #d39b2d', width: `${NODE_WIDTH + 10}px`, minHeight: '70px', fontWeight: 650 },
    SourceBlock: { background: '#ffffff', border: '1px solid #9bb8c4', width: `${NODE_WIDTH}px`, minHeight: `${NODE_HEIGHT}px` }
  };
  return {
    borderRadius: '8px',
    color: '#17202a',
    fontSize: '12px',
    lineHeight: '1.35',
    padding: '10px 12px',
    whiteSpace: 'normal',
    textAlign: 'center',
    ...palette[entityTypeValue]
  };
}

const sourceOptions = computed<{ id: string; label: string }[]>(() => {
  return nodes.value.map((node) => ({ id: String(node.id), label: String(node.data?.label || node.id) }));
});

function normalizeNodes(graphNodes: GraphNodePayload[] = []): FlowNode[] {
  return graphNodes.map((item, index) => {
    const entityTypeValue = String(item.entityType || item.type || item.data?.entityType || 'entity');
    const label = String(item.label || item.data?.rawLabel || item.data?.label || item.id || `实体 ${index + 1}`);
    return {
      id: String(item.entityId || item.id || `entity:${index + 1}`),
      type: 'default',
      position: {
        x: 80 + (index % 4) * 230,
        y: 80 + Math.floor(index / 4) * 150
      },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      data: {
        label: readableNodeLabel(label, entityTypeValue),
        rawLabel: label,
        entityType: entityTypeValue,
        status: item.status || item.data?.status || 'published',
        evidenceRefs: item.evidenceRefs || item.data?.evidenceRefs || []
      },
      style: nodeStyle(entityTypeValue)
    };
  });
}

function normalizeEdges(graphEdges: GraphEdgePayload[] = []): FlowEdge[] {
  return graphEdges
    .filter((item) => item.source && item.target)
    .map((item, index) => {
      const edgeType = item.relationType || item.type || item.label || item.data?.relationType || 'RELATED_TO';
      return {
        id: String(item.relationId || item.data?.relationId || item.id || `${item.source}-${edgeType}-${item.target}-${index}`),
        source: String(item.source),
        target: String(item.target),
        label: edgeType,
        type: 'straight',
        animated: false,
        data: { relationId: item.relationId || item.data?.relationId || item.id, relationType: edgeType, status: item.status || item.data?.status || 'published', evidenceRefs: item.evidenceRefs || item.data?.evidenceRefs || [] }
      };
    });
}

const flowEdges = computed<FlowEdge[]>(() => {
  return edges.value
    .filter((edge) => !shouldHideEdge(edge))
    .map((edge) => ({
      ...edge,
      type: 'straight',
      label: edgeMode.value === 'all' ? edge.label : '',
      style: edgeStyle(edge),
      labelStyle: { fontSize: 10, fill: '#42515a' },
      labelBgStyle: { fill: '#ffffff', fillOpacity: 0.88 }
    }));
});

const hiddenEdgeCount = computed(() => Math.max(0, edges.value.length - flowEdges.value.length));

function edgeStyle(edge: FlowEdge) {
  const relation = String(edge.data?.relationType || edge.label || '');
  if (relation === 'HAS_SECTION') {
    return { stroke: '#d39b2d', strokeWidth: 1.7 };
  }
  if (relation === 'IN_SECTION') {
    return { stroke: '#6f9eb1', strokeWidth: 1.25 };
  }
  if (relation === 'HAS_EVIDENCE') {
    return { stroke: '#b8c5ca', strokeWidth: 0.9 };
  }
  return { stroke: '#8aa2ad', strokeWidth: 1.15 };
}

function shouldHideEdge(edge: FlowEdge) {
  const relation = String(edge.data?.relationType || edge.label || '');
  if (edgeMode.value === 'all') {
    return false;
  }
  if (edgeMode.value === 'minimal') {
    return relation === 'HAS_EVIDENCE';
  }
  return graphHasDocumentStructure.value && hasNonEvidenceEdges.value && relation === 'HAS_EVIDENCE';
}

const selectedNode = computed(() => nodes.value.find((node) => node.id === selectedNodeId.value) || null);
const selectedEdge = computed(() => edges.value.find((edge) => edge.id === selectedEdgeId.value) || null);
const selectedObjectStatus = computed(() => String(selectedNode.value?.data?.status || selectedEdge.value?.data?.status || ''));
const selectedObjectEvidence = computed<EvidenceRef[]>(() => {
  const evidence = selectedNode.value?.data?.evidenceRefs || selectedEdge.value?.data?.evidenceRefs || [];
  return Array.isArray(evidence) ? evidence : [];
});

function edgeId(source: string, target: string, type: string) {
  return `${source}-${type}-${target}-${Date.now()}`;
}

function applyLayout(mode = layoutMode.value) {
  if (mode === 'tree') {
    nodes.value = layoutTopDownTree(nodes.value, edges.value);
    return;
  }
  if (mode === 'grid') {
    nodes.value = layoutGrid(nodes.value);
    return;
  }
  nodes.value = layoutLayered(nodes.value, edges.value);
}

function fitGraphView() {
  nextTick(() => {
    fitView({ padding: 0.18, duration: 180 });
  });
}

function layoutTopDownTree(currentNodes: FlowNode[], currentEdges: FlowEdge[]) {
  const ids = new Set(currentNodes.map((node) => String(node.id)));
  const nodeById = new Map(currentNodes.map((node) => [String(node.id), node]));
  const evidenceIds = new Set(
    currentNodes
      .filter((node) => ['Document', 'Section', 'SourceBlock'].includes(String(node.data?.entityType || '')))
      .map((node) => String(node.id))
  );
  const semanticIds = new Set(currentNodes.map((node) => String(node.id)).filter((id) => !evidenceIds.has(id)));
  const treeIds = semanticIds.size ? semanticIds : ids;
  const incoming = new Map<string, number>();
  const childrenById = new Map<string, string[]>();
  currentNodes.forEach((node) => {
    incoming.set(String(node.id), 0);
    childrenById.set(String(node.id), []);
  });

  currentEdges.forEach((edge) => {
    const source = String(edge.source);
    const target = String(edge.target);
    if (!treeIds.has(source) || !treeIds.has(target) || shouldHideEdge(edge)) {
      return;
    }
    incoming.set(target, (incoming.get(target) || 0) + 1);
    childrenById.set(source, [...(childrenById.get(source) || []), target]);
  });

  const roots = currentNodes
    .filter((node) => treeIds.has(String(node.id)) && (incoming.get(String(node.id)) || 0) === 0)
    .sort(compareReadableNodes)
    .map((node) => String(node.id));
  const orderedRoots = roots.length ? roots : currentNodes.filter((node) => treeIds.has(String(node.id))).slice(0, 1).map((node) => String(node.id));
  const nextPositions = new Map<string, { x: number; y: number }>();
  const placed = new Set<string>();
  placeEvidenceColumn(currentNodes, evidenceIds, nextPositions);
  let leafCursor = 0;

  function placeNode(nodeId: string, depth: number, visiting = new Set<string>()): number {
    const existing = nextPositions.get(nodeId);
    if (existing) {
      return existing.x;
    }
    if (visiting.has(nodeId)) {
      const cycleX = LAYOUT_LEFT + leafCursor * TREE_COLUMN_GAP;
      leafCursor += 1;
      nextPositions.set(nodeId, { x: cycleX, y: LAYOUT_TOP + depth * TREE_ROW_GAP });
      placed.add(nodeId);
      return cycleX;
    }

    visiting.add(nodeId);
    const children = [...(childrenById.get(nodeId) || [])]
      .filter((childId) => treeIds.has(childId))
      .filter((childId, index, array) => array.indexOf(childId) === index)
      .sort((first, second) => compareReadableNodes(nodeById.get(first), nodeById.get(second)));

    let x: number;
    if (!children.length) {
      x = semanticTreeLeft(evidenceIds) + leafCursor * TREE_COLUMN_GAP;
      leafCursor += 1;
    } else {
      const childXs = children.map((childId) => placeNode(childId, depth + 1, new Set(visiting)));
      x = childXs.reduce((sum, item) => sum + item, 0) / childXs.length;
    }

    nextPositions.set(nodeId, { x, y: LAYOUT_TOP + depth * TREE_ROW_GAP });
    placed.add(nodeId);
    return x;
  }

  orderedRoots.forEach((rootId, index) => {
    if (index > 0 && leafCursor > 0) {
      leafCursor += 1;
    }
    placeNode(rootId, 0);
  });
  currentNodes.sort(compareReadableNodes).forEach((node) => {
    const nodeId = String(node.id);
    if (treeIds.has(nodeId) && !placed.has(nodeId)) {
      if (leafCursor > 0) {
        leafCursor += 1;
      }
      placeNode(nodeId, 0);
    }
  });

  return currentNodes.map((node) => ({
    ...node,
    position: nextPositions.get(String(node.id)) || node.position,
    sourcePosition: Position.Bottom,
    targetPosition: Position.Top,
    style: nodeStyle(String(node.data?.entityType || 'entity'))
  }));
}

function placeEvidenceColumn(currentNodes: FlowNode[], evidenceIds: Set<string>, nextPositions: Map<string, { x: number; y: number }>) {
  if (!evidenceIds.size) {
    return;
  }
  currentNodes
    .filter((node) => evidenceIds.has(String(node.id)))
    .sort(compareReadableNodes)
    .forEach((node, index) => {
      nextPositions.set(String(node.id), { x: LAYOUT_LEFT, y: LAYOUT_TOP + index * TREE_ROW_GAP });
    });
}

function semanticTreeLeft(evidenceIds: Set<string>) {
  return evidenceIds.size ? LAYOUT_LEFT + TREE_COLUMN_GAP + 80 : LAYOUT_LEFT;
}

function layoutDocumentTree(currentNodes: FlowNode[], currentEdges: FlowEdge[]) {
  const nodeById = new Map(currentNodes.map((node) => [String(node.id), node]));
  const documents = currentNodes.filter((node) => node.data?.entityType === 'Document');
  const sections = currentNodes.filter((node) => node.data?.entityType === 'Section');
  const blocks = currentNodes.filter((node) => node.data?.entityType === 'SourceBlock');
  const genericNodes = currentNodes.filter((node) => String(node.data?.entityType || '').startsWith('Extracted'));
  const sectionIds = new Set(sections.map((node) => String(node.id)));
  const sectionToBlocks = new Map<string, FlowNode[]>();

  for (const edge of currentEdges) {
    const relation = String(edge.data?.relationType || edge.label || '');
    if (relation !== 'IN_SECTION') {
      continue;
    }
    const source = nodeById.get(String(edge.source));
    const target = nodeById.get(String(edge.target));
    if (!source || !target) {
      continue;
    }
    const sectionId = sectionIds.has(String(edge.target)) ? String(edge.target) : String(edge.source);
    const block = source.data?.entityType === 'SourceBlock' ? source : target.data?.entityType === 'SourceBlock' ? target : null;
    if (block) {
      sectionToBlocks.set(sectionId, [...(sectionToBlocks.get(sectionId) || []), block]);
    }
  }

  const assignedBlocks = new Set(Array.from(sectionToBlocks.values()).flat().map((node) => String(node.id)));
  const looseBlocks = blocks.filter((node) => !assignedBlocks.has(String(node.id)));
  const nextPositions = new Map<string, { x: number; y: number }>();
  documents.forEach((node, index) => nextPositions.set(String(node.id), { x: 80, y: 80 + index * ROW_GAP }));
  sections.forEach((node, index) => nextPositions.set(String(node.id), { x: 80 + COLUMN_GAP, y: 80 + index * ROW_GAP }));

  let blockRow = 0;
  const blockColumn = sections.length ? 2 : 1;
  for (const section of sections) {
    const relatedBlocks = sectionToBlocks.get(String(section.id)) || [];
    relatedBlocks.forEach((node) => {
      nextPositions.set(String(node.id), { x: 80 + COLUMN_GAP * blockColumn, y: 80 + blockRow * (ROW_GAP - 10) });
      blockRow += 1;
    });
  }
  looseBlocks.forEach((node) => {
    nextPositions.set(String(node.id), { x: 80 + COLUMN_GAP * blockColumn, y: 80 + blockRow * (ROW_GAP - 10) });
    blockRow += 1;
  });

  if (genericNodes.length) {
    placeGenericFactNodes(genericNodes, currentEdges, nextPositions, sections.length ? 3 : 2);
  }

  return currentNodes.map((node) => ({
    ...node,
    position: nextPositions.get(String(node.id)) || node.position,
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    style: nodeStyle(String(node.data?.entityType || 'entity'))
  }));
}

function placeGenericFactNodes(genericNodes: FlowNode[], currentEdges: FlowEdge[], nextPositions: Map<string, { x: number; y: number }>, startColumn: number) {
  const genericIds = new Set(genericNodes.map((node) => String(node.id)));
  const componentTargets = new Set<string>();
  const attachedTargets = new Set<string>();
  currentEdges.forEach((edge) => {
    const relation = String(edge.data?.relationType || edge.label || '');
    if (relation === 'HAS_COMPONENT' && genericIds.has(String(edge.target))) {
      componentTargets.add(String(edge.target));
    }
    if ((relation === 'HAS_IDENTIFIER' || relation === 'HAS_QUANTITY') && genericIds.has(String(edge.target))) {
      attachedTargets.add(String(edge.target));
    }
  });

  const primaryObjects = genericNodes.filter((node) => node.data?.entityType === 'ExtractedObject' && !componentTargets.has(String(node.id)));
  const componentObjects = genericNodes.filter((node) => node.data?.entityType === 'ExtractedObject' && componentTargets.has(String(node.id)));
  const quantities = genericNodes.filter((node) => node.data?.entityType === 'ExtractedQuantity');
  const identifiers = genericNodes.filter((node) => node.data?.entityType === 'ExtractedIdentifier');
  const otherGeneric = genericNodes.filter((node) => !primaryObjects.includes(node) && !componentObjects.includes(node) && !quantities.includes(node) && !identifiers.includes(node) && !attachedTargets.has(String(node.id)));

  placeColumn(primaryObjects, startColumn, 80, nextPositions);
  placeColumn([...componentObjects, ...otherGeneric], startColumn + 1, 80, nextPositions);
  placeColumn([...quantities, ...identifiers], startColumn + 2, 80, nextPositions);
}

function placeColumn(items: FlowNode[], column: number, top: number, nextPositions: Map<string, { x: number; y: number }>) {
  items.forEach((node, index) => {
    nextPositions.set(String(node.id), { x: 80 + COLUMN_GAP * column, y: top + index * ROW_GAP });
  });
}

function layoutLayered(currentNodes: FlowNode[], currentEdges: FlowEdge[]) {
  const ids = new Set(currentNodes.map((node) => String(node.id)));
  const incoming = new Map<string, number>();
  const outgoing = new Map<string, string[]>();
  const nodeById = new Map(currentNodes.map((node) => [String(node.id), node]));
  currentNodes.forEach((node) => {
    incoming.set(String(node.id), 0);
    outgoing.set(String(node.id), []);
  });
  currentEdges.forEach((edge) => {
    if (!ids.has(String(edge.source)) || !ids.has(String(edge.target)) || shouldHideEdge(edge)) {
      return;
    }
    incoming.set(String(edge.target), (incoming.get(String(edge.target)) || 0) + 1);
    outgoing.set(String(edge.source), [...(outgoing.get(String(edge.source)) || []), String(edge.target)]);
  });

  const level = new Map<string, number>();
  const queue = currentNodes
    .filter((node) => (incoming.get(String(node.id)) || 0) === 0)
    .sort(compareReadableNodes)
    .map((node) => String(node.id));
  if (!queue.length && currentNodes.length) {
    queue.push(String(currentNodes[0].id));
  }
  queue.forEach((id) => level.set(id, 0));
  for (let index = 0; index < queue.length; index += 1) {
    const id = queue[index];
    const nextLevel = (level.get(id) || 0) + 1;
    for (const target of [...(outgoing.get(id) || [])].sort((a, b) => compareReadableNodes(nodeById.get(a), nodeById.get(b)))) {
      if ((level.get(target) ?? -1) < nextLevel) {
        level.set(target, nextLevel);
        queue.push(target);
      }
    }
  }

  currentNodes.forEach((node) => {
    if (!level.has(String(node.id))) {
      level.set(String(node.id), 0);
    }
  });
  const nextPositions = new Map<string, { x: number; y: number }>();
  const placed = new Set<string>();
  const roots = currentNodes
    .filter((node) => (incoming.get(String(node.id)) || 0) === 0)
    .sort(compareReadableNodes)
    .map((node) => String(node.id));
  const orderedRoots = roots.length ? roots : [String(currentNodes[0]?.id || '')].filter(Boolean);
  let rowCursor = 0;

  function placeSubtree(nodeId: string, visiting = new Set<string>()): number {
    if (placed.has(nodeId)) {
      return nextPositions.get(nodeId)?.y ?? LAYOUT_TOP + rowCursor * ROW_GAP;
    }
    if (visiting.has(nodeId)) {
      const cycleY = LAYOUT_TOP + rowCursor * ROW_GAP;
      rowCursor += 1;
      nextPositions.set(nodeId, { x: LAYOUT_LEFT + (level.get(nodeId) || 0) * COLUMN_GAP, y: cycleY });
      placed.add(nodeId);
      return cycleY;
    }
    visiting.add(nodeId);
    const children = [...(outgoing.get(nodeId) || [])]
      .filter((childId) => ids.has(childId))
      .sort((a, b) => compareReadableNodes(nodeById.get(a), nodeById.get(b)));
    let y: number;
    if (!children.length) {
      y = LAYOUT_TOP + rowCursor * ROW_GAP;
      rowCursor += 1;
    } else {
      const childYs = children.map((childId) => placeSubtree(childId, new Set(visiting)));
      y = childYs.reduce((sum, item) => sum + item, 0) / childYs.length;
    }
    nextPositions.set(nodeId, { x: LAYOUT_LEFT + (level.get(nodeId) || 0) * COLUMN_GAP, y });
    placed.add(nodeId);
    return y;
  }

  orderedRoots.forEach((rootId) => placeSubtree(rootId));
  currentNodes.sort(compareReadableNodes).forEach((node) => {
    const nodeId = String(node.id);
    if (!placed.has(nodeId)) {
      placeSubtree(nodeId);
    }
  });

  return currentNodes.map((node) => ({
    ...node,
    position: nextPositions.get(String(node.id)) || node.position,
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    style: nodeStyle(String(node.data?.entityType || 'entity'))
  }));
}

function compareReadableNodes(first?: FlowNode, second?: FlowNode) {
  const firstRank = readableNodeRank(first);
  const secondRank = readableNodeRank(second);
  if (firstRank !== secondRank) {
    return firstRank - secondRank;
  }
  return readableSortText(first).localeCompare(readableSortText(second), 'zh-Hans-CN', { numeric: true });
}

function readableNodeRank(node?: FlowNode) {
  const entityTypeValue = String(node?.data?.entityType || '');
  const label = String(node?.data?.rawLabel || node?.data?.label || '');
  if (entityTypeValue === 'Document') return 0;
  if (entityTypeValue === 'Section') return 1;
  if (entityTypeValue === 'SourceBlock') return 2;
  if (entityTypeValue === 'ExtractedQuantity') return 3;
  if (/大|一|1/.test(label)) return 4;
  if (/二|2/.test(label)) return 5;
  if (/三|3/.test(label)) return 6;
  if (/小|末/.test(label)) return 7;
  if (entityTypeValue === 'ExtractedObject') return 8;
  if (entityTypeValue === 'ExtractedIdentifier') return 9;
  return 10;
}

function readableSortText(node?: FlowNode) {
  return String(node?.data?.rawLabel || node?.data?.label || node?.id || '');
}

function layoutGrid(currentNodes: FlowNode[]) {
  const columnCount = currentNodes.length > 24 ? 5 : 4;
  return currentNodes.map((node, index) => ({
    ...node,
    position: { x: 80 + (index % columnCount) * 240, y: 80 + Math.floor(index / columnCount) * ROW_GAP },
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    style: nodeStyle(String(node.data?.entityType || 'entity'))
  }));
}

function cleanEdgesForSave() {
  return edges.value.map((edge) => ({
    ...edge,
    relationId: edge.data?.relationId || edge.id,
    relationType: String(edge.data?.relationType || edge.label || 'RELATED_TO'),
    status: edge.data?.status,
    evidenceRefs: edge.data?.evidenceRefs || [],
    hidden: undefined,
    style: undefined,
    labelStyle: undefined,
    labelBgStyle: undefined
  }));
}

function cleanNodesForSave() {
  return nodes.value.map((node) => ({
    ...node,
    entityId: node.id,
    entityType: String(node.data?.entityType || 'entity'),
    status: node.data?.status,
    evidenceRefs: node.data?.evidenceRefs || [],
    style: undefined,
    data: {
      ...node.data,
      label: node.data?.rawLabel || node.data?.label
    }
  }));
}

function resetSelection() {
  selectedNodeId.value = '';
  selectedEdgeId.value = '';
  editRelationSource.value = '';
  editRelationTarget.value = '';
  editRelationType.value = '';
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
      type: 'straight',
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
  editRelationSource.value = String(event.edge.source || '');
  editRelationTarget.value = String(event.edge.target || '');
  editRelationType.value = String(event.edge.data?.relationType || event.edge.label || 'RELATED_TO');
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
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      data: { label, rawLabel: label, entityType: entityType.value },
      style: nodeStyle(entityType.value)
    }
  ];
  entityLabel.value = '';
}

function deleteSelectedEntity() {
  if (!selectedNodeId.value || selectedObjectStatus.value === 'frozen') {
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
      type: 'straight',
      data: { relationType: type }
    }
  ];
}

function deleteSelectedRelation() {
  if (!selectedEdgeId.value || selectedObjectStatus.value === 'frozen') {
    return;
  }
  edges.value = edges.value.filter((edge) => edge.id !== selectedEdgeId.value);
  resetSelection();
}

function updateSelectedRelation() {
  if (!selectedEdgeId.value || selectedObjectStatus.value === 'frozen') {
    return;
  }
  if (!editRelationSource.value || !editRelationTarget.value || editRelationSource.value === editRelationTarget.value) {
    return;
  }
  const type = editRelationType.value.trim() || 'RELATED_TO';
  edges.value = edges.value.map((edge) => {
    if (edge.id !== selectedEdgeId.value) {
      return edge;
    }
    return {
      ...edge,
      source: editRelationSource.value,
      target: editRelationTarget.value,
      label: type,
      type: 'straight',
      data: {
        ...edge.data,
        relationId: edge.data?.relationId || edge.id,
        relationType: type,
        status: edge.data?.status || 'draft'
      }
    };
  });
}

function saveDraft() {
  emit('save', { graphId: graphId.value, nodes: cleanNodesForSave(), edges: cleanEdgesForSave() });
}

function applySelectedAction(action: 'freeze' | 'unfreeze') {
  if (selectedNode.value) {
    emit('action', { objectType: 'entity', objectId: String(selectedNode.value.id), action });
  } else if (selectedEdge.value) {
    emit('action', { objectType: 'relation', objectId: String(selectedEdge.value.id), action });
  }
}

watch(
  () => props.graph,
  (graph) => {
    nodes.value = normalizeNodes(graph?.nodes || []);
    edges.value = normalizeEdges(graph?.edges || []);
    layoutMode.value = 'tree';
    edgeMode.value = graphHasDocumentStructure.value ? 'structure' : 'all';
    applyLayout(layoutMode.value);
    fitGraphView();
    relationSource.value = nodes.value[0]?.id || '';
    relationTarget.value = nodes.value[1]?.id || '';
    resetSelection();
  },
  { immediate: true }
);

watch([layoutMode, edgeMode], () => {
  applyLayout(layoutMode.value);
  fitGraphView();
});
</script>

<template>
  <div class="graph-editor">
    <div class="graph-toolbar">
      <div class="graph-meta">
        <strong>{{ graph?.graphName || '关系子图' }}</strong>
        <span>{{ graph?.graphCategoryName || '未分类图谱' }} · {{ nodes.length }} 个实体 / {{ edges.length }} 条关系</span>
        <small v-if="hiddenEdgeCount">已简化 {{ hiddenEdgeCount }} 条高密度关系</small>
      </div>
      <div class="graph-actions">
        <el-radio-group v-model="layoutMode" size="small" class="graph-mode-control">
          <el-radio-button value="tree">树形</el-radio-button>
          <el-radio-button value="layered">分层</el-radio-button>
          <el-radio-button value="grid">网格</el-radio-button>
        </el-radio-group>
        <el-select v-model="edgeMode" size="small" class="edge-mode-select" placeholder="关系密度">
          <el-option label="结构关系" value="structure" />
          <el-option label="全部关系" value="all" />
          <el-option label="少标签" value="minimal" />
        </el-select>
        <el-button @click="applySelectedAction('freeze')" :disabled="!(selectedNode || selectedEdge) || selectedObjectStatus === 'frozen'">冻结</el-button>
        <el-button @click="applySelectedAction('unfreeze')" :disabled="!(selectedNode || selectedEdge) || selectedObjectStatus !== 'frozen'">解冻</el-button>
        <el-button @click="deleteSelectedEntity" :disabled="!selectedNodeId || selectedObjectStatus === 'frozen'">删除实体</el-button>
        <el-button @click="deleteSelectedRelation" :disabled="!selectedEdgeId || selectedObjectStatus === 'frozen'">删除关系</el-button>
        <el-button type="primary" @click="saveDraft">保存草稿</el-button>
      </div>
    </div>

    <div class="graph-workbench">
      <VueFlow
        v-model:nodes="nodes"
        :edges="flowEdges"
        class="graph-flow"
        fit-view-on-init
        @connect="onConnect"
        @node-click="onNodeClick"
        @edge-click="onEdgeClick"
      />
      <aside class="graph-side-panel">
        <div class="side-section">
          <strong>新增实体</strong>
          <el-input v-model="entityLabel" placeholder="实体名称" />
          <el-select v-model="entityType" placeholder="实体类型">
            <el-option v-for="item in entityTypeOptions" :key="optionValue(item)" :label="item.label || optionValue(item)" :value="optionValue(item)" />
          </el-select>
          <el-button type="primary" plain @click="addEntity">添加实体</el-button>
        </div>

        <div class="side-section">
          <strong>重建关系</strong>
          <el-select v-model="relationSource" filterable placeholder="起点实体">
            <el-option v-for="item in sourceOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
          <el-select v-model="relationType" filterable allow-create placeholder="关系类型">
            <el-option v-for="item in relationTypeOptions" :key="optionValue(item)" :label="item.label || optionValue(item)" :value="optionValue(item)" />
          </el-select>
          <el-select v-model="relationTarget" filterable placeholder="终点实体">
            <el-option v-for="item in sourceOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
          <el-button type="primary" plain @click="addRelation">建立关系</el-button>
        </div>

        <div v-if="selectedNode || selectedEdge" class="side-section">
          <strong>选中对象</strong>
          <el-tag :type="selectedObjectStatus === 'frozen' ? 'warning' : 'success'" effect="plain">
            {{ selectedObjectStatus === 'frozen' ? 'Frozen' : selectedObjectStatus || 'published' }}
          </el-tag>
          <template v-if="selectedEdge">
            <el-alert title="删除或更新关系后需要保存草稿；如需让模型重新判断，可回到录入区点击重新生成关系。" type="info" :closable="false" />
            <el-select v-model="editRelationSource" filterable placeholder="起点实体" :disabled="selectedObjectStatus === 'frozen'">
              <el-option v-for="item in sourceOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
            <el-select v-model="editRelationType" filterable allow-create placeholder="关系类型" :disabled="selectedObjectStatus === 'frozen'">
              <el-option v-for="item in relationTypeOptions" :key="optionValue(item)" :label="item.label || optionValue(item)" :value="optionValue(item)" />
            </el-select>
            <el-select v-model="editRelationTarget" filterable placeholder="终点实体" :disabled="selectedObjectStatus === 'frozen'">
              <el-option v-for="item in sourceOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
            <el-button type="primary" plain :disabled="selectedObjectStatus === 'frozen'" @click="updateSelectedRelation">更新关系</el-button>
          </template>
          <div v-if="selectedObjectEvidence.length" class="evidence-tag-list">
            <el-tag v-for="item in selectedObjectEvidence" :key="`${item.blockId}-${item.sourceType}`" size="small" effect="plain">
              {{ item.sourceType || 'paragraph' }} · {{ Math.round(Number(item.weight || 0) * 100) }}%
            </el-tag>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>
