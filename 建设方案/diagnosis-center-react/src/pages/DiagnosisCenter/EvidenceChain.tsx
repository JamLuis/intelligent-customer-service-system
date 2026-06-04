import { ArrowDown, FileSearch, Link2, Network, Radar, ShieldCheck, X } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import StatusTag from '../../components/StatusTag'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function EvidenceChain() {
  const { activeCase, ui } = useDiagnosisStore()

  return (
    <div className="evidence-panel">
      <GlassCard className="evidence-panel__header">
        <div className="evidence-panel__title">
          <h2>{ui.evidence.title}</h2>
        </div>
        <div className="evidence-panel__actions">
          <button type="button">{ui.evidence.collapseLabel}</button>
          <button type="button" aria-label={ui.evidence.closeAriaLabel}>
            <X size={14} />
          </button>
        </div>
      </GlassCard>

      <div className="evidence-body">
        <GlassCard className="evidence-tree glow-border">
          <div className="evidence-path-head">
            <span>{ui.evidence.pathTitle}</span>
            <button type="button">{ui.evidence.fullViewLabel}</button>
          </div>

          {activeCase.evidence.map((node, index) => (
            <div className="evidence-node-wrap" key={node.id}>
              <div className={`evidence-node${node.highlight ? ' is-highlight' : ''}`}>
                <div className="evidence-node__icon">
                  {index === 0 && <ShieldCheck size={16} />}
                  {index === 1 && <Radar size={16} />}
                  {index >= 2 && <Network size={16} />}
                </div>
                <div className="evidence-node__copy">
                  <span>{node.label}</span>
                  <strong>{node.value}</strong>
                  <small>{node.description}</small>
                </div>
                <StatusTag tone={node.tone}>{node.state}</StatusTag>
              </div>
              {index < activeCase.evidence.length - 1 && (
                <div className="evidence-arrow">
                  <ArrowDown size={14} />
                </div>
              )}
            </div>
          ))}
        </GlassCard>

        <div className="evidence-toolbar glass-card">
          <button type="button" aria-label={ui.evidence.relationAriaLabel}>
            <Link2 size={15} />
          </button>
          <button type="button" aria-label={ui.evidence.detailAriaLabel}>
            <FileSearch size={15} />
          </button>
        </div>
      </div>
    </div>
  )
}
