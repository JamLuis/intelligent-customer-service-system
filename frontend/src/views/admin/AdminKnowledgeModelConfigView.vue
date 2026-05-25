<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Bot, CheckCircle2, MousePointer2, Play, Power, RefreshCw, Save, ServerCog, Sparkles } from 'lucide-vue-next';
import { api, setRuntimeConfig, type RuntimeConfig } from '../../api';

type Purpose = 'chat_answer' | 'knowledge_extract';
type ProviderMode = 'cloud' | 'local';

interface ProfileForm {
  purpose: Purpose;
  providerMode: ProviderMode;
  profileKey: string;
  displayName: string;
  provider: string;
  apiBaseUrl: string;
  apiKeyConfigured: boolean;
  apiKeyMasked: string;
  apiKeyInput: string;
  workspaceId: string;
  model: string;
  embeddingModel: string;
  embeddingDim: number;
  localRuntime: string;
  modelFilePath: string;
  contextWindow: number;
  temperature: number;
  maxTokens: number;
  forceGraphGrounding: boolean;
  enabled: boolean;
  active: boolean;
  status: string;
  lastCheckResult: Record<string, any> | null;
}

interface LocalModelOption {
  runtime: string;
  runtimeLabel: string;
  provider: string;
  apiBaseUrl: string;
  apiKey: string;
  model: string;
  modelId: string;
  displayName: string;
  profileKey: string;
  localRuntime: string;
  modifiedAt: string;
  sizeLabel: string;
  contextWindow: number;
  temperature: number;
  maxTokens: number;
  forceGraphGrounding: boolean;
  embeddingModel: string;
  embeddingDim: number;
  recommended: boolean;
  fitNote: string;
}

const props = defineProps<{ runtime: RuntimeConfig }>();

const loading = ref(false);
const checking = ref(false);
const scanning = ref(false);
const activePurpose = ref<Purpose>('chat_answer');
const activeMode = ref<ProviderMode>('local');
const localModels = ref<LocalModelOption[]>([]);
const localRuntimes = ref<Record<string, any>[]>([]);
const selectedLocalModelKey = ref('');
const runtimeStarting = reactive<Record<string, boolean>>({});
const autoChecking = ref(false);
const preloading = ref(false);

const forms = reactive<Record<Purpose, Record<ProviderMode, ProfileForm>>>({
  chat_answer: {
    cloud: defaultForm('chat_answer', 'cloud'),
    local: defaultForm('chat_answer', 'local')
  },
  knowledge_extract: {
    cloud: defaultForm('knowledge_extract', 'cloud'),
    local: defaultForm('knowledge_extract', 'local')
  }
});

const currentForm = computed(() => forms[activePurpose.value][activeMode.value]);
const purposeOptions = [
  { label: '对话问答模型', value: 'chat_answer' },
  { label: '知识抽取模型', value: 'knowledge_extract' }
];
const modeOptions = [
  { label: '云端大模型', name: 'cloud' },
  { label: '本地模型', name: 'local' }
];
const statusType = computed(() => {
  if (currentForm.value.status === 'healthy') return 'success';
  if (currentForm.value.status === 'failed') return 'danger';
  return 'info';
});
const selectedLocalModel = computed(() => localModels.value.find((item) => modelKey(item) === selectedLocalModelKey.value));

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error || '操作失败');
}

function defaultForm(purpose: Purpose, providerMode: ProviderMode): ProfileForm {
  const isChat = purpose === 'chat_answer';
  const isLocal = providerMode === 'local';
  return {
    purpose,
    providerMode,
    profileKey: isLocal ? 'default-local' : 'default-cloud',
    displayName: `${isChat ? '对话问答' : '知识抽取'} - ${isLocal ? '本地模型' : '云端模型'}`,
    provider: isLocal ? 'local-openai-compatible' : 'bailian',
    apiBaseUrl: isLocal ? 'http://127.0.0.1:11434/v1' : 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKeyConfigured: isLocal,
    apiKeyMasked: isLocal ? '********' : '',
    apiKeyInput: isLocal ? 'ollama' : '',
    workspaceId: '',
    model: isLocal ? 'qwen2.5:1.5b' : 'qwen3.6-flash',
    embeddingModel: isLocal ? 'nomic-embed-text' : 'text-embedding-v4',
    embeddingDim: isLocal ? 768 : 1536,
    localRuntime: isLocal ? 'ollama' : '',
    modelFilePath: '',
    contextWindow: 8192,
    temperature: isChat ? 0.2 : 0,
    maxTokens: isChat ? 1024 : 900,
    forceGraphGrounding: true,
    enabled: true,
    active: isChat && isLocal,
    status: 'unchecked',
    lastCheckResult: null
  };
}

function syncRuntime() {
  setRuntimeConfig(props.runtime);
}

function applyProfile(profile: Record<string, any>) {
  const purpose = String(profile.purpose || 'chat_answer') as Purpose;
  const providerMode = String(profile.providerMode || 'local') as ProviderMode;
  const form = forms[purpose][providerMode];
  form.profileKey = String(profile.profileKey || form.profileKey);
  form.displayName = String(profile.displayName || form.displayName);
  form.provider = String(profile.provider || form.provider);
  form.apiBaseUrl = String(profile.apiBaseUrl || form.apiBaseUrl);
  form.apiKeyConfigured = Boolean(profile.apiKeyConfigured);
  form.apiKeyMasked = String(profile.apiKeyMasked || '');
  form.apiKeyInput = '';
  form.workspaceId = String(profile.workspaceId || '');
  form.model = String(profile.model || form.model);
  form.embeddingModel = String(profile.embeddingModel || form.embeddingModel);
  form.embeddingDim = Number(profile.embeddingDim || form.embeddingDim);
  form.localRuntime = String(profile.localRuntime || '');
  form.modelFilePath = String(profile.modelFilePath || '');
  form.contextWindow = Number(profile.contextWindow || 8192);
  form.temperature = Number(profile.temperature ?? form.temperature);
  form.maxTokens = Number(profile.maxTokens || form.maxTokens);
  form.forceGraphGrounding = Boolean(profile.forceGraphGrounding ?? true);
  form.enabled = Boolean(profile.enabled ?? true);
  form.active = Boolean(profile.active);
  form.status = String(profile.status || 'unchecked');
  form.lastCheckResult = profile.lastCheckResult && Object.keys(profile.lastCheckResult).length ? profile.lastCheckResult : null;
}

async function loadProfiles() {
  loading.value = true;
  syncRuntime();
  try {
    for (const purpose of ['chat_answer', 'knowledge_extract'] as Purpose[]) {
      const profiles = await api.listModelProfiles(purpose);
      for (const providerMode of ['cloud', 'local'] as ProviderMode[]) {
        const modeProfiles = profiles.filter((profile: Record<string, any>) => profile.providerMode === providerMode);
        const preferred = modeProfiles.find((profile: Record<string, any>) => profile.active)
          || modeProfiles.find((profile: Record<string, any>) => profile.status === 'healthy')
          || modeProfiles[0];
        if (preferred) applyProfile(preferred);
      }
    }
  } catch (error) {
    ElMessage.error(`加载模型配置失败：${errorMessage(error)}`);
  } finally {
    loading.value = false;
  }
}

function modelKey(model: LocalModelOption) {
  return `${model.runtime}::${model.model}`;
}

async function scanLocalModels() {
  scanning.value = true;
  activeMode.value = 'local';
  syncRuntime();
  try {
    const result = await api.discoverLocalModels(activePurpose.value);
    localModels.value = Array.isArray(result.models) ? result.models as unknown as LocalModelOption[] : [];
    localRuntimes.value = Array.isArray(result.runtimes) ? result.runtimes as Record<string, any>[] : [];
    const currentMatched = localModels.value.find((item) => item.model === currentForm.value.model && item.apiBaseUrl === currentForm.value.apiBaseUrl);
    if (currentMatched) {
      selectedLocalModelKey.value = modelKey(currentMatched);
    } else if (selectedLocalModelKey.value && !localModels.value.some((item) => modelKey(item) === selectedLocalModelKey.value)) {
      selectedLocalModelKey.value = '';
    }
    if (localModels.value.length) {
      ElMessage.success(`已发现 ${localModels.value.length} 个本地模型`);
    } else {
      ElMessage.warning('没有发现可直接使用的本地模型，请确认 Ollama 或本地 OpenAI 服务已启动');
    }
  } catch (error) {
    ElMessage.error(`扫描本地模型失败：${errorMessage(error)}`);
  } finally {
    scanning.value = false;
  }
}

async function applyLocalModel(model: LocalModelOption) {
  const form = currentForm.value;
  activeMode.value = 'local';
  form.profileKey = model.profileKey;
  form.displayName = model.displayName;
  form.provider = model.provider;
  form.apiBaseUrl = model.apiBaseUrl;
  form.apiKeyInput = model.apiKey || 'ollama';
  form.model = model.model;
  form.embeddingModel = model.embeddingModel || 'text-embedding-v4';
  form.embeddingDim = Number(model.embeddingDim || 1536);
  form.localRuntime = model.localRuntime || model.runtime;
  form.modelFilePath = '';
  form.contextWindow = Number(model.contextWindow || 8192);
  form.temperature = Number(model.temperature ?? (activePurpose.value === 'chat_answer' ? 0.2 : 0));
  form.maxTokens = Number(model.maxTokens || (activePurpose.value === 'chat_answer' ? 384 : 900));
  form.forceGraphGrounding = Boolean(model.forceGraphGrounding ?? true);
  form.enabled = true;
  form.status = 'unchecked';
  form.lastCheckResult = null;
  selectedLocalModelKey.value = modelKey(model);
  await autoCheckCurrentModel();
}

async function autoCheckCurrentModel() {
  autoChecking.value = true;
  syncRuntime();
  try {
    await api.saveModelProfile(activePurpose.value, 'local', currentForm.value.profileKey, payload(currentForm.value));
    const result = await api.checkModelProfile(activePurpose.value, 'local', currentForm.value.profileKey);
    currentForm.value.lastCheckResult = result;
    currentForm.value.status = result?.ok ? 'healthy' : 'failed';
    if (result?.ok) {
      ElMessage.success('已自动检查：模型可用');
    } else {
      ElMessage.warning(String(result?.message || '已自动检查：模型不可用'));
    }
  } catch (error) {
    currentForm.value.status = 'failed';
    currentForm.value.lastCheckResult = {
      ok: false,
      message: errorMessage(error),
      apiBaseUrl: currentForm.value.apiBaseUrl,
      model: currentForm.value.model,
      modelsOk: false,
      chatOk: false,
      embeddingOk: false
    };
    ElMessage.error(`自动检查失败：${errorMessage(error)}`);
  } finally {
    autoChecking.value = false;
  }
}

function canStartRuntime(runtime: string) {
  return runtime === 'ollama' || runtime === 'llama.cpp';
}

async function startRuntime(runtime: string) {
  runtimeStarting[runtime] = true;
  syncRuntime();
  try {
    const result = await api.startLocalRuntime(runtime);
    ElMessage.success(String(result?.message || `${runtime} 启动命令已执行`));
    await scanLocalModels();
  } catch (error) {
    ElMessage.error(`启动 ${runtime} 失败：${errorMessage(error)}`);
  } finally {
    runtimeStarting[runtime] = false;
  }
}

async function saveCheckActivateSelected() {
  const model = selectedLocalModel.value;
  if (!model) {
    ElMessage.warning('请先选择一个本地模型');
    return;
  }
  applyLocalModel(model);
  loading.value = true;
  checking.value = true;
  syncRuntime();
  try {
    applyProfile(await api.saveModelProfile(activePurpose.value, 'local', currentForm.value.profileKey, payload(currentForm.value)));
    const result = await api.checkModelProfile(activePurpose.value, 'local', currentForm.value.profileKey);
    currentForm.value.lastCheckResult = result;
    currentForm.value.status = result?.ok ? 'healthy' : 'failed';
    if (!result?.ok) {
      ElMessage.warning(String(result?.message || '模型验证未通过，请换一个模型或检查本地服务'));
      return;
    }
    applyProfile(await api.activateModelProfile(activePurpose.value, 'local', currentForm.value.profileKey));
    await loadProfiles();
    preloadCurrentPurpose(); // lazy load: preload in runtime memory
    ElMessage.success('已保存、验证并设为当前使用模型');
  } catch (error) {
    ElMessage.error(`一键配置失败：${errorMessage(error)}`);
  } finally {
    checking.value = false;
    loading.value = false;
  }
}

function payload(form: ProfileForm) {
  const body: Record<string, unknown> = {
    displayName: form.displayName,
    provider: form.provider,
    apiBaseUrl: form.apiBaseUrl,
    workspaceId: form.workspaceId,
    model: form.model,
    embeddingModel: form.embeddingModel,
    embeddingDim: form.embeddingDim,
    localRuntime: form.localRuntime,
    modelFilePath: form.modelFilePath,
    contextWindow: form.contextWindow,
    temperature: form.temperature,
    maxTokens: form.maxTokens,
    forceGraphGrounding: form.forceGraphGrounding,
    enabled: form.enabled
  };
  if (form.apiKeyInput.trim()) {
    body.apiKey = form.apiKeyInput.trim();
  }
  return body;
}

async function saveCurrent() {
  loading.value = true;
  syncRuntime();
  try {
    applyProfile(await api.saveModelProfile(activePurpose.value, activeMode.value, currentForm.value.profileKey, payload(currentForm.value)));
    ElMessage.success('当前配置已保存');
  } catch (error) {
    ElMessage.error(`保存失败：${errorMessage(error)}`);
  } finally {
    loading.value = false;
  }
}

async function checkCurrent() {
  checking.value = true;
  syncRuntime();
  try {
    await api.saveModelProfile(activePurpose.value, activeMode.value, currentForm.value.profileKey, payload(currentForm.value));
    const result = await api.checkModelProfile(activePurpose.value, activeMode.value, currentForm.value.profileKey);
    currentForm.value.lastCheckResult = result;
    currentForm.value.status = result?.ok ? 'healthy' : 'failed';
    if (result?.ok) {
      ElMessage.success('模型连接正常');
    } else {
      ElMessage.warning(String(result?.message || '模型验证未通过，请查看验证结果'));
    }
  } catch (error) {
    currentForm.value.status = 'failed';
    currentForm.value.lastCheckResult = {
      ok: false,
      message: errorMessage(error),
      apiBaseUrl: currentForm.value.apiBaseUrl,
      model: currentForm.value.model,
      modelsOk: false,
      chatOk: false,
      embeddingOk: false
    };
    ElMessage.error(`验证失败：${errorMessage(error)}`);
  } finally {
    checking.value = false;
  }
}

async function activateCurrent() {
  loading.value = true;
  syncRuntime();
  try {
    await api.saveModelProfile(activePurpose.value, activeMode.value, currentForm.value.profileKey, payload(currentForm.value));
    applyProfile(await api.activateModelProfile(activePurpose.value, activeMode.value, currentForm.value.profileKey));
    await loadProfiles();
    preloadCurrentPurpose(); // lazy load: preload in runtime memory
    ElMessage.success('已设为当前使用模型');
  } catch (error) {
    ElMessage.error(`启用失败：${errorMessage(error)}`);
  } finally {
    loading.value = false;
  }
}

function useGemmaPreset() {
  const form = currentForm.value;
  form.profileKey = 'gemma-local-gguf';
  form.displayName = `${activePurpose.value === 'chat_answer' ? '对话问答' : '知识抽取'} - Gemma 4 E4B Q4_0`;
  form.provider = 'local-openai-compatible';
  form.apiBaseUrl = 'http://127.0.0.1:11435/v1';
  form.model = 'gemma-4-E4B-it-Q4_0';
  form.localRuntime = 'llama.cpp';
  form.modelFilePath = '/Users/lucas/Work/Personal/llama.cpp-kleidiai/models/gemma-4-E4B-it-Q4_0.gguf';
  form.contextWindow = 8192;
  form.temperature = activePurpose.value === 'chat_answer' ? 0.2 : 0;
  form.maxTokens = activePurpose.value === 'chat_answer' ? 1024 : 900;
}

async function preloadCurrentPurpose() {
  const purpose = activePurpose.value;
  const form = forms[purpose].local;
  if (!form.active || form.providerMode !== 'local') return;
  preloading.value = true;
  syncRuntime();
  try {
    const result = await api.preloadModelForPurpose(purpose);
    if (result?.available === false) {
      ElMessage.warning(String(result?.message || '模型预加载失败'));
    } else {
      ElMessage.success(String(result?.message || `${purpose === 'chat_answer' ? '对话' : '抽取'}模型已加载到内存`));
    }
  } catch (error) {
    ElMessage.warning(`模型预加载失败：${errorMessage(error)}`);
  } finally {
    preloading.value = false;
  }
}

watch(activePurpose, () => {
  preloadCurrentPurpose();
});

onMounted(async () => {
  await loadProfiles();
  await scanLocalModels();
});
</script>

<template>
  <section class="admin-grid" v-loading="loading">
    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><ServerCog :size="18" />模型配置</div>
      </template>

      <div class="toolbar top-toolbar">
        <el-segmented v-model="activePurpose" :options="purposeOptions" />
        <el-tag v-if="forms[activePurpose].cloud.active" type="success">当前：云端</el-tag>
        <el-tag v-if="forms[activePurpose].local.active" type="success">当前：本地</el-tag>
        <el-tag v-if="preloading" type="warning">正在加载模型到内存…</el-tag>
        <el-button :icon="Sparkles" :loading="scanning" @click="scanLocalModels">扫描本机模型</el-button>
      </div>

      <el-tabs v-model="activeMode" class="model-profile-tabs">
        <el-tab-pane v-for="item in modeOptions" :key="item.name" :label="item.label" :name="item.name">
          <div v-if="activeMode === 'local'" class="local-model-picker">
            <div class="picker-header">
              <div>
                <div class="picker-title">本机模型</div>
                <div class="picker-subtitle">自动识别 Ollama、llama.cpp、LM Studio；你可以自由选择任意模型，切换时会自动检查状态。</div>
              </div>
              <div class="toolbar">
                <el-button :icon="RefreshCw" :loading="scanning" @click="scanLocalModels">重新扫描</el-button>
                <el-button type="primary" :icon="MousePointer2" :disabled="!selectedLocalModel" :loading="loading || checking || autoChecking" @click="saveCheckActivateSelected">一键使用选中模型</el-button>
              </div>
            </div>

            <div v-if="localRuntimes.length" class="runtime-strip">
              <div v-for="runtime in localRuntimes" :key="String(runtime.runtime)" class="runtime-item">
                <el-tag :type="runtime.available ? 'success' : 'info'" effect="plain">
                  {{ runtime.displayName }}：{{ runtime.available ? '可用' : '未启动' }}
                </el-tag>
                <el-button v-if="!runtime.available" size="small" :icon="Play" :disabled="!canStartRuntime(String(runtime.runtime))" :loading="Boolean(runtimeStarting[String(runtime.runtime)])" @click="startRuntime(String(runtime.runtime))">
                  启动
                </el-button>
              </div>
            </div>

            <el-radio-group v-if="localModels.length" v-model="selectedLocalModelKey" class="model-option-grid">
              <label v-for="model in localModels" :key="modelKey(model)" class="model-option" :class="{ selected: selectedLocalModelKey === modelKey(model) }">
                <el-radio :value="modelKey(model)" @change="applyLocalModel(model)">
                  <span class="model-name">{{ model.model }}</span>
                </el-radio>
                <div class="model-meta">
                  <el-tag size="small" effect="plain">{{ model.runtimeLabel }}</el-tag>
                  <el-tag v-if="model.sizeLabel" size="small" effect="plain">{{ model.sizeLabel }}</el-tag>
                </div>
                <div class="model-note">{{ model.fitNote }}</div>
              </label>
            </el-radio-group>
            <el-empty v-else description="尚未扫描到本机模型，点击重新扫描" />
          </div>

          <el-form label-position="top" class="model-config-form">
            <div class="model-config-grid">
              <el-form-item label="显示名称">
                <el-input v-model="currentForm.displayName" />
              </el-form-item>
              <el-form-item label="Profile Key">
                <el-input v-model="currentForm.profileKey" />
              </el-form-item>
              <el-form-item label="Provider">
                <el-input v-model="currentForm.provider" />
              </el-form-item>
              <el-form-item label="模型服务地址">
                <el-input v-model="currentForm.apiBaseUrl" />
              </el-form-item>
              <el-form-item label="模型名称">
                <el-input v-model="currentForm.model" />
              </el-form-item>
              <el-form-item label="API Key">
                <el-input v-model="currentForm.apiKeyInput" show-password :placeholder="currentForm.apiKeyConfigured ? `已配置：${currentForm.apiKeyMasked}` : '本地可填 ollama，云端填写 API Key'" />
              </el-form-item>
              <el-form-item label="Workspace ID">
                <el-input v-model="currentForm.workspaceId" placeholder="可选" />
              </el-form-item>
              <el-form-item label="Embedding 模型">
                <el-input v-model="currentForm.embeddingModel" />
              </el-form-item>
              <el-form-item label="Embedding 维度">
                <el-input-number v-model="currentForm.embeddingDim" :min="128" :max="8192" :step="128" />
              </el-form-item>
              <el-form-item label="上下文窗口">
                <el-input-number v-model="currentForm.contextWindow" :min="1024" :max="131072" :step="1024" />
              </el-form-item>
              <el-form-item label="Temperature">
                <el-input-number v-model="currentForm.temperature" :min="0" :max="2" :step="0.1" />
              </el-form-item>
              <el-form-item label="Max Tokens">
                <el-input-number v-model="currentForm.maxTokens" :min="128" :max="8192" :step="128" />
              </el-form-item>
              <el-form-item v-if="activeMode === 'local'" label="本地运行时">
                <el-input v-model="currentForm.localRuntime" placeholder="ollama / llama.cpp / lm-studio" />
              </el-form-item>
              <el-form-item v-if="activeMode === 'local'" label="本地模型文件">
                <el-input v-model="currentForm.modelFilePath" />
              </el-form-item>
              <el-form-item label="启用当前 Profile">
                <el-switch v-model="currentForm.enabled" active-text="启用" inactive-text="停用" />
              </el-form-item>
              <el-form-item v-if="activePurpose === 'chat_answer'" label="强制按知识库关系图谱回答">
                <el-switch v-model="currentForm.forceGraphGrounding" active-text="启用" inactive-text="停用" />
              </el-form-item>
              <el-form-item v-else label="知识抽取启用 LLM/OpenIE">
                <el-switch v-model="currentForm.enabled" active-text="启用" inactive-text="停用" />
              </el-form-item>
            </div>

            <div class="toolbar top-toolbar">
              <el-button v-if="activeMode === 'local'" :icon="Bot" @click="useGemmaPreset">使用 Gemma GGUF 预设</el-button>
              <el-button type="primary" :icon="Save" @click="saveCurrent">保存当前 Tab</el-button>
              <el-button :icon="Power" @click="activateCurrent">设为当前使用</el-button>
              <el-button :icon="CheckCircle2" :loading="checking || autoChecking" @click="checkCurrent">验证连接</el-button>
              <el-button :icon="RefreshCw" @click="loadProfiles">刷新</el-button>
              <el-tag :type="statusType">{{ currentForm.status }}</el-tag>
              <el-tag v-if="currentForm.active" type="success">active</el-tag>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-card shadow="never" class="panel-card wide-card">
      <template #header>
        <div class="panel-title"><Bot :size="18" />验证结果</div>
      </template>
      <el-descriptions v-if="currentForm.lastCheckResult" :column="2" border>
        <el-descriptions-item label="整体状态">
          <el-tag :type="currentForm.lastCheckResult.ok ? 'success' : 'danger'">{{ currentForm.lastCheckResult.ok ? '可用' : '不可用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentForm.lastCheckResult.latencyMs || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="模型服务">{{ currentForm.lastCheckResult.apiBaseUrl }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ currentForm.lastCheckResult.model }}</el-descriptions-item>
        <el-descriptions-item label="/models">{{ currentForm.lastCheckResult.modelsOk ? '可访问' : '不可访问' }}</el-descriptions-item>
        <el-descriptions-item label="Chat 调用">{{ currentForm.lastCheckResult.chatOk ? '成功' : '失败' }}</el-descriptions-item>
        <el-descriptions-item label="Embedding 调用">{{ currentForm.lastCheckResult.embeddingOk ? '成功' : '未验证/失败' }}</el-descriptions-item>
        <el-descriptions-item label="返回信息" :span="2">{{ currentForm.lastCheckResult.message }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="保存后点击验证连接，验证结果只写回当前用途和当前 Tab" />
    </el-card>
  </section>
</template>

<style scoped>
.local-model-picker {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 16px;
  background: var(--el-fill-color-extra-light);
}

.runtime-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.picker-title {
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 24px;
}

.picker-subtitle,
.model-note {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 20px;
}

.runtime-strip,
.model-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.runtime-strip {
  margin-bottom: 12px;
}

.model-option-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
  width: 100%;
}

.model-option {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 112px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.model-option.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.model-name {
  font-weight: 700;
  color: var(--el-text-color-primary);
}

@media (max-width: 720px) {
  .picker-header {
    flex-direction: column;
  }
}
</style>
