import { ArrowDown, FileSearch, Link2, Network, Radar, ShieldCheck, X } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import StatusTag from '../../components/StatusTag'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function EvidenceChain() {
  const { activeCase, activeStageIndex, hasActiveConversation, isPlaybackRunning, playbackPhase, ui } = useDiagnosisStore()
  const visibleCount = !hasActiveConversation ? 0 : playbackPhase === 0 ? 1 : playbackPhase === 1 ? Math.min(2, activeCase.evidence.length) : activeStageIndex === 1 ? Math.min(4, activeCase.evidence.length) : activeCase.evidence.length
  const visibleEvidence = activeCase.evidence.slice(0, visibleCount)
  const remainingCount = activeCase.evidence.length - visibleEvidence.length

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

          {visibleEvidence.map((node, index) => (
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
              {index < visibleEvidence.length - 1 && (
                <div className="evidence-arrow">
                  <ArrowDown size={14} />
                </div>
              )}
            </div>
          ))}

          {!hasActiveConversation ? (
            <div className="evidence-node-wrap">
              <div className="evidence-node evidence-node--placeholder">
                <div className="evidence-node__icon">
                  <Network size={16} />
                </div>
                <div className="evidence-node__copy">
                  <span>诊断证据链待启动</span>
                  <strong>点击发送后开始装载路径节点</strong>
                  <small>系统会按 agent 推理节奏逐步展开船舶、设备、数据与服务节点。</small>
                </div>
                <StatusTag tone="info">待启动</StatusTag>
              </div>
            </div>
          ) : null}

          {hasActiveConversation && remainingCount > 0 && (
            <div className="evidence-node-wrap">
              <div className="evidence-node evidence-node--placeholder">
                <div className="evidence-node__icon">
                  <Network size={16} />
                </div>
                <div className="evidence-node__copy">
                  <span>更多诊断节点</span>
                  <strong>剩余 {remainingCount} 个节点待展开</strong>
                  <small>{isPlaybackRunning ? '系统正在并行装载后续证据与关系链。' : '点击发送后会继续展开完整证据链。'}</small>
                </div>
                <StatusTag tone={activeStageIndex === 0 ? 'info' : 'warning'}>{activeStageIndex === 0 ? '分析中' : '待展开'}</StatusTag>
              </div>
            </div>
          )}
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
