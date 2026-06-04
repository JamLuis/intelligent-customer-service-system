import { PageShell } from '../components/page-shell'
import { GlassCard, TonePill } from '../components/ui'

export function FaultDiagnosisPage() {
  return (
    <PageShell
      pageId="fault-diagnosis"
      eyebrow="16.4 面向维修部门"
      description="以设备关系图谱、故障树、AI 根因分析和工单闭环为核心，展示维修工程师如何快速定位问题并生成处置任务。"
      metrics={[
        { label: '待诊断设备', value: '21', detail: '高频异常设备', tone: 'orange' },
        { label: '疑似供电问题', value: '6', detail: '摄像头与 CAN 共振', tone: 'red' },
        { label: '根因置信度', value: '87%', detail: 'AI 模型综合评分', tone: 'green' },
        { label: '当日工单', value: '14', detail: '闭环率 79%', tone: 'blue' },
      ]}
      actions={[
        { label: '创建故障工单', detail: 'POST /workorder/create', tone: 'blue' },
        { label: '查看相似案例', detail: '历史案例检索', tone: 'orange' },
        { label: '输出维修建议', detail: '自动生成处置方案', tone: 'green' },
      ]}
    >
      <div className="dashboard-grid">
        <GlassCard className="span-4" title="设备关系图谱" subtitle="摄像头、交换机、电源、车辆关联">
          <div className="network-board">
            <span className="network-node network-node--top">CAM-3201</span>
            <span className="network-node network-node--left">交换机</span>
            <span className="network-node network-node--center">供电总线</span>
            <span className="network-node network-node--right">CAN-9907</span>
            <span className="network-node network-node--bottom">苏L03215</span>
            <span className="network-line network-line--v1" />
            <span className="network-line network-line--h1" />
            <span className="network-line network-line--h2" />
            <span className="network-line network-line--v2" />
          </div>
        </GlassCard>

        <GlassCard className="span-4" title="故障树分析" subtitle="从掉线现象向上追溯可能原因">
          <div className="tree-list">
            {[
              ['摄像头掉线', '100%'],
              ['交换机供电波动', '91%'],
              ['车辆电源不稳', '84%'],
              ['CAN 设备同步异常', '76%'],
            ].map(([label, value]) => (
              <div className="tree-row" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard className="span-4" title="AI 根因分析" subtitle="自动推断与操作建议">
          <div className="insight-item insight-item--single">
            <strong>疑似供电问题</strong>
            <p>CAM-3201 与 CAN-9907 同车同时出现异常，且发生在高负载时间段，建议优先检查电源和交换机。</p>
          </div>
          <div className="legend-row">
            <TonePill tone="red">高置信度</TonePill>
            <TonePill tone="orange">需人工复核</TonePill>
          </div>
        </GlassCard>

        <GlassCard className="span-7" title="历史关联故障" subtitle="相似案例与处理结果">
          <div className="history-list">
            {[
              ['2026-05-19', 'CAM-3201', '更换交换机电源后恢复'],
              ['2026-05-11', 'CAN-9907', '修复供电线束后恢复'],
              ['2026-04-28', 'CAM-3188', '更换摄像头模组'],
            ].map(([date, device, result]) => (
              <div className="history-row" key={`${date}-${device}`}>
                <span>{date}</span>
                <strong>{device}</strong>
                <small>{result}</small>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard className="span-5" title="工单操作区" subtitle="描述、优先级、负责人和状态">
          <div className="workorder-list">
            <div className="workorder-row">
              <div>
                <strong>CAM-3201 掉线故障排查</strong>
                <span>P1 · 负责人：刘工</span>
              </div>
              <TonePill tone="orange">处理中</TonePill>
            </div>
            <div className="workorder-row">
              <div>
                <strong>同车 CAN 设备复核</strong>
                <span>P1 · 负责人：杨工</span>
              </div>
              <TonePill tone="green">已派发</TonePill>
            </div>
          </div>
        </GlassCard>
      </div>
    </PageShell>
  )
}