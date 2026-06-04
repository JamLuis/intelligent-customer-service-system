import { AlertTriangle, CircleCheckBig, ShieldAlert } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function ConclusionPanel() {
  const { activeCase, activeStageIndex, hasActiveConversation, isPlaybackRunning, playbackPhase, ui } = useDiagnosisStore()
  const rootCauseText =
    !hasActiveConversation
      ? '发送后开始生成根因分析。'
      : playbackPhase < 2
        ? `系统已接收“${activeCase.title}”，正在归集根因线索与责任链路。`
        : activeCase.rootCause
  const impactItems = !hasActiveConversation ? ['等待发送后生成影响范围。'] : activeStageIndex === 0 ? ['正在汇总影响范围...'] : activeCase.impact
  const suggestionItems =
    !hasActiveConversation
      ? ['等待发送后逐步生成处置建议。']
      : activeStageIndex === 0
      ? ['等待诊断推进后生成处置建议。']
      : activeStageIndex === 1
        ? [activeCase.suggestions[0], '其余建议与工单字段正在生成中。']
        : activeCase.suggestions
  const ownerText = !hasActiveConversation || activeStageIndex === 0 ? 'AI 自动分诊' : activeCase.owner
  const priorityText = !hasActiveConversation || activeStageIndex === 0 ? '--' : activeCase.priority
  const responseDeadlineText = !hasActiveConversation || activeStageIndex === 0 ? '等待结论输出' : activeCase.responseDeadline

  return (
    <GlassCard className="conclusion-panel">
      <div className="panel-title-row">
        <h3>{ui.conclusion.title}</h3>
      </div>

      <div className="conclusion-grid">
        <div className="conclusion-block glass-card">
          <div className="block-title">
            <ShieldAlert size={16} />
            <span>{ui.conclusion.rootCauseLabel}</span>
          </div>
          <strong>{rootCauseText}</strong>
          <div className="confidence-strip">
            <span>
              {ui.conclusion.confidenceLabel}
              {activeCase.confidence}
            </span>
            <div className="confidence-strip__bar">
              <i style={{ width: activeCase.confidence }} />
            </div>
          </div>
        </div>

        <div className="conclusion-block glass-card">
          <div className="block-title">
            <AlertTriangle size={16} />
            <span>{ui.conclusion.impactLabel}</span>
          </div>
          <ul>
            {impactItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <span className="conclusion-note">
            {ui.conclusion.impactLevelLabel}
            {activeCase.severityLabel}
          </span>
        </div>

        <div className="conclusion-block glass-card">
          <div className="block-title">
            <CircleCheckBig size={16} />
            <span>{ui.conclusion.suggestionLabel}</span>
          </div>
          <ol>
            {suggestionItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
        </div>

        <div className="conclusion-block glass-card">
          <div className="block-title">
            <CircleCheckBig size={16} />
            <span>{ui.conclusion.ownerLabel}</span>
          </div>
          <strong>{ownerText}</strong>
          <button className="inline-action" disabled={!hasActiveConversation || activeStageIndex < 2 || isPlaybackRunning} type="button">
            {ui.conclusion.reassignLabel}
          </button>
        </div>

        <div className="conclusion-block glass-card">
          <div className="block-title">
            <AlertTriangle size={16} />
            <span>{ui.conclusion.priorityLabel}</span>
          </div>
          <strong className="priority-value">{priorityText}</strong>
          <p className="conclusion-note">
            {ui.conclusion.responseDeadlineLabel}
            {responseDeadlineText}
          </p>
        </div>
      </div>
    </GlassCard>
  )
}
