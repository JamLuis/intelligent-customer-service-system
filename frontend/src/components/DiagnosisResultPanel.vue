<script setup lang="ts">
import { Activity } from 'lucide-vue-next';

defineProps<{
  caseDetail: Record<string, unknown> | null;
}>();
</script>

<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="panel-title"><Activity :size="18" />诊断结果</div>
    </template>
    <el-descriptions v-if="caseDetail" :column="1" border>
      <el-descriptions-item label="Case">{{ caseDetail.caseId }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ caseDetail.status }}</el-descriptions-item>
      <el-descriptions-item label="根因">{{ caseDetail.rootCause }}</el-descriptions-item>
      <el-descriptions-item label="置信度">{{ caseDetail.confidenceScore }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else description="等待诊断结果" />
  </el-card>
</template>
