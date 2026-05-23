<script setup lang="ts">
import { computed, reactive } from 'vue';
import { useRoute } from 'vue-router';
import { BotMessageSquare, BookOpen, GitPullRequest, PlugZap, Settings } from 'lucide-vue-next';
import type { RuntimeConfig } from '../api';

const runtime = reactive<RuntimeConfig>({ token: 'mock-token', projectId: 'P001' });
const route = useRoute();

const activeMenu = computed(() => String(route.name || 'chat'));
</script>

<template>
  <el-container class="system-shell">
    <el-aside class="system-sidebar" width="236px">
      <div class="brand-block">
        <div class="brand-mark">SS</div>
        <div>
          <h1>Smart Support</h1>
          <p>工程诊断平台</p>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="nav-menu">
        <el-menu-item index="chat" route="/chat">
          <el-icon><BotMessageSquare /></el-icon>
          <span>用户诊断</span>
        </el-menu-item>
        <el-sub-menu index="admin">
          <template #title>
            <el-icon><Settings /></el-icon>
            <span>后台管理</span>
          </template>
          <el-menu-item index="admin-knowledge" route="/admin/knowledge">
            <el-icon><BookOpen /></el-icon>
            <span>知识库与图谱</span>
          </el-menu-item>
          <el-menu-item index="admin-tickets" route="/admin/tickets">
            <el-icon><GitPullRequest /></el-icon>
            <span>问题工单</span>
          </el-menu-item>
          <el-menu-item index="admin-mcp" route="/admin/mcp-tools">
            <el-icon><PlugZap /></el-icon>
            <span>MCP 工具库</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="system-header" height="64px">
        <div>
          <strong>{{ route.meta.title || '用户诊断' }}</strong>
          <span>{{ route.meta.subtitle || '聊天式工程问题诊断入口' }}</span>
        </div>
        <div class="runtime-panel compact">
          <el-input v-model="runtime.projectId" placeholder="Project ID" />
          <el-input v-model="runtime.token" placeholder="Token" show-password />
        </div>
      </el-header>
      <el-main class="system-main">
        <router-view :runtime="runtime" />
      </el-main>
    </el-container>
  </el-container>
</template>
