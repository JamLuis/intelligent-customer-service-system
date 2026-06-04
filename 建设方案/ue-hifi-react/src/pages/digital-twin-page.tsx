import { PageShell } from '../components/page-shell'
import { GlassCard, TonePill } from '../components/ui'

export function DigitalTwinPage() {
  return (
    <PageShell
      pageId="digital-twin"
      eyebrow="16.6 面向领导层与监管部门"
      description="作为汇报收束页，集中展示企业数字孪生、关系图谱、风险预测与自动调度建议，体现系统向企业级 AI 运营中枢演进的方向。"
      metrics={[
        { label: '全局告警', value: '1,284', detail: '集团级实时汇总', tone: 'red' },
        { label: '在线率', value: '98.7%', detail: '车辆 / 设备 / 船舶', tone: 'green' },
        { label: '未来 7 天风险指数', value: '0.81', detail: '较本周抬升', tone: 'orange' },
        { label: '自动调度建议', value: '11 条', detail: '可审批后执行', tone: 'blue' },
      ]}
      actions={[
        { label: '执行调度建议', detail: 'POST /dispatch/changeVehicle', tone: 'blue' },
        { label: '查看高风险对象', detail: '车辆 / 设备 / 人员', tone: 'orange' },
        { label: '导出运营总览', detail: '用于领导汇报', tone: 'green' },
      ]}
    >
      <div className="dashboard-grid">
        <GlassCard className="span-5" title="数字孪生主视图区" subtitle="资产、告警和运行状态 3D 叠加">
          <div className="twin-hero">
            <div className="twin-hero__core">Twin Core</div>
            <span className="twin-hero__ring twin-hero__ring--outer" />
            <span className="twin-hero__ring twin-hero__ring--inner" />
          </div>
        </GlassCard>

        <GlassCard className="span-4" title="企业关系图谱" subtitle="集团、分公司、线路、车辆、设备、人员">
          <div className="network-board network-board--dense">
            <span className="network-node network-node--top">集团</span>
            <span className="network-node network-node--left">分公司</span>
            <span className="network-node network-node--center">线路 / 航线</span>
            <span className="network-node network-node--right">设备</span>
            <span className="network-node network-node--bottom">车辆 / 人员</span>
            <span className="network-line network-line--v1" />
            <span className="network-line network-line--h1" />
            <span className="network-line network-line--h2" />
            <span className="network-line network-line--v2" />
          </div>
        </GlassCard>

        <GlassCard className="span-3" title="高风险对象区" subtitle="风险最高的对象列表">
          <div className="rank-list">
            {[
              ['K12 线路', '风险 92'],
              ['苏L03215', '告警 18'],
              ['CAM-3201', '离线 7 天'],
              ['夜班司机组', '疲劳指数 0.78'],
            ].map(([label, value]) => (
              <div className="rank-row" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard className="span-6" title="AI 预测区" subtitle="事故预测、趋势分析和建议">
          <div className="forecast-chart">
            {[42, 46, 55, 58, 62, 74, 81].map((value, index) => (
              <div className="forecast-chart__item" key={index}>
                <span style={{ height: `${value}%` }} />
                <small>D{index + 1}</small>
              </div>
            ))}
          </div>
          <div className="insight-item insight-item--single">
            <strong>事故风险上升</strong>
            <p>夜间急刹、疲劳驾驶、超速和制动异常在 K12 线路同时上升，建议临时更换车辆并调整班次。</p>
          </div>
        </GlassCard>

        <GlassCard className="span-6" title="自动调度建议区" subtitle="资源配置与效果预测">
          <div className="decision-grid">
            <article className="decision-card">
              <strong>临时更换车辆</strong>
              <p>将苏L03215 替换为备用车，预计风险下降 23%。</p>
              <span className="endpoint-tag">/dispatch/changeVehicle</span>
            </article>
            <article className="decision-card">
              <strong>调整班次节奏</strong>
              <p>缩短夜间连续发车密度，预计疲劳驾驶事件下降 17%。</p>
              <span className="endpoint-tag">审批后执行</span>
            </article>
          </div>
          <div className="legend-row">
            <TonePill tone="green">可执行</TonePill>
            <TonePill tone="orange">待审批</TonePill>
            <TonePill tone="blue">效果预测已生成</TonePill>
          </div>
        </GlassCard>
      </div>
    </PageShell>
  )
}