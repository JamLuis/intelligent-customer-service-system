import { PageShell } from '../components/page-shell'
import { GlassCard, TonePill } from '../components/ui'

export function DispatchCenterPage() {
  return (
    <PageShell
      pageId="dispatch-center"
      eyebrow="16.2 面向调度员"
      description="以实时车辆位置、状态联动、视频回放和调度建议为主线，重现调度人员在单车定位、异常确认和运力调整时的操作界面。"
      metrics={[
        { label: '在线车辆', value: '318', detail: '实时在线率 98.4%', tone: 'green' },
        { label: '异常车辆', value: '18', detail: '急刹或 CAN 异常', tone: 'red' },
        { label: '平均调度响应', value: '43s', detail: '较上周缩短 12s', tone: 'blue' },
        { label: '建议执行率', value: '74%', detail: 'AI 建议被采纳比例', tone: 'purple' },
      ]}
      actions={[
        { label: '刷新实时位置', detail: 'GET /vehicle/realtime/location', tone: 'blue' },
        { label: '调取视频回放', detail: 'GET /video/playback/query', tone: 'orange' },
        { label: '分析 CAN 状态', detail: 'GET /can/latest/data', tone: 'green' },
      ]}
    >
      <div className="dashboard-grid">
        <GlassCard className="span-7" title="GIS 实时车辆地图" subtitle="支持车牌搜索、定位、轨迹回放">
          <div className="search-row">
            <span className="search-chip">搜索车牌：苏L03215</span>
            <TonePill tone="blue">轨迹回放中</TonePill>
          </div>
          <div className="vehicle-map">
            <span className="vehicle-map__route vehicle-map__route--main" />
            <span className="vehicle-map__route vehicle-map__route--branch" />
            <span className="vehicle-point vehicle-point--selected">苏L03215</span>
            <span className="vehicle-point vehicle-point--warning">急刹告警</span>
            <span className="vehicle-point vehicle-point--normal">K18</span>
          </div>
        </GlassCard>

        <GlassCard className="span-5" title="实时状态卡片" subtitle="速度、方向、司机与所属线路">
          <div className="state-grid">
            {[
              ['车牌', '苏L03215'],
              ['速度', '42 km/h'],
              ['方向', '135°'],
              ['驾驶员', '王志远'],
              ['所属线路', 'K12 快速公交'],
            ].map(([label, value]) => (
              <div className="state-card" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard className="span-3" title="AI 分析面板" subtitle="急刹、ECU、ABS、CAN 状态">
          <div className="status-list">
            <div className="status-row"><span>近 24h 急刹</span><TonePill tone="red">12 次</TonePill></div>
            <div className="status-row"><span>ECU 状态</span><TonePill tone="green">在线</TonePill></div>
            <div className="status-row"><span>ABS 状态</span><TonePill tone="orange">波动</TonePill></div>
            <div className="status-row"><span>CAN 通信</span><TonePill tone="red">异常</TonePill></div>
          </div>
        </GlassCard>

        <GlassCard className="span-4" title="视频联动面板" subtitle="异常时刻自动回放前门视频">
          <div className="video-screen">
            <div className="video-screen__overlay">08:32:17 前门摄像头 · 急刹回放</div>
          </div>
          <div className="video-controls">
            <TonePill tone="blue">暂停</TonePill>
            <TonePill tone="green">慢放</TonePill>
            <TonePill tone="orange">快进</TonePill>
          </div>
        </GlassCard>

        <GlassCard className="span-5" title="调度建议面板" subtitle="车辆调整、路线优化和司机提醒">
          <div className="suggestion-list">
            <article className="suggestion-item">
              <strong>车辆调度调整</strong>
              <p>建议调入备用车替换苏L03215，避免高峰时段继续放大风险。</p>
            </article>
            <article className="suggestion-item">
              <strong>路线优化</strong>
              <p>建议避开镇江南站拥堵路段，临时缩短两站停靠时间。</p>
            </article>
            <article className="suggestion-item">
              <strong>司机提醒</strong>
              <p>向当前驾驶员推送疲劳驾驶提醒，并建议进站后交接班。</p>
            </article>
          </div>
        </GlassCard>
      </div>
    </PageShell>
  )
}