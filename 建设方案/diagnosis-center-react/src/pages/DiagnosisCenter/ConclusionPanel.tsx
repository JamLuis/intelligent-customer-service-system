import { AlertTriangle, CircleCheckBig, ShieldAlert } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function ConclusionPanel() {
  const { activeCase, ui } = useDiagnosisStore()

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
          <strong>{activeCase.rootCause}</strong>
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
            {activeCase.impact.map((item) => (
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
            {activeCase.suggestions.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
        </div>

        <div className="conclusion-block glass-card">
          <div className="block-title">
            <CircleCheckBig size={16} />
            <span>{ui.conclusion.ownerLabel}</span>
          </div>
          <strong>{activeCase.owner}</strong>
          <button className="inline-action" type="button">
            {ui.conclusion.reassignLabel}
          </button>
        </div>

        <div className="conclusion-block glass-card">
          <div className="block-title">
            <AlertTriangle size={16} />
            <span>{ui.conclusion.priorityLabel}</span>
          </div>
          <strong className="priority-value">{activeCase.priority}</strong>
          <p className="conclusion-note">
            {ui.conclusion.responseDeadlineLabel}
            {activeCase.responseDeadline}
          </p>
        </div>
      </div>
    </GlassCard>
  )
}
