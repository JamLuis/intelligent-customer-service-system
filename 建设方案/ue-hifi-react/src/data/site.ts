import {
  AlarmClock,
  Orbit,
  Route,
  ShieldCheck,
  Smartphone,
  Wrench,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export type Tone = 'blue' | 'green' | 'purple' | 'orange' | 'red'

export type DashboardPageId =
  | 'group-safety'
  | 'dispatch-center'
  | 'safety-supervision'
  | 'fault-diagnosis'
  | 'field-assistant'
  | 'digital-twin'

export type DashboardPageMeta = {
  id: DashboardPageId
  title: string
  shortTitle: string
  summary: string
  fileName: string
  roles: string[]
  icon: LucideIcon
}

export const dashboardPages: DashboardPageMeta[] = [
  {
    id: 'group-safety',
    title: '16.1 集团安全运营驾驶舱',
    shortTitle: '安全运营',
    summary: '面向集团监管领导，聚焦告警聚类、风险运营分析和 AI 主动预警。',
    fileName: 'group-safety.html',
    roles: ['集团监管中心', '安全总监', '运营总监'],
    icon: AlarmClock,
  },
  {
    id: 'dispatch-center',
    title: '16.2 智能调度中心',
    shortTitle: '智能调度',
    summary: '面向调度员，融合 GIS 实时位置、视频联动和自动故障定位。',
    fileName: 'dispatch-center.html',
    roles: ['公交调度', '船舶调度', '运营值班'],
    icon: Route,
  },
  {
    id: 'safety-supervision',
    title: '16.3 安全监管中心',
    shortTitle: '安全监管',
    summary: '面向安全监管人员，围绕偏航热力图、风险排行和自动巡检。',
    fileName: 'safety-supervision.html',
    roles: ['安全员', '海事监管', '公交安全中心'],
    icon: ShieldCheck,
  },
  {
    id: 'fault-diagnosis',
    title: '16.4 AI故障诊断中心',
    shortTitle: '故障诊断',
    summary: '面向维修部门，覆盖设备关系图谱、故障树和 AI 根因分析。',
    fileName: 'fault-diagnosis.html',
    roles: ['维修工程师', '机务', '设备运维'],
    icon: Wrench,
  },
  {
    id: 'field-assistant',
    title: '16.5 AI现场助手',
    shortTitle: '移动助手',
    summary: '面向司机、船长和现场操作员，提供语音问答、表单生成和 PDF 导出。',
    fileName: 'field-assistant.html',
    roles: ['司机', '船长', '现场操作员'],
    icon: Smartphone,
  },
  {
    id: 'digital-twin',
    title: '16.6 企业数字孪生运营中心',
    shortTitle: '数字孪生',
    summary: '面向领导层与监管部门，展示企业数字孪生、风险预测与自动调度。',
    fileName: 'digital-twin.html',
    roles: ['集团领导', '政府监管', '应急中心'],
    icon: Orbit,
  },
]

export function getDashboardPage(pageId: DashboardPageId) {
  return dashboardPages.find((page) => page.id === pageId) ?? dashboardPages[0]
}