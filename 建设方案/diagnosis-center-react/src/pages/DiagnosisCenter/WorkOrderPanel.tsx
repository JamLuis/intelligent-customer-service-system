import { Download, FilePlus2, UserCog } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import GlowButton from '../../components/GlowButton'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function WorkOrderPanel() {
  const { activeCase, ui } = useDiagnosisStore()

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
          <strong>{activeCase.title}</strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.descriptionLabel}</span>
          <strong>{activeCase.question}</strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.typeLabel}</span>
          <strong>{activeCase.workOrderType}</strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.priorityLabel}</span>
          <strong>
            {activeCase.priority}
            <em>{activeCase.severityLabel}</em>
          </strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.ownerLabel}</span>
          <strong>
            <UserCog size={14} />
            {activeCase.owner}
          </strong>
        </div>
        <div className="workorder-field glass-card">
          <span>{ui.workOrder.attachmentsLabel}</span>
          <strong>{activeCase.attachmentsSummary}</strong>
        </div>
      </div>

      <div className="workorder-actions">
        <GlowButton icon={<FilePlus2 size={16} />}>{ui.workOrder.generateLabel}</GlowButton>
        <GlowButton icon={<Download size={16} />} kind="secondary">
          {ui.workOrder.exportLabel}
        </GlowButton>
      </div>
    </GlassCard>
  )
}
