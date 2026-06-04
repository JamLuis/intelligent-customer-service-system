type MetricCardProps = {
  label: string
  value: string
  tone?: 'blue' | 'warning' | 'success'
}

export default function MetricCard({ label, value, tone = 'blue' }: MetricCardProps) {
  return (
    <div className={`metric-card metric-card--${tone} glass-card`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}
