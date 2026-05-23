<script setup lang="ts">
import type { RuntimeConfig } from '../api';

defineProps<{
  runtime: RuntimeConfig;
}>();
</script>

<template>
  <section class="topbar">
    <div>
      <h1>Smart Support Engineering Console</h1>
      <p>Mock API 联调工作台</p>
    </div>
    <div class="runtime-panel">
      <el-input v-model="runtime.projectId" placeholder="Project ID" />
      <el-input v-model="runtime.token" placeholder="Bearer token" show-password />
    </div>
  </section>
</template>
