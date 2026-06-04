import { Bot, Copy, ImagePlus, Maximize2, Paperclip, SendHorizontal, Trash2, UserRound } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import StepTimeline from '../../components/StepTimeline'
import GlowButton from '../../components/GlowButton'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function ChatPanel() {
  const { activeCase, ui } = useDiagnosisStore()

  return (
    <div className="chat-panel">
      <GlassCard className="chat-thread glow-border">
        <div className="chat-thread__toolbar">
          <div className="chat-thread__session">
            <span>
              {ui.chat.sessionLabel} {activeCase.caseCode}
            </span>
            <button type="button" aria-label={ui.chat.copyCaseCodeAriaLabel}>
              <Copy size={14} />
            </button>
          </div>
          <div className="chat-thread__toolbar-actions">
            <button type="button">
              <Trash2 size={15} />
              <span>{ui.chat.clearLabel}</span>
            </button>
            <button type="button" aria-label={ui.chat.expandAriaLabel}>
              <Maximize2 size={15} />
            </button>
          </div>
        </div>

        <div className="chat-thread__question-row">
          <div className="chat-thread__question glass-card">
            <p>{activeCase.question}</p>
            <span>{activeCase.questionTime}</span>
          </div>
          <div className="chat-thread__user-avatar">
            <UserRound size={18} />
          </div>
        </div>

        <div className="assistant-card glass-card">
          <div className="assistant-card__head">
            <div className="assistant-card__title">
              <div className="chat-avatar ai-avatar">
                <Bot size={16} />
              </div>
              <strong>{ui.chat.assistantTitle}</strong>
            </div>
            <span>{activeCase.aiTime}</span>
          </div>

          <p className="assistant-card__intro">{ui.chat.assistantIntro}</p>
          <StepTimeline detailActionLabel={ui.chat.detailActionLabel} stateText={ui.chat.stateText} steps={activeCase.steps} />

          <div className="tool-call-strip">
            <p>
              {ui.chat.toolCallPrefix} {activeCase.toolCalls.length} 个工具
            </p>
            <div className="tool-call-strip__items">
              {activeCase.toolCalls.map((item) => (
                <span className={`tool-pill tool-pill--${item.state}`} key={item.id}>
                  {item.label}
                </span>
              ))}
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard className="chat-composer">
        <div className="chat-composer__hint">{ui.chat.composerPlaceholder}</div>
        <div className="chat-composer__toolbar">
          <div className="chat-composer__tools">
            <button type="button" aria-label={ui.chat.attachmentAriaLabel}>
              <Paperclip size={16} />
            </button>
            <button type="button" aria-label={ui.chat.imageAriaLabel}>
              <ImagePlus size={16} />
            </button>
          </div>
          <GlowButton icon={<SendHorizontal size={16} />}>{ui.chat.sendLabel}</GlowButton>
        </div>
      </GlassCard>
    </div>
  )
}
