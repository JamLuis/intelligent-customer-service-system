import type { ButtonHTMLAttributes, ReactNode } from 'react'

type GlowButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  icon?: ReactNode
  kind?: 'primary' | 'secondary'
}

export default function GlowButton({ children, className = '', icon, kind = 'primary', ...props }: GlowButtonProps) {
  return (
    <button className={`glow-button glow-button--${kind} ${className}`.trim()} type="button" {...props}>
      {icon}
      <span>{children}</span>
    </button>
  )
}
