import { PageShell } from '../components/page-shell'
import { GlassCard, TonePill } from '../components/ui'

export function SafetySupervisionPage() {
  return (
    <PageShell
      pageId="safety-supervision"
      eyebrow="16.3 面向安全监管人员"
      description="突出偏航热力图、水域和航线风险排行、AI 监管洞察以及巡检任务生成，贴合海事监管和公交安全中心的风险排查工作流。"
      metrics={[
        { label: '偏航告警', value: '64', detail: '近 7 日累计', tone: 'red' },
        { label: '高风险水域', value: '4 个', detail: '重点巡查区域', tone: 'orange' },
        { label: '离线设备', value: '9 台', detail: '摄像头与 CAN 为主', tone: 'orange' },
        { label: '自动巡检任务', value: '12', detail: '已生成待处理', tone: 'green' },
      ]}
      actions={[
        { label: '生成巡检任务', detail: 'POST /workorder/create', tone: 'blue' },
        { label: '筛选高风险水域', detail: '近 7 天 / 30 天', tone: 'orange' },
        { label: '导出风险排行', detail: '支持监管复盘', tone: 'green' },
      ]}
    >
      <div className="dashboard-grid">
        <GlassCard className="span-7" title="GIS 风险分析大屏" subtitle="偏航热力图与告警点叠加">
          <div className="mock-map mock-map--water">
            <div className="mock-map__glow mock-map__glow--water-a" />
            <div className="mock-map__glow mock-map__glow--water-b" />
            <span className="mock-map__label mock-map__label--a">长江镇江段 · 偏航 18 次</span>
            <span className="mock-map__label mock-map__label--b">南京港航线 · 风险 84</span>
          </div>
          <div className="legend-row">
            <TonePill tone="red">偏航热区</TonePill>
            <TonePill tone="orange">重点监管</TonePill>
            <TonePill tone="blue">常规通行</TonePill>
          </div>
        </GlassCard>

        <GlassCard className="span-5" title="风险区域排行" subtitle="水域、航线、船舶三类排名">
          <div className="rank-list">
            {[
              ['长江镇江段', '偏航 18 次'],
              ['镇江港-南京港', '风险分 84'],
              ['海巡1023', '告警等级 A'],
              ['第三船队', '离线设备 3 台'],
            ].map(([label, value]) => (
              <div className="rank-row" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard className="span-6" title="AI 监管洞察" subtitle="高风险区域识别与趋势预测">
          <div className="insight-list">
            <article className="insight-item">
              <strong>偏航集中于同一水域</strong>
              <p>长江镇江段在凌晨 2 点到 4 点的偏航频次显著高于其他区域。</p>
            </article>
            <article className="insight-item">
              <strong>摄像头离线与恶劣天气共振</strong>
              <p>第三运营分公司 3 台车辆连续 7 天摄像头离线，建议优先巡检。</p>
            </article>
          </div>
        </GlassCard>

        <GlassCard className="span-6" title="巡检任务生成" subtitle="任务优先级、负责人和状态跟踪">
          <div className="workorder-list">
            {[
              ['长江镇江段偏航巡检', 'P1 · 张海峰'],
              ['第三船队摄像头离线', 'P1 · 李广浩'],
              ['南京港航线复核', 'P2 · 王晨曦'],
            ].map(([label, value]) => (
              <div className="workorder-row" key={label}>
                <div>
                  <strong>{label}</strong>
                  <span>{value}</span>
                </div>
                <TonePill tone="green">待执行</TonePill>
              </div>
            ))}
          </div>
        </GlassCard>
      </div>
    </PageShell>
  )
}