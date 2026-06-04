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

type RoleOption = {
  id: string
  label: string
}

type SuggestedPrompt = {
  id: string
  label: string
  targetCaseId: string
}

type CaseStage = {
  id: string
  label: string
  description: string
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
  roleId: string
  roleLabel: string
  sceneLabel: string
  sceneType: string
  title: string
  question: string
  customerVoice: string
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
  quickPrompts: SuggestedPrompt[]
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
    roleSectionLabel: string
    searchPlaceholder: string
    filterAriaLabel: string
    metrics: SummaryMetric[]
    pager: PagerItem[]
  }
  chat: {
    roleLabel: string
    sceneLabel: string
    customerVoiceLabel: string
    sessionLabel: string
    copyCaseCodeAriaLabel: string
    clearLabel: string
    expandAriaLabel: string
    assistantTitle: string
    assistantIntro: string
    promptSectionLabel: string
    stageSectionLabel: string
    advanceLabel: string
    resetLabel: string
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
    roleSectionLabel: '角色视角',
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
    roleLabel: '角色视角',
    sceneLabel: '场景剧本',
    customerVoiceLabel: '客户原话',
    sessionLabel: '会话ID:',
    copyCaseCodeAriaLabel: '复制会话ID',
    clearLabel: '清空会话',
    expandAriaLabel: '全屏查看',
    assistantTitle: 'AI诊断助手',
    assistantIntro: '正在分析问题，请稍候...',
    promptSectionLabel: '推荐追问',
    stageSectionLabel: '剧本推进',
    advanceLabel: '推进诊断',
    resetLabel: '重置剧本',
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

const roleOptions: RoleOption[] = [
  { id: 'all', label: '全部' },
  { id: 'customer-service', label: '客服副驾' },
  { id: 'business-query', label: '业务查询' },
  { id: 'ops-diagnosis', label: '运维诊断' },
  { id: 'implementation', label: '实施交付' },
  { id: 'engineering', label: '研发排障' },
  { id: 'analytics', label: '运营分析' },
  { id: 'audit', label: '审计权限' },
]

const baseCases: CaseItem[] = [
  {
    id: 'case-001',
    roleId: 'customer-service',
    roleLabel: '客服坐席副驾',
    sceneLabel: '领导现场保障',
    sceneType: '复杂严重',
    title: '长江2号地图不显示',
    question: '为什么长江2号地图不显示',
    customerVoice: '领导现在就在现场看大屏，长江2号位置不对，最新告警点不开，视频也打不开。',
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
    quickPrompts: [
      { id: 'case-001-p1', label: '地图不显示是权限问题还是定位问题？', targetCaseId: 'case-001' },
      { id: 'case-001-p2', label: '领导现场为什么视频抓图会失败？', targetCaseId: 'case-002' },
      { id: 'case-001-p3', label: '一级告警为什么没有实时推送？', targetCaseId: 'case-004' },
    ],
  },
  {
    id: 'case-002',
    roleId: 'engineering',
    roleLabel: '研发排障助手',
    sceneLabel: '视频链路排障',
    sceneType: '复杂严重',
    title: '驾驶舱抓图失败',
    question: '驾驶舱抓图为什么失败',
    customerVoice: '开放接口返回 201，没有图片，驾驶舱抓图失败。',
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
    quickPrompts: [
      { id: 'case-002-p1', label: '为什么 shipName 校验通过仍然抓图失败？', targetCaseId: 'case-002' },
      { id: 'case-002-p2', label: '新项目上线时视频链路怎么体检？', targetCaseId: 'case-005' },
      { id: 'case-002-p3', label: '这个问题能不能转成客服阶段答复？', targetCaseId: 'case-001' },
    ],
  },
  {
    id: 'case-003',
    roleId: 'ops-diagnosis',
    roleLabel: '运维诊断助手',
    sceneLabel: '通知链路排障',
    sceneType: '标准排障',
    title: '账号过期提醒未收到',
    question: '为什么账号过期提醒未收到',
    customerVoice: '用户说账号快过期了，但一直没有收到提醒。',
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
    quickPrompts: [
      { id: 'case-003-p1', label: '是模板问题还是 MQTT 没推送？', targetCaseId: 'case-003' },
      { id: 'case-003-p2', label: '多个租户告警都不推送怎么办？', targetCaseId: 'case-004' },
      { id: 'case-003-p3', label: '越权情况下提醒数据会不会泄漏？', targetCaseId: 'case-008' },
    ],
  },
  {
    id: 'case-004',
    roleId: 'ops-diagnosis',
    roleLabel: '运维诊断助手',
    sceneLabel: '多租户消息故障',
    sceneType: '复杂严重',
    title: '一级告警推送异常',
    question: '为什么一级告警没有推送',
    customerVoice: '多个客户同时反馈告警没推送，手机端和大屏都没有刷新。',
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
    quickPrompts: [
      { id: 'case-004-p1', label: '是 MQTT 没发还是前端没收？', targetCaseId: 'case-004' },
      { id: 'case-004-p2', label: '升级后闭环率下滑和消息链路有关系吗？', targetCaseId: 'case-007' },
      { id: 'case-004-p3', label: '审计上怎么证明没有跨租户推送？', targetCaseId: 'case-008' },
    ],
  },
  {
    id: 'case-005',
    roleId: 'implementation',
    roleLabel: '实施交付顾问',
    sceneLabel: '上线前体检',
    sceneType: '上线保障',
    title: '上线前体检与验收保障',
    question: '为什么新项目上线演示链路跑不通',
    customerVoice: '上线当天地图、围栏、视频、巡查都要演示，但现在关键链路跑不通。',
    status: '处理中',
    statusTone: 'warning',
    date: '05-31 14:30',
    owner: '实施工程师',
    tenant: '新项目验收现场',
    priority: 'P2',
    caseCode: 'CASE-20240531-0043',
    questionTime: '14:31:44',
    aiTime: '14:31:47',
    confidence: '89%',
    responseDeadline: '当日验收前',
    severityLabel: '高',
    workOrderType: '上线保障工单',
    attachmentsSummary: '上线体检清单 / 设备绑定截图 / 视频通道截图',
    rootCause: '船舶未绑定定位设备且驾驶舱通道未完成配置，导致地图、告警、视频三条链路无法闭环。',
    impact: ['验收演示失败', '围栏告警无法触发', '视频证据缺失'],
    suggestions: ['补齐定位与视频通道绑定', '复核围栏与告警规则', '执行上线前九项体检清单'],
    steps: [
      { id: 's5-1', title: '检查船舶与定位绑定', detail: '发现 2 条演示船舶未绑定定位设备。', state: 'warning' },
      { id: 's5-2', title: '检查视频通道配置', detail: '驾驶舱通道未完成 GB 配置。', state: 'warning' },
      { id: 's5-3', title: '检查围栏与告警规则', detail: '围栏与一级告警规则未完成关联验证。', state: 'running' },
    ],
    evidence: [
      { id: 's5-ev1', label: '船舶资料', value: '资料完整', description: '演示船舶已建档', state: '正常', tone: 'success' },
      { id: 's5-ev2', label: '定位绑定', value: '2 条船舶未绑定', description: '地图展示链路不完整', state: '异常', tone: 'danger', highlight: true },
      { id: 's5-ev3', label: '视频通道', value: '驾驶舱通道缺失', description: 'GB28181 未完成接入', state: '异常', tone: 'warning' },
      { id: 's5-ev4', label: '围栏与告警', value: '待联调验证', description: '规则未完成验收前检查', state: '待检查', tone: 'info' },
    ],
    toolCalls: [
      { id: 'tool-9', label: '设备绑定检查', state: 'done' },
      { id: 'tool-10', label: '视频通道检查', state: 'running' },
      { id: 'tool-11', label: '上线体检清单生成', state: 'queued' },
    ],
    quickPrompts: [
      { id: 'case-005-p1', label: '上线前要先检查哪些链路？', targetCaseId: 'case-005' },
      { id: 'case-005-p2', label: '地图和视频都异常时先派给谁？', targetCaseId: 'case-001' },
      { id: 'case-005-p3', label: '怎么把这次问题沉淀成验收 SOP？', targetCaseId: 'case-007' },
    ],
  },
  {
    id: 'case-006',
    roleId: 'business-query',
    roleLabel: '业务查询助手',
    sceneLabel: '巡查争议复核',
    sceneType: '证据查询',
    title: '巡查漏检争议',
    question: '为什么巡查结果被判定漏检',
    customerVoice: '这个点到底有没有巡查？为什么系统判成漏检？',
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
    quickPrompts: [
      { id: 'case-006-p1', label: '昨天巡查有没有漏检？证据在哪？', targetCaseId: 'case-006' },
      { id: 'case-006-p2', label: '这类争议会不会影响月度复盘？', targetCaseId: 'case-007' },
      { id: 'case-006-p3', label: '如果客户现场质疑怎么快速答复？', targetCaseId: 'case-001' },
    ],
  },
  {
    id: 'case-007',
    roleId: 'analytics',
    roleLabel: '运营分析参谋',
    sceneLabel: '月度经营复盘',
    sceneType: '管理分析',
    title: '月度闭环率下滑复盘',
    question: '为什么本月告警闭环率明显下降',
    customerVoice: '为什么这个月告警很多、处理很慢、误报也高，下个月怎么改？',
    status: '处理中',
    statusTone: 'warning',
    date: '05-29 18:20',
    owner: '运营负责人',
    tenant: '集团运营中心',
    priority: 'P2',
    caseCode: 'CASE-20240529-0018',
    questionTime: '18:20:30',
    aiTime: '18:20:33',
    confidence: '84%',
    responseDeadline: '本周内复盘',
    severityLabel: '高',
    workOrderType: '复盘整改任务',
    attachmentsSummary: '月报截图 / 机构排名 / 误报规则比对',
    rootCause: '二级告警误报率上升叠加两个机构确认超时，导致整体闭环率明显下滑。',
    impact: ['管理层关注升级', '机构考核压力增大', '设备维护投入上升'],
    suggestions: ['调整误报规则阈值', '督办确认慢的机构', '优先维护证据缺失设备'],
    steps: [
      { id: 's7-1', title: '汇总告警统计', detail: '本月一级/二级告警与闭环率已汇总。', state: 'done' },
      { id: 's7-2', title: '比对误报规则', detail: '二级告警误报率较上月提升 14%。', state: 'warning' },
      { id: 's7-3', title: '输出整改建议', detail: '正在生成机构、设备、规则三类整改建议。', state: 'running' },
    ],
    evidence: [
      { id: 's7-ev1', label: '闭环率', value: '72%', description: '较上月下降 11%', state: '异常', tone: 'warning', highlight: true },
      { id: 's7-ev2', label: '误报率', value: '31%', description: '二级告警误报明显升高', state: '异常', tone: 'danger' },
      { id: 's7-ev3', label: '机构排名', value: '2 个机构确认超时', description: '集中在夜班处理链路', state: '异常', tone: 'warning' },
      { id: 's7-ev4', label: '设备证据', value: '17 台设备视频证据缺失', description: '建议优先维护', state: '受影响', tone: 'warning' },
    ],
    toolCalls: [
      { id: 'tool-12', label: '统计接口汇总', state: 'done' },
      { id: 'tool-13', label: '误报规则比对', state: 'running' },
      { id: 'tool-14', label: '整改建议生成', state: 'queued' },
    ],
    quickPrompts: [
      { id: 'case-007-p1', label: '为什么这个月闭环率下降这么多？', targetCaseId: 'case-007' },
      { id: 'case-007-p2', label: '误报率升高和设备链路有关系吗？', targetCaseId: 'case-004' },
      { id: 'case-007-p3', label: '这类数据能否直接生成月报建议？', targetCaseId: 'case-007' },
    ],
  },
  {
    id: 'case-008',
    roleId: 'audit',
    roleLabel: '审计与权限助手',
    sceneLabel: '越权审计证明',
    sceneType: '合规证明',
    title: '跨租户越权查询审计校验',
    question: 'AI 会不会查到其他租户的数据',
    customerVoice: '如果客服让 AI 查询某条船信息，会不会查到别的机构的数据？',
    status: '待确认',
    statusTone: 'danger',
    date: '05-28 10:05',
    owner: '安全管理员',
    tenant: '政企监管项目',
    priority: 'P2',
    caseCode: 'CASE-20240528-0007',
    questionTime: '10:05:12',
    aiTime: '10:05:15',
    confidence: '91%',
    responseDeadline: '采购答疑会前',
    severityLabel: '高',
    workOrderType: '合规审计任务',
    attachmentsSummary: '权限裁剪日志 / 脱敏规则 / 审计回放记录',
    rootCause: '当前查询请求已被权限网关裁剪，跨租户数据未放行，且敏感字段按规则脱敏。',
    impact: ['采购安全顾虑', '需要现场证明 AI 可控', '要求审计回放'],
    suggestions: ['展示权限继承链路', '导出脱敏审计日志', '演示高风险动作拒绝策略'],
    steps: [
      { id: 's8-1', title: '识别用户上下文', detail: '已识别当前租户、角色与授权船舶范围。', state: 'done' },
      { id: 's8-2', title: '检查 MCP 权限裁剪', detail: '查询仅放行授权范围内的船舶摘要。', state: 'running' },
      { id: 's8-3', title: '审计回放验证', detail: '等待导出审计日志与脱敏命中记录。', state: 'queued' },
    ],
    evidence: [
      { id: 's8-ev1', label: '当前租户', value: '政企监管项目', description: '已继承当前登录用户上下文', state: '正常', tone: 'success' },
      { id: 's8-ev2', label: '授权范围', value: '仅放行 6 条船舶摘要', description: '跨租户查询被拒绝', state: '已裁剪', tone: 'success', highlight: true },
      { id: 's8-ev3', label: '脱敏规则', value: '手机号 / token / 内部 URL 已脱敏', description: '命中 3 类敏感字段策略', state: '生效', tone: 'success' },
      { id: 's8-ev4', label: '审计日志', value: '可按用户与时间回放', description: '支持追责与证明', state: '待导出', tone: 'info' },
    ],
    toolCalls: [
      { id: 'tool-15', label: '用户上下文识别', state: 'done' },
      { id: 'tool-16', label: '权限裁剪检查', state: 'running' },
      { id: 'tool-17', label: '审计日志回放', state: 'queued' },
    ],
    quickPrompts: [
      { id: 'case-008-p1', label: 'AI 能不能查到别的机构的数据？', targetCaseId: 'case-008' },
      { id: 'case-008-p2', label: '高风险动作会不会被自动执行？', targetCaseId: 'case-008' },
      { id: 'case-008-p3', label: '采购方如何验证审计留痕？', targetCaseId: 'case-008' },
    ],
  },
]

const caseKeywordMap: Record<string, string[]> = {
  'case-001': ['地图', '船舶', '定位', '大屏', '最新告警', '点不开', '位置不对'],
  'case-002': ['抓图', '驾驶舱', '视频', '201', 'openId', 'shipName', 'gb'],
  'case-003': ['账号', '过期', '提醒', '短信', '模板', '补发'],
  'case-004': ['告警', '推送', 'mqtt', '订阅', '多租户', '手机端', '大屏', '没刷新'],
  'case-005': ['上线', '验收', '体检', '演示', '围栏', '通道', '绑定'],
  'case-006': ['巡查', '漏检', '打卡', '围栏', '争议', '证据'],
  'case-007': ['闭环率', '误报', '月度', '复盘', '整改', '处理慢', '经营'],
  'case-008': ['越权', '审计', '脱敏', '权限', '跨租户', '合规', '追责'],
}

const stageLabels = ['剧本预演', '并行诊断', '结论输出'] as const

function buildStageOptions(caseItem: CaseItem): CaseStage[] {
  return [
    {
      id: 'script',
      label: stageLabels[0],
      description: `按${caseItem.roleLabel}视角重放“${caseItem.sceneLabel}”的客户原话和初始证据。`,
    },
    {
      id: 'diagnosing',
      label: stageLabels[1],
      description: '并行查看步骤、工具调用、证据链和责任归属，形成阶段性结论。',
    },
    {
      id: 'conclusion',
      label: stageLabels[2],
      description: '输出处置建议、工单类型、责任人和响应时限，完成状态型 mock 闭环。',
    },
  ]
}

function projectSteps(steps: DiagnosisStep[], progress: number): DiagnosisStep[] {
  if (progress === 1) {
    return steps
  }

  if (progress === 0) {
    return steps.map((step, index) => ({
      ...step,
      state: (index === 0 ? 'running' : 'queued') as DiagnosisStep['state'],
    }))
  }

  return steps.map((step) => ({
    ...step,
    state: 'done',
  }))
}

function projectTools(toolCalls: ToolCall[], progress: number): ToolCall[] {
  if (progress === 1) {
    return toolCalls
  }

  if (progress === 0) {
    return toolCalls.map((item, index) => ({
      ...item,
      state: (index === 0 ? 'running' : 'queued') as ToolCall['state'],
    }))
  }

  return toolCalls.map((item) => ({
    ...item,
    state: 'done',
  }))
}

function projectEvidence(evidence: EvidenceNode[], progress: number): EvidenceNode[] {
  if (progress === 1) {
    return evidence
  }

  if (progress === 0) {
    return evidence.map((node, index) => {
      if (index === 0) {
        return {
          ...node,
          state: '已载入',
          tone: 'success',
          highlight: false,
        }
      }

      if (index === 1) {
        return {
          ...node,
          state: '核验中',
          tone: 'warning',
          highlight: true,
        }
      }

      return {
        ...node,
        state: '待检查',
        tone: 'info',
        highlight: false,
      }
    })
  }

  return evidence.map((node) => ({
    ...node,
    state: node.state === '待检查' ? '已复核' : node.state,
    tone: node.tone === 'info' ? 'success' : node.tone,
    highlight: node.tone === 'danger' || node.highlight,
  }))
}

function bumpConfidence(confidence: string) {
  const value = Number.parseInt(confidence.replace('%', ''), 10)

  if (Number.isNaN(value)) {
    return confidence
  }

  return `${Math.min(value + 4, 98)}%`
}

function projectCase(baseCase: CaseItem, progress: number, liveQuestion?: string): CaseItem {
  const question = liveQuestion?.trim() || baseCase.question

  if (progress === 1) {
    return {
      ...baseCase,
      question,
    }
  }

  if (progress === 0) {
    return {
      ...baseCase,
      question,
      status: '剧本待推演',
      statusTone: 'info',
      owner: 'AI 自动分诊',
      confidence: '68%',
      responseDeadline: '等待推进剧本',
      rootCause: `正在围绕“${baseCase.sceneLabel}”收集证据，准备给出${baseCase.roleLabel}视角结论。`,
      impact: baseCase.impact.slice(0, Math.max(1, Math.ceil(baseCase.impact.length / 2))),
      suggestions: [
        `按${baseCase.roleLabel}视角继续追问关键上下文`,
        `并行核查${baseCase.evidence.slice(0, 2).map((item) => item.label).join('、')}`,
        '生成阶段性答复与责任归属草案',
      ],
      steps: projectSteps(baseCase.steps, progress),
      evidence: projectEvidence(baseCase.evidence, progress),
      toolCalls: projectTools(baseCase.toolCalls, progress),
    }
  }

  const finalSuggestions = baseCase.suggestions.includes('已同步处置建议与工单草案')
    ? baseCase.suggestions
    : [...baseCase.suggestions, '已同步处置建议与工单草案']

  return {
    ...baseCase,
    question,
    status: '结论已输出',
    statusTone: 'success',
    confidence: bumpConfidence(baseCase.confidence),
    responseDeadline: '已生成责任建议与工单草案',
    suggestions: finalSuggestions,
    steps: projectSteps(baseCase.steps, progress),
    evidence: projectEvidence(baseCase.evidence, progress),
    toolCalls: projectTools(baseCase.toolCalls, progress),
  }
}

type DiagnosisState = {
  activeCaseId: string
  activeRoleId: string
  caseProgressById: Record<string, number>
  submittedCaseIds: Record<string, boolean>
  cases: CaseItem[]
  roles: RoleOption[]
  ui: DiagnosisUiConfig
  liveQuestion: string
  playbackPhase: number
  playbackRunId: number
  isPlaybackRunning: boolean
}

type DiagnosisSnapshot = {
  activeCaseId: string
  activeRoleId: string
  activeStageIndex: number
  activeQuestion: string
  assistantStatus: string
  hasActiveConversation: boolean
  isPlaybackRunning: boolean
  playbackPhase: number
  playbackRunId: number
  cases: CaseItem[]
  filteredCases: CaseItem[]
  roles: RoleOption[]
  activeCase: CaseItem
  activePrompts: SuggestedPrompt[]
  stageOptions: CaseStage[]
  ui: DiagnosisUiConfig
  selectCase: (caseId: string) => void
  selectPrompt: (caseId: string) => void
  selectRole: (roleId: string) => void
  submitQuestion: (question: string, preferredCaseId?: string) => void
  advancePlaybackPhase: () => void
  advanceCaseStage: () => void
  resetCaseStage: () => void
  finishPlayback: () => void
}

const state: DiagnosisState = {
  activeCaseId: baseCases[0].id,
  activeRoleId: 'all',
  caseProgressById: Object.fromEntries(baseCases.map((item) => [item.id, 0])),
  submittedCaseIds: Object.fromEntries(baseCases.map((item) => [item.id, false])),
  cases: baseCases,
  roles: roleOptions,
  ui: mockUi,
  liveQuestion: '',
  playbackPhase: 0,
  playbackRunId: 0,
  isPlaybackRunning: false,
}

const listeners = new Set<() => void>()

function emitChange() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getProjectedCases() {
  return state.cases.map((item) => {
    const liveQuestion = item.id === state.activeCaseId ? state.liveQuestion : ''

    return projectCase(item, state.caseProgressById[item.id] ?? 0, liveQuestion)
  })
}

function deriveAssistantStatus(stageIndex: number, playbackPhase: number, isPlaybackRunning: boolean, hasActiveConversation: boolean) {
  if (!hasActiveConversation) {
    return '已载入案例问题，点击发送后开始模拟诊断与结果生成。'
  }

  if (isPlaybackRunning && playbackPhase === 0) {
    return '正在接收问题并建立本次诊断上下文...'
  }

  if (isPlaybackRunning && playbackPhase === 1) {
    return '正在识别角色、匹配场景并装载初始证据...'
  }

  if (isPlaybackRunning && playbackPhase === 2) {
    return '正在并行分析证据链、工具调用和责任归属...'
  }

  if (isPlaybackRunning && playbackPhase >= 3) {
    return '正在汇总结论、工单建议与责任归属，请稍候...'
  }

  if (stageIndex >= 2) {
    return '已生成阶段性结论，正在同步工单建议与责任归属。'
  }

  return '输入预演问题后，系统会自动推进证据链与诊断结论。'
}

function normalizeText(value: string) {
  return value.trim().toLowerCase()
}

function pickCase(question: string, roleId: string, preferredCaseId?: string) {
  if (preferredCaseId) {
    return state.cases.find((item) => item.id === preferredCaseId) ?? state.cases[0]
  }

  const normalizedQuestion = normalizeText(question)
  const scopedCases = roleId === 'all' ? state.cases : state.cases.filter((item) => item.roleId === roleId)
  const candidates = scopedCases.length > 0 ? scopedCases : state.cases

  return (
    candidates
      .map((item) => {
        const keywords = caseKeywordMap[item.id] ?? []
        const corpus = normalizeText([
          item.title,
          item.question,
          item.customerVoice,
          item.sceneLabel,
          item.roleLabel,
          ...item.quickPrompts.map((prompt) => prompt.label),
        ].join(' '))

        let score = 0

        if (normalizedQuestion.length > 0 && corpus.includes(normalizedQuestion)) {
          score += 10
        }

        keywords.forEach((keyword) => {
          if (question.includes(keyword)) {
            score += 5
          }
        })

        ;['地图', '告警', '视频', '抓图', '提醒', '巡查', '上线', '验收', '闭环', '误报', '越权', '权限', '推送', '租户'].forEach((keyword) => {
          if (question.includes(keyword) && corpus.includes(keyword.toLowerCase())) {
            score += 1
          }
        })

        return { item, score }
      })
      .sort((left, right) => right.score - left.score)[0]?.item ?? state.cases[0]
  )
}

function createSnapshot(): DiagnosisSnapshot {
  const cases = getProjectedCases()
  const filteredCases = state.activeRoleId === 'all' ? cases : cases.filter((item) => item.roleId === state.activeRoleId)
  const activeCase =
    filteredCases.find((item) => item.id === state.activeCaseId) ??
    cases.find((item) => item.id === state.activeCaseId) ??
    filteredCases[0] ??
    cases[0]
  const activeStageIndex = state.caseProgressById[activeCase.id] ?? 0
  const hasActiveConversation = state.submittedCaseIds[activeCase.id] ?? false

  return {
    activeCaseId: state.activeCaseId,
    activeRoleId: state.activeRoleId,
    activeStageIndex,
    activeQuestion: hasActiveConversation ? state.liveQuestion || activeCase.question : '',
    assistantStatus: deriveAssistantStatus(activeStageIndex, state.playbackPhase, state.isPlaybackRunning, hasActiveConversation),
    hasActiveConversation,
    isPlaybackRunning: state.isPlaybackRunning,
    playbackPhase: state.playbackPhase,
    playbackRunId: state.playbackRunId,
    cases,
    filteredCases,
    roles: state.roles,
    activeCase,
    activePrompts: activeCase.quickPrompts,
    stageOptions: buildStageOptions(activeCase),
    ui: state.ui,
    selectCase,
    selectPrompt,
    selectRole,
    submitQuestion,
    advancePlaybackPhase,
    advanceCaseStage,
    resetCaseStage,
    finishPlayback,
  }
}

let snapshot = createSnapshot()

function selectCase(caseId: string) {
  if (state.activeCaseId === caseId) {
    return
  }

  state.activeCaseId = caseId
  state.caseProgressById[caseId] = 0
  state.submittedCaseIds[caseId] = false
  state.liveQuestion = ''
  state.playbackPhase = 0
  state.isPlaybackRunning = false
  state.playbackRunId += 1
  snapshot = createSnapshot()
  emitChange()
}

function selectPrompt(caseId: string) {
  state.activeCaseId = caseId
  state.caseProgressById[caseId] = 0
  state.submittedCaseIds[caseId] = false
  state.liveQuestion = ''
  state.playbackPhase = 0
  state.isPlaybackRunning = false
  state.playbackRunId += 1
  snapshot = createSnapshot()
  emitChange()
}

function selectRole(roleId: string) {
  if (state.activeRoleId === roleId) {
    return
  }

  state.activeRoleId = roleId
  state.liveQuestion = ''
  state.playbackPhase = 0
  state.isPlaybackRunning = false
  state.playbackRunId += 1

  if (roleId !== 'all') {
    const nextCase = state.cases.find((item) => item.roleId === roleId)

    if (nextCase) {
      state.activeCaseId = nextCase.id
      state.caseProgressById[nextCase.id] = 0
      state.submittedCaseIds[nextCase.id] = false
    }
  }

  snapshot = createSnapshot()
  emitChange()
}

function submitQuestion(question: string, preferredCaseId?: string) {
  const trimmedQuestion = question.trim()

  if (!trimmedQuestion) {
    return
  }

  const nextCase = pickCase(trimmedQuestion, state.activeRoleId, preferredCaseId)

  state.activeCaseId = nextCase.id

  if (state.activeRoleId === 'all') {
    state.activeRoleId = nextCase.roleId
  }

  state.liveQuestion = trimmedQuestion
  state.caseProgressById[nextCase.id] = 0
  state.submittedCaseIds[nextCase.id] = true
  state.playbackPhase = 0
  state.isPlaybackRunning = true
  state.playbackRunId += 1
  snapshot = createSnapshot()
  emitChange()
}

function advancePlaybackPhase() {
  state.playbackPhase = Math.min(state.playbackPhase + 1, 3)
  snapshot = createSnapshot()
  emitChange()
}

function advanceCaseStage() {
  const current = state.caseProgressById[state.activeCaseId] ?? 0
  state.caseProgressById[state.activeCaseId] = Math.min(current + 1, 2)

  if (state.caseProgressById[state.activeCaseId] >= 2) {
    state.isPlaybackRunning = false
  }

  snapshot = createSnapshot()
  emitChange()
}

function resetCaseStage() {
  state.caseProgressById[state.activeCaseId] = 0
  state.playbackPhase = 0
  state.isPlaybackRunning = false
  state.playbackRunId += 1
  snapshot = createSnapshot()
  emitChange()
}

function finishPlayback() {
  state.playbackPhase = 3
  state.isPlaybackRunning = false
  snapshot = createSnapshot()
  emitChange()
}

function getSnapshot() {
  return snapshot
}

export function useDiagnosisStore() {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}
