import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '../components/AppLayout.vue';
import ChatView from '../views/ChatView.vue';
import AdminKnowledgeView from '../views/admin/AdminKnowledgeView.vue';
import AdminMcpToolsView from '../views/admin/AdminMcpToolsView.vue';
import AdminTicketsView from '../views/admin/AdminTicketsView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/chat',
      children: [
        {
          path: 'chat',
          name: 'chat',
          component: ChatView,
          meta: { title: '用户诊断', subtitle: '专家模式 / 引导式客服模式' }
        },
        {
          path: 'admin/knowledge',
          name: 'admin-knowledge',
          component: AdminKnowledgeView,
          meta: { title: '知识库与图谱', subtitle: '维护知识源、入图任务和图谱资产' }
        },
        {
          path: 'admin/tickets',
          name: 'admin-tickets',
          component: AdminTicketsView,
          meta: { title: '问题工单', subtitle: '追溯用户问题、调用链和诊断结果' }
        },
        {
          path: 'admin/mcp-tools',
          name: 'admin-mcp',
          component: AdminMcpToolsView,
          meta: { title: 'MCP 工具库', subtitle: '查看接口能力、启停状态和调用影响' }
        }
      ]
    }
  ]
});
