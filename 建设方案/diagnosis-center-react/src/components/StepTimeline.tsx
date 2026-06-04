import { CircleCheckBig, LoaderCircle, OctagonAlert, SearchCheck } from 'lucide-react'
import type { DiagnosisStep } from '../stores/diagnosisStore'

type StepTimelineProps = {
  detailActionLabel: string
  stateText: Record<DiagnosisStep['state'], string>
  steps: DiagnosisStep[]
}

export default function StepTimeline({ detailActionLabel, stateText, steps }: StepTimelineProps) {
  return (
    <div className="step-timeline">
      {steps.map((step, index) => (
        <div className={`step-item step-item--${step.state}`} key={step.id}>
          <div className="step-item__order">{index + 1}</div>
          <div className="step-item__body">
            <div className="step-item__main">
              <div className="step-item__copy">
                <strong>{step.title}</strong>
                <span>{step.detail}</span>
              </div>
              <div className="step-item__meta">
                <button className="step-item__link" type="button">
                  {detailActionLabel}
                </button>
                <span className={`step-state step-state--${step.state}`}>
                  {step.state === 'done' && <CircleCheckBig size={14} />}
                  {step.state === 'running' && <LoaderCircle size={14} />}
                  {step.state === 'warning' && <OctagonAlert size={14} />}
                  {step.state === 'queued' && <SearchCheck size={14} />}
                  {stateText[step.state]}
                </span>
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
