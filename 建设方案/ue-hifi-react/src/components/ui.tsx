import type { ReactNode } from 'react'
import { ArrowRight } from 'lucide-react'
import type { Tone } from '../data/site'

type GlassCardProps = {
  title: string
  subtitle?: string
  className?: string
  children: ReactNode
}

type MetricCardProps = {
  label: string
  value: string
  detail: string
  tone: Tone
}

type TonePillProps = {
  tone: Tone
  children: ReactNode
}

type ActionChipProps = {
  label: string
  detail: string
  tone: Tone
}

function joinClasses(...classNames: Array<string | undefined>) {
  return classNames.filter(Boolean).join(' ')
}

export function GlassCard({ title, subtitle, className, children }: GlassCardProps) {
  return (
    <section className={joinClasses('glass-card', className)}>
      <header className="glass-card__header">
        <div>
          <h3>{title}</h3>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
      </header>
      <div className="glass-card__body">{children}</div>
    </section>
  )
}

export function MetricCard({ label, value, detail, tone }: MetricCardProps) {
  return (
    <article className="metric-card" data-tone={tone}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  )
}

export function TonePill({ tone, children }: TonePillProps) {
  return (
    <span className="tone-pill" data-tone={tone}>
      {children}
    </span>
  )
}

export function ActionChip({ label, detail, tone }: ActionChipProps) {
  return (
    <button className="action-chip" data-tone={tone} type="button">
      <div>
        <strong>{label}</strong>
        <span>{detail}</span>
      </div>
      <ArrowRight size={16} />
    </button>
  )
}