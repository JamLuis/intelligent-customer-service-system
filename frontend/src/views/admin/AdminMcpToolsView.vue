<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { PlugZap, RefreshCw } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';

const props = defineProps<{
  runtime: RuntimeConfig;
}>();

const capabilities = ref<Record<string, any>[]>([]);
const impact = ref<Record<string, any> | null>(null);
const loading = ref(false);

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

async function loadCapabilities() {
  loading.value = true;
  syncRuntime();
  try {
    const response = await api.listCapabilities();
    capabilities.value = Array.isArray(response.items) ? response.items : [];
    if (capabilities.value[0]) {
      impact.value = await api.capabilityImpact(capabilities.value[0].capabilityId);
    }
  } finally {
    loading.value = false;
  }
}

async function setCapabilityStatus(row: Record<string, any>, targetStatus: 'enabled' | 'disabled') {
  loading.value = true;
  syncRuntime();
  try {
    await api.updateCapabilityStatus(row.capabilityId, { targetStatus, reason: '后台管理操作', impactConfirmed: true });
    await loadCapabilities();
    ElMessage.success(targetStatus === 'enabled' ? '已启用' : '已停用');
  } finally {
    loading.value = false;
  }
}

async function viewImpact(row: Record<string, any>) {
  syncRuntime();
  impact.value = await api.capabilityImpact(row.capabilityId);
}

onMounted(loadCapabilities);
</script>

<template>
  <section class="admin-grid" v-loading="loading">
    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><PlugZap :size="18" />MCP 工具库</div>
      </template>
      <div class="toolbar top-toolbar">
        <el-button :icon="RefreshCw" @click="loadCapabilities">刷新工具</el-button>
      </div>
      <el-table :data="capabilities">
        <el-table-column prop="capabilityCode" label="接口能力" min-width="180" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="riskLevel" label="风险" width="90" />
        <el-table-column prop="status" label="启停状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调用状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.lastHealthStatus === 'healthy' ? 'success' : 'danger'">{{ row.lastHealthStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewImpact(row)">影响范围</el-button>
            <el-button v-if="row.status !== 'enabled'" size="small" type="success" @click="setCapabilityStatus(row, 'enabled')">启用</el-button>
            <el-button v-else size="small" type="warning" @click="setCapabilityStatus(row, 'disabled')">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title">调用链与影响范围</div>
      </template>
      <el-descriptions v-if="impact" :column="2" border>
        <el-descriptions-item label="能力">{{ impact.capabilityCode }}</el-descriptions-item>
        <el-descriptions-item label="近 7 日调用">{{ impact.recentCallCount7d }}</el-descriptions-item>
        <el-descriptions-item label="影响路径" :span="2">{{ impact.impactRoutes }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="选择能力后查看影响范围" />
    </el-card>
  </section>
</template>
