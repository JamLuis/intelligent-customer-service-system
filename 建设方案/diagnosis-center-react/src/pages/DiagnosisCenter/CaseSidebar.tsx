import { ChevronLeft, ChevronRight, Clock3, Filter, Paperclip, Plus, Search, UserRound } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import StatusTag from '../../components/StatusTag'
import MetricCard from '../../components/MetricCard'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function CaseSidebar() {
  const { activeCaseId, cases, selectCase, ui } = useDiagnosisStore()

  return (
    <div className="case-sidebar">
      <GlassCard className="case-sidebar__header">
        <h2>{ui.caseSidebar.title}</h2>
        <button className="case-create-btn" type="button">
          <Plus size={16} />
          <span>{ui.caseSidebar.createLabel}</span>
        </button>
      </GlassCard>

      <div className="case-sidebar__metrics">
        {ui.caseSidebar.metrics.map((item) => (
          <MetricCard key={item.id} label={item.label} value={item.value} tone={item.tone} />
        ))}
      </div>

      <div className="case-sidebar__search-row">
        <div className="case-search glass-card">
          <Search size={15} />
          <span>{ui.caseSidebar.searchPlaceholder}</span>
        </div>
        <button className="icon-button" type="button" aria-label={ui.caseSidebar.filterAriaLabel}>
          <Filter size={16} />
        </button>
      </div>

      <div className="case-list">
        {cases.map((item) => {
          const active = item.id === activeCaseId

          return (
            <button
              className={`case-item glass-card${active ? ' is-active' : ''}`}
              key={item.id}
              onClick={() => selectCase(item.id)}
              type="button"
            >
              <div className="case-item__top">
                <strong>{item.title}</strong>
                <StatusTag tone={item.priority === 'P1' || item.priority === 'P2' ? 'warning' : 'info'}>{item.priority}</StatusTag>
              </div>
              <div className="case-item__meta">
                <span>
                  <span className={`status-dot status-dot--${item.statusTone}`} />
                  {item.status}
                </span>
                <span>
                  <Clock3 size={14} />
                  {item.date}
                </span>
                <span>
                  <UserRound size={14} />
                  {item.owner}
                </span>
                <span>
                  <Paperclip size={14} />
                </span>
              </div>
              <small>{item.tenant}</small>
            </button>
          )
        })}
      </div>

      <div className="case-sidebar__pager glass-card">
        {ui.caseSidebar.pager.map((item) => {
          if (item.kind === 'ellipsis') {
            return <span key={item.id}>{item.label}</span>
          }

          const isPrev = item.id === 'page-prev'
          const isNext = item.id === 'page-next'

          return (
            <button className={item.active ? 'is-active' : ''} key={item.id} type="button">
              {isPrev && <ChevronLeft size={14} />}
              {isNext && <ChevronRight size={14} />}
              {!isPrev && !isNext ? item.label : null}
            </button>
          )
        })}
      </div>
    </div>
  )
}
