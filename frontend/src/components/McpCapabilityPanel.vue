<script setup lang="ts">
import { PlugZap } from 'lucide-vue-next';
import JsonBlock from './JsonBlock.vue';

defineProps<{
  capabilities: Record<string, any> | null;
  capabilityImpact: Record<string, unknown> | null;
}>();

const emit = defineEmits<{
  loadCapabilities: [];
  disableFirstCapability: [];
}>();
</script>

<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="panel-title"><PlugZap :size="18" />MCP 能力</div>
    </template>
    <div class="toolbar">
      <el-button type="primary" @click="emit('loadCapabilities')">加载能力</el-button>
      <el-button :disabled="!capabilities" @click="emit('disableFirstCapability')">停用首个能力</el-button>
    </div>
    <el-table v-if="capabilities" :data="capabilities.items" height="220">
      <el-table-column prop="capabilityCode" label="能力" min-width="150" />
      <el-table-column prop="riskLevel" label="风险" width="80" />
      <el-table-column prop="status" label="状态" width="110" />
    </el-table>
    <JsonBlock :value="capabilityImpact" />
  </el-card>
</template>
