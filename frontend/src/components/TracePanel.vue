<script setup lang="ts">
import { GitBranch } from 'lucide-vue-next';
import JsonBlock from './JsonBlock.vue';

defineProps<{
  trace: Record<string, unknown> | null;
}>();
</script>

<template>
  <el-card shadow="never" class="panel wide">
    <template #header>
      <div class="panel-title"><GitBranch :size="18" />执行轨迹</div>
    </template>
    <div v-if="trace" class="trace-grid">
      <div>
        <h3>Graph Paths</h3>
        <JsonBlock :value="trace.graphPaths" />
      </div>
      <div>
        <h3>MCP Calls</h3>
        <JsonBlock :value="trace.mcpCalls" />
      </div>
      <div>
        <h3>Reasoning Steps</h3>
        <JsonBlock :value="trace.reasoningSteps" />
      </div>
    </div>
    <el-empty v-else description="暂无执行轨迹" />
  </el-card>
</template>
