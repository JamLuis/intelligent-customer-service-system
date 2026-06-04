import { useSyncExternalStore } from 'react'

export type DiagnosisStep = {
  id: string
  title: string
  detail: string
  state: 'done' | 'running' | 'warning' | 'queued'
}

type ToolCall = {
  id: string
  label: string
  state: 'done' | 'running' | 'queued'
}

type SummaryMetric = {
  id: string
  label: string
  value: string
  tone: 'blue' | 'warning' | 'success'
}

type PagerItem = {
  id: string
  label: string
  active?: boolean
  kind?: 'button' | 'ellipsis'
}

type NavigationItem = {
  id: string
  label: string
  active?: boolean
}

type EvidenceNode = {
  id: string
  label: string
  value: string
  description: string
  state: string
  tone: 'info' | 'success' | 'warning' | 'danger'
  highlight?: boolean
}

type CaseItem = {
  id: string
  title: string
  question: string
  status: string
  statusTone: 'info' | 'success' | 'warning' | 'danger'
  date: string
  owner: string
  tenant: string
  priority: string
  caseCode: string
  questionTime: string
  aiTime: string
  confidence: string
  responseDeadline: string
  severityLabel: string
  workOrderType: string
  attachmentsSummary: string
  rootCause: string
  impact: string[]
  suggestions: string[]
  steps: DiagnosisStep[]
  evidence: EvidenceNode[]
  toolCalls: ToolCall[]
}

type DiagnosisUiConfig = {
  layout: {
    brandTitle: string
    badgeText: string
    searchPlaceholder: string
    notificationCount: string
    userName: string
    userRole: string
    navItems: NavigationItem[]
    footerVersion: string
    footerMeta: string[]
  }
  caseSidebar: {
    title: string
    createLabel: string
    searchPlaceholder: string
    filterAriaLabel: string
    metrics: SummaryMetric[]
    pager: PagerItem[]
  }
  chat: {
    sessionLabel: string
    copyCaseCodeAriaLabel: string
    clearLabel: string
    expandAriaLabel: string
    assistantTitle: string
    assistantIntro: string
    toolCallPrefix: string
    composerPlaceholder: string
    attachmentAriaLabel: string
    imageAriaLabel: string
    sendLabel: string
    detailActionLabel: string
    stateText: Record<DiagnosisStep['state'], string>
  }
  evidence: {
    title: string
    collapseLabel: string
    closeAriaLabel: string
    pathTitle: string
    fullViewLabel: string
    relationAriaLabel: string
    detailAriaLabel: string
  }
  conclusion: {
    title: string
    rootCauseLabel: string
    confidenceLabel: string
    impactLabel: string
    impactLevelLabel: string
    suggestionLabel: string
    ownerLabel: string
    reassignLabel: string
    priorityLabel: string
    responseDeadlineLabel: string
  }
  workOrder: {
    title: string
    detailLinkLabel: string
    titleLabel: string
    descriptionLabel: string
    typeLabel: string
    priorityLabel: string
    ownerLabel: string
    attachmentsLabel: string
    generateLabel: string
    exportLabel: string
  }
}

const mockUi: DiagnosisUiConfig = {
  layout: {
    brandTitle: '业务智能诊断平台',
    badgeText: 'AI',
    searchPlaceholder: '搜索',
    notificationCount: '12',
    userName: '张工程师',
    userRole: '管理员',
    navItems: [
      { id: 'nav-1', label: 'AI诊断中心', active: true },
      { id: 'nav-2', label: '案例知识库' },
      { id: 'nav-3', label: '运营分析驾驶舱' },
      { id: 'nav-4', label: '系统管理' },
    ],
    footerVersion: '© 2024 业务智能诊断平台 v2.1.0',
    footerMeta: ['在线用户：24', '系统状态：正常', '数据同步：10:30:16'],
  },
  caseSidebar: {
    title: '案件中心',
    createLabel: '新建会话',
    searchPlaceholder: '搜索案件标题/关键字',
    filterAriaLabel: '筛选案件',
    metrics: [
      { id: 'metric-1', label: '全部', value: '124', tone: 'blue' },
      { id: 'metric-2', label: '处理中', value: '28', tone: 'warning' },
      { id: 'metric-3', label: '已完成', value: '96', tone: 'success' },
    ],
    pager: [
      { id: 'page-prev', label: '<' },
      { id: 'page-1', label: '1', active: true },
      { id: 'page-2', label: '2' },
      { id: 'page-3', label: '3' },
      { id: 'page-ellipsis', label: '...', kind: 'ellipsis' },
      { id: 'page-13', label: '13' },
      { id: 'page-next', label: '>' },
    ],
  },
  chat: {
    sessionLabel: '会话ID:',
    copyCaseCodeAriaLabel: '复制会话ID',
    clearLabel: '清空会话',
    expandAriaLabel: '全屏查看',
    assistantTitle: 'AI诊断助手',
    assistantIntro: '正在分析问题，请稍候...',
    toolCallPrefix: '正在调用',
    composerPlaceholder: '请输入您的问题，AI将为您分析...',
    attachmentAriaLabel: '上传附件',
    imageAriaLabel: '上传图片',
    sendLabel: '发送',
    detailActionLabel: '查看详情',
    stateText: {
      done: '完成',
      running: '进行中',
      warning: '异常',
      queued: '等待中',
    },
  },
  evidence: {
    title: '诊断证据链',
    collapseLabel: '收起',
    closeAriaLabel: '关闭侧栏',
    pathTitle: '当前诊断路径',
    fullViewLabel: '完整视图',
    relationAriaLabel: '定位关联关系',
    detailAriaLabel: '查看证据详情',
  },
  conclusion: {
    title: '诊断结论',
    rootCauseLabel: '根因分析',
    confidenceLabel: '置信度：',
    impactLabel: '影响范围',
    impactLevelLabel: '影响等级：',
    suggestionLabel: '处理建议',
    ownerLabel: '建议责任人',
    reassignLabel: '重新分配',
    priorityLabel: '紧急程度',
    responseDeadlineLabel: '建议响应时间 ',
  },
  workOrder: {
    title: '工单信息',
    detailLinkLabel: '查看详情',
    titleLabel: '工单标题',
    descriptionLabel: '问题描述',
    typeLabel: '工单类型',
    priorityLabel: '优先级',
    ownerLabel: '建议责任人',
    attachmentsLabel: '附件',
    generateLabel: '生成工单',
    exportLabel: '导出诊断结论',
  },
}

const cases: CaseItem[] = [
  {
    id: 'case-001',
    title: '长江2号地图不显示',
    question: '为什么长江2号地图不显示',
    status: '处理中',
    statusTone: 'warning',
    date: '2026-06-01',
    owner: '实施工程师',
    tenant: '江苏海事一中心',
    priority: 'P2',
    caseCode: 'CASE-20240602-0001',
    questionTime: '10:30:15',
    aiTime: '10:30:16',
    confidence: '92%',
    responseDeadline: '2 小时内',
    severityLabel: '中等',
    workOrderType: '设备故障',
    attachmentsSummary: '定位链路截图 / 证据链节点 / 诊断记录',
    rootCause: '船舶定位器 20 分钟未上传数据，地图服务无法刷新当前位置。',
    impact: ['地图显示异常', '实时轨迹异常', '围栏判断异常'],
    suggestions: ['检查定位器网络连通性', '检查 MQTT 连接状态', '检查 AIS 服务与定位数据回流'],
    steps: [
      { id: 'step-1', title: '步骤 1：检查船舶资料', detail: '已确认船舶基础资料完整，租户归属正常。', state: 'done' },
      { id: 'step-2', title: '检查定位设备状态', detail: '定位设备 LOC-10001 最后上报时间 20 分钟前。', state: 'warning' },
      { id: 'step-3', title: '检查定位数据上报', detail: '正在检查 MQTT 连接及数据上报情况。', state: 'running' },
      { id: 'step-4', title: '检查地图服务状态', detail: '等待前置节点完成。', state: 'queued' },
      { id: 'step-5', title: '检查前端筛选条件', detail: '等待前置节点完成。', state: 'queued' },
    ],
    evidence: [
      { id: 'ev-1', label: '船舶', value: '长江2号', description: 'IMO: 123456789 / 状态：航行中', state: '正常', tone: 'success' },
      { id: 'ev-2', label: '定位设备', value: 'LOC-10001', description: '类型：北斗定位器 / 状态：在线(异常)', state: '异常', tone: 'warning' },
      { id: 'ev-3', label: '定位数据', value: '最后上报时间 2024-06-02 10:10:15', description: '超时：20 分钟', state: '异常', tone: 'danger', highlight: true },
      { id: 'ev-4', label: '数据服务', value: '定位服务-MQTT', description: '状态：连接正常', state: '正常', tone: 'success' },
      { id: 'ev-5', label: '地图服务', value: '地图渲染服务', description: '状态：待检查', state: '待检查', tone: 'info' },
      { id: 'ev-6', label: '前端页面', value: '船舶监控页面', description: '状态：待检查', state: '待检查', tone: 'info' },
    ],
    toolCalls: [
      { id: 'tool-1', label: '船舶信息查询', state: 'done' },
      { id: 'tool-2', label: '定位数据查询', state: 'running' },
      { id: 'tool-3', label: '地图服务检测', state: 'queued' },
    ],
  },
  {
    id: 'case-002',
    title: '驾驶舱抓图失败',
    question: '驾驶舱抓图为什么失败',
    status: '待确认',
    statusTone: 'danger',
    date: '2026-06-02',
    owner: '后端工程师',
    tenant: '蓝水木接入租户',
    priority: 'P1',
    caseCode: 'CASE-20240602-0002',
    questionTime: '09:21:28',
    aiTime: '09:21:29',
    confidence: '88%',
    responseDeadline: '30 分钟内',
    severityLabel: '高',
    workOrderType: '视频抓图故障',
    attachmentsSummary: 'GB 通道配置截图 / 抓图接口返回 / 鉴权日志',
    rootCause: '驾驶舱 GB 通道未匹配到含“驾驶”的媒体通道配置。',
    impact: ['驾驶舱抓图失败', '视频证据缺失', '对外开放接口返回 201'],
    suggestions: ['补齐 jww_dvr_media_gb_config 通道配置', '复核 shipName 与 openId 权限绑定', '验证 VideoService 手工抓图'],
    steps: [
      { id: 's2-1', title: '步骤 1：检查第三方 token', detail: 'Token 校验通过，openId 正常。', state: 'done' },
      { id: 's2-2', title: '步骤 2：检查船舶权限', detail: '船舶存在，filter_id 包含该船。', state: 'done' },
      { id: 's2-3', title: '步骤 3：检查驾驶舱通道', detail: '未匹配到 media_name 含驾驶的通道。', state: 'warning' },
      { id: 's2-4', title: '步骤 4：检查抓图服务', detail: '等待通道修复后重试。', state: 'queued' },
    ],
    evidence: [
      { id: 's2-ev1', label: '接入方', value: 'openId 已认证', description: '第三方 token 校验通过', state: '正常', tone: 'success' },
      { id: 's2-ev2', label: '船舶权限', value: '已授权', description: 'filter_id 命中该船', state: '正常', tone: 'success' },
      { id: 's2-ev3', label: 'GB 通道', value: '未找到驾驶舱通道', description: 'media_name 未命中驾驶舱配置', state: '缺失', tone: 'danger', highlight: true },
      { id: 's2-ev4', label: '抓图接口', value: '返回 201', description: '等待通道修复后重试', state: '失败', tone: 'danger' },
      { id: 's2-ev5', label: '开放 API', value: '无法返回 base64', description: '影响第三方集成接口', state: '受影响', tone: 'warning' },
    ],
    toolCalls: [
      { id: 'tool-4', label: '第三方 token 校验', state: 'done' },
      { id: 'tool-5', label: '船舶权限核验', state: 'done' },
      { id: 'tool-6', label: 'GB 通道检查', state: 'running' },
    ],
  },
  {
    id: 'case-003',
    title: '账号过期提醒未收到',
    question: '为什么账号过期提醒未收到',
    status: '已完成',
    statusTone: 'success',
    date: '06-01',
    owner: '王工程师',
    tenant: '南通港航',
    priority: 'P4',
    caseCode: 'CASE-20240601-0011',
    questionTime: '13:11:02',
    aiTime: '13:11:04',
    confidence: '95%',
    responseDeadline: '已关闭',
    severityLabel: '低',
    workOrderType: '消息通知异常',
    attachmentsSummary: '短信模板配置 / 发送日志 / 补发记录',
    rootCause: '短信模板开关关闭，导致账号过期提醒未发送。',
    impact: ['续费提醒漏发'],
    suggestions: ['开启短信模板', '补发提醒'],
    steps: [
      { id: 's3-1', title: '检查模板开关', detail: '模板开关关闭。', state: 'done' },
      { id: 's3-2', title: '检查发送日志', detail: '发送任务未触发。', state: 'done' },
    ],
    evidence: [
      { id: 's3-ev1', label: '短信模板', value: '已关闭', description: '提醒模板开关关闭', state: '异常', tone: 'warning' },
    ],
    toolCalls: [{ id: 'tool-7', label: '短信日志检查', state: 'done' }],
  },
  {
    id: 'case-004',
    title: '一级告警推送异常',
    question: '为什么一级告警没有推送',
    status: '处理中',
    statusTone: 'danger',
    date: '05-31 16:45',
    owner: '张工程师',
    tenant: '巡查卫士项目',
    priority: 'P2',
    caseCode: 'CASE-20240531-0088',
    questionTime: '16:46:01',
    aiTime: '16:46:05',
    confidence: '86%',
    responseDeadline: '4 小时内',
    severityLabel: '中等',
    workOrderType: '告警推送异常',
    attachmentsSummary: '推送任务日志 / 限流规则 / 终端接收记录',
    rootCause: '告警推送任务被租户限流配置拦截。',
    impact: ['移动端未收到推送'],
    suggestions: ['调整租户限流阈值'],
    steps: [{ id: 's4-1', title: '检查推送队列', detail: '队列正常。', state: 'done' }],
    evidence: [{ id: 's4-ev1', label: '租户限流', value: '命中阈值', description: '超出分钟级发送上限', state: '异常', tone: 'warning' }],
    toolCalls: [{ id: 'tool-8', label: '推送链路检查', state: 'running' }],
  },
  {
    id: 'case-005',
    title: '设备离线分析',
    question: '为什么设备频繁离线',
    status: '已完成',
    statusTone: 'success',
    date: '05-31 14:30',
    owner: '李工程师',
    tenant: '长航智能监管',
    priority: 'P3',
    caseCode: 'CASE-20240531-0043',
    questionTime: '14:31:44',
    aiTime: '14:31:47',
    confidence: '90%',
    responseDeadline: '已关闭',
    severityLabel: '中',
    workOrderType: '设备巡检',
    attachmentsSummary: '心跳日志 / 供电检测记录 / 现场检查单',
    rootCause: '设备供电松动导致离线抖动。',
    impact: ['在线率下降'],
    suggestions: ['现场检查供电'],
    steps: [{ id: 's5-1', title: '检查心跳日志', detail: '存在间歇性中断。', state: 'done' }],
    evidence: [{ id: 's5-ev1', label: '供电状态', value: '不稳定', description: '现场供电波动', state: '异常', tone: 'warning' }],
    toolCalls: [{ id: 'tool-9', label: '心跳日志分析', state: 'done' }],
  },
  {
    id: 'case-006',
    title: '巡查漏检争议',
    question: '为什么巡查结果被判定漏检',
    status: '已归档',
    statusTone: 'info',
    date: '05-30 11:20',
    owner: '王工程师',
    tenant: '巡查业务组',
    priority: 'P4',
    caseCode: 'CASE-20240530-0022',
    questionTime: '11:21:12',
    aiTime: '11:21:16',
    confidence: '93%',
    responseDeadline: '已归档',
    severityLabel: '低',
    workOrderType: '巡查争议复核',
    attachmentsSummary: '巡查打点记录 / 轨迹回放 / 复核结论',
    rootCause: '巡查打点时间超出容忍窗口。',
    impact: ['争议复核'],
    suggestions: ['调整容忍阈值'],
    steps: [{ id: 's6-1', title: '检查打点时间', detail: '超出阈值 4 分钟。', state: 'done' }],
    evidence: [{ id: 's6-ev1', label: '巡查打点', value: '超时 4 分钟', description: '超出容忍窗口', state: '异常', tone: 'warning' }],
    toolCalls: [{ id: 'tool-10', label: '巡查日志复核', state: 'done' }],
  },
]

type DiagnosisState = {
  activeCaseId: string
  cases: CaseItem[]
  ui: DiagnosisUiConfig
}

type DiagnosisSnapshot = {
  activeCaseId: string
  cases: CaseItem[]
  activeCase: CaseItem
  ui: DiagnosisUiConfig
  selectCase: (caseId: string) => void
}

const state: DiagnosisState = {
  activeCaseId: cases[0].id,
  cases,
  ui: mockUi,
}

const listeners = new Set<() => void>()

function emitChange() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function createSnapshot(): DiagnosisSnapshot {
  const activeCase = state.cases.find((item) => item.id === state.activeCaseId) ?? state.cases[0]

  return {
    activeCaseId: state.activeCaseId,
    cases: state.cases,
    activeCase,
    ui: state.ui,
    selectCase,
  }
}

let snapshot = createSnapshot()

function selectCase(caseId: string) {
  if (state.activeCaseId === caseId) {
    return
  }

  state.activeCaseId = caseId
  snapshot = createSnapshot()
  emitChange()
}

function getSnapshot() {
  return snapshot
}

export function useDiagnosisStore() {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}
