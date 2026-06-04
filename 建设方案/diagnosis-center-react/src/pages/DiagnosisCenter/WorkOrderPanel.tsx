import { Download, FilePlus2, UserCog } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import GlowButton from '../../components/GlowButton'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function WorkOrderPanel() {
  const { activeCase, activeQuestion, activeStageIndex, hasActiveConversation, isPlaybackRunning, ui } = useDiagnosisStore()
  const titleText = hasActiveConversation ? activeCase.title : '待发送后生成'
  const questionText = hasActiveConversation ? activeQuestion : '待发送后生成'
  const typeText = !hasActiveConversation || activeStageIndex === 0 ? '正在识别工单类型' : activeCase.workOrderType
  const ownerText = !hasActiveConversation || activeStageIndex === 0 ? '责任人生成中' : activeCase.owner
  const attachmentText = !hasActiveConversation || activeStageIndex === 0 ? `正在汇总 ${activeCase.evidence.slice(0, 2).map((item) => item.label).join(' / ')} 相关材料` : activeCase.attachmentsSummary
  const priorityText = !hasActiveConversation || activeStageIndex === 0 ? '--' : activeCase.priority
  const severityText = !hasActiveConversation || activeStageIndex === 0 ? '待分级' : activeCase.severityLabel

  return (
    <GlassCard className="workorder-panel">
      <div className="panel-title-row">
        <h3>{ui.workOrder.title}</h3>
        <button className="inline-link" type="button">
          {ui.workOrder.detailLinkLabel}
        </button>
      </div>

      <div className="workorder-fields">
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.titleLabel}</span>
          <strong>{titleText}</strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.descriptionLabel}</span>
          <strong>{questionText}</strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.typeLabel}</span>
          <strong>{typeText}</strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.priorityLabel}</span>
          <strong>
            {priorityText}
            <em>{severityText}</em>
          </strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.ownerLabel}</span>
          <strong>
            <UserCog size={14} />
            {ownerText}
          </strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.attachmentsLabel}</span>
          <strong>{attachmentText}</strong>
        </div>
      </div>

      <div className="workorder-actions">
        <GlowButton disabled={!hasActiveConversation || activeStageIndex < 2 || isPlaybackRunning} icon={<FilePlus2 size={16} />}>
          {activeStageIndex < 2 ? '生成中...' : ui.workOrder.generateLabel}
        </GlowButton>
        <GlowButton disabled={!hasActiveConversation || activeStageIndex < 2 || isPlaybackRunning} icon={<Download size={16} />} kind="secondary">
          {ui.workOrder.exportLabel}
        </GlowButton>
      </div>
    </GlassCard>
  )
}
