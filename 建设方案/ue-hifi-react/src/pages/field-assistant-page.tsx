import { PageShell } from '../components/page-shell'
import { GlassCard, TonePill } from '../components/ui'

export function FieldAssistantPage() {
  return (
    <PageShell
      pageId="field-assistant"
      eyebrow="16.5 面向船长 / 驾驶员 / 现场操作员"
      description="采用移动端 390×844 的单列交互布局，突出语音输入、AI 问答、告警查询、表单生成和 PDF 导出，符合现场快速操作特征。"
      viewport="mobile"
      metrics={[
        { label: '语音识别准确率', value: '97%', detail: '现场噪音下可用', tone: 'green' },
        { label: '平均响应时间', value: '1.3s', detail: '语音转文字 + 查询', tone: 'blue' },
        { label: '表单自动填充率', value: '88%', detail: '航次字段自动汇总', tone: 'purple' },
        { label: 'PDF 导出时长', value: '6s', detail: '含电子签名', tone: 'orange' },
      ]}
      actions={[
        { label: '告警查询', detail: 'GET /alarm/query', tone: 'blue' },
        { label: '自动生成业务表单', detail: '汇总 AIS / 水域 / 告警', tone: 'orange' },
        { label: '导出 PDF', detail: '含电子签名', tone: 'green' },
      ]}
    >
      <div className="mobile-stage">
        <div className="phone-frame">
          <div className="phone-topbar">
            <span>09:42</span>
            <TonePill tone="green">在线</TonePill>
          </div>

          <GlassCard title="语音输入区" subtitle="自然语言直接触发查询">
            <div className="voice-box">
              <div className="voice-orb">AI</div>
              <p>“查看今天所有偏航告警。”</p>
            </div>
          </GlassCard>

          <GlassCard title="AI 问答区" subtitle="关键字高亮与多轮对话">
            <div className="chat-thread">
              <div className="chat-thread__bubble chat-thread__bubble--user">帮我查今天的偏航告警。</div>
              <div className="chat-thread__bubble chat-thread__bubble--ai">
                已为你查询今日偏航告警，共 8 条，其中长江镇江段 3 条，建议优先查看高等级告警。
              </div>
            </div>
          </GlassCard>

          <GlassCard title="告警查询结果" subtitle="AIS 轨迹、告警时间、告警等级">
            <div className="result-list">
              {[
                ['AIS-7721', '08:32', '高'],
                ['AIS-7721', '09:05', '中'],
                ['海巡1023', '09:16', '高'],
              ].map(([name, time, level]) => (
                <div className="result-row" key={`${name}-${time}`}>
                  <div>
                    <strong>{name}</strong>
                    <span>{time}</span>
                  </div>
                  <TonePill tone={level === '高' ? 'red' : 'orange'}>{level}</TonePill>
                </div>
              ))}
            </div>
          </GlassCard>

          <GlassCard title="自动生成业务表单" subtitle="字段可编辑并支持签名">
            <div className="form-preview">
              <div className="form-field"><span>船舶</span><strong>海巡1023</strong></div>
              <div className="form-field"><span>航次</span><strong>镇江港 - 南京港</strong></div>
              <div className="form-field"><span>告警摘要</span><strong>今日偏航告警 8 条</strong></div>
              <div className="form-field"><span>签名</span><strong>已签署</strong></div>
            </div>
          </GlassCard>

          <div className="export-panel glass-panel">
            <strong>PDF 导出</strong>
            <span>已生成电子记录簿，可回传后台归档。</span>
          </div>
        </div>
      </div>
    </PageShell>
  )
}