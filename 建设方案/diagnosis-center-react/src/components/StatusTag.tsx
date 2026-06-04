import type { ReactNode } from 'react'

type StatusTone = 'info' | 'success' | 'warning' | 'danger'

type StatusTagProps = {
  children: ReactNode
  tone?: StatusTone
}

export default function StatusTag({ children, tone = 'info' }: StatusTagProps) {
  return <span className={`status-tag status-tag--${tone}`}>{children}</span>
}
