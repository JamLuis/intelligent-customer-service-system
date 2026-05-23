<script setup lang="ts">
import { Route } from 'lucide-vue-next';
import JsonBlock from './JsonBlock.vue';

defineProps<{
  canSubmit: boolean;
  feedbackResult: Record<string, unknown> | null;
  routeResult: Record<string, unknown> | null;
  graphs: Record<string, unknown> | null;
}>();

const feedbackRating = defineModel<'valid' | 'invalid' | 'partial'>('feedbackRating', { required: true });

const emit = defineEmits<{
  submitRouteFeedback: [];
}>();
</script>

<template>
  <el-card shadow="never" class="panel wide">
    <template #header>
      <div class="panel-title"><Route :size="18" />路径反馈</div>
    </template>
    <div class="feedback-row">
      <el-radio-group v-model="feedbackRating">
        <el-radio-button label="valid">valid</el-radio-button>
        <el-radio-button label="partial">partial</el-radio-button>
        <el-radio-button label="invalid">invalid</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :disabled="!canSubmit" @click="emit('submitRouteFeedback')">提交反馈</el-button>
    </div>
    <div class="trace-grid">
      <JsonBlock :value="feedbackResult" />
      <JsonBlock :value="routeResult" />
      <JsonBlock :value="graphs" />
    </div>
  </el-card>
</template>
