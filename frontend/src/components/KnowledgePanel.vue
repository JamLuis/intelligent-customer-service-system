<script setup lang="ts">
import { Database, RefreshCw, UploadCloud } from 'lucide-vue-next';
import JsonBlock from './JsonBlock.vue';

defineProps<{
  knowledgeSource: Record<string, unknown> | null;
  knowledgeTasks: Record<string, unknown> | null;
}>();

const sourceText = defineModel<string>('sourceText', { required: true });

const emit = defineEmits<{
  createKnowledge: [];
  retryKnowledge: [];
}>();
</script>

<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="panel-title"><UploadCloud :size="18" />知识录入</div>
    </template>
    <el-input v-model="sourceText" type="textarea" :rows="5" />
    <div class="toolbar">
      <el-button type="primary" :icon="Database" @click="emit('createKnowledge')">提交知识源</el-button>
      <el-button :icon="RefreshCw" :disabled="!knowledgeSource" @click="emit('retryKnowledge')">重跑任务</el-button>
    </div>
    <JsonBlock :value="knowledgeSource" />
    <JsonBlock :value="knowledgeTasks" />
  </el-card>
</template>
