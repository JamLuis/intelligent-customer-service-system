<script setup lang="ts">
const props = defineProps<{
  budgetUsage?: Record<string, any> | null;
  hybridScores?: Record<string, any>[];
}>();

function percent(value: unknown) {
  const n = Number(value || 0);
  return Number.isFinite(n) ? Math.round(n * 100) : 0;
}
</script>

<template>
  <div v-if="props.budgetUsage || props.hybridScores?.length" class="budget-banner">
    <el-alert
      v-if="props.budgetUsage"
      :type="props.budgetUsage.truncated ? 'warning' : 'success'"
      :closable="false"
      show-icon
    >
      <template #title>
        <span>
          遍历预算 {{ props.budgetUsage.visitedNodes || 0 }} 节点 / {{ props.budgetUsage.visitedEdges || 0 }} 边
          <strong v-if="props.budgetUsage.truncated"> · 已截断</strong>
        </span>
      </template>
    </el-alert>

    <div v-if="props.hybridScores?.length" class="hybrid-score-grid">
      <div v-for="item in props.hybridScores" :key="item.blockId || item.id" class="hybrid-score-card">
        <span>{{ item.blockId || item.id }}</span>
        <strong>{{ percent(item.hybridScore) }}%</strong>
        <small>V {{ percent(item.vectorScore) }} · B {{ percent(item.bm25Score) }} · G {{ percent(item.graphBoostScore) }}</small>
      </div>
    </div>
  </div>
</template>