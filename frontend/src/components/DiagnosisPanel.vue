<script setup lang="ts">
import { MessagesSquare, Send } from 'lucide-vue-next';
import JsonBlock from './JsonBlock.vue';

defineProps<{
  session: Record<string, unknown> | null;
}>();

const questionText = defineModel<string>('questionText', { required: true });
const followUpAnswer = defineModel<string>('followUpAnswer', { required: true });

const emit = defineEmits<{
  createSession: [];
  supplementContext: [];
  startDiagnosis: [];
}>();
</script>

<template>
  <el-card shadow="never" class="panel chat-panel">
    <template #header>
      <div class="panel-title"><MessagesSquare :size="18" />问答诊断</div>
    </template>
    <el-input v-model="questionText" type="textarea" :rows="4" />
    <div class="toolbar">
      <el-button type="primary" :icon="Send" @click="emit('createSession')">创建会话</el-button>
      <el-button :disabled="!session" @click="emit('supplementContext')">补充上下文</el-button>
      <el-button type="success" :disabled="!session" @click="emit('startDiagnosis')">发起诊断</el-button>
    </div>
    <el-input v-model="followUpAnswer" class="mt" />
    <JsonBlock :value="session" />
  </el-card>
</template>
