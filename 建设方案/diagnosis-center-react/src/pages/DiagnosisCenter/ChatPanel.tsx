import { useEffect, useState } from 'react'
import { Bot, Copy, ImagePlus, Maximize2, Paperclip, RotateCcw, SendHorizontal, Sparkles, Trash2, UserRound, Wand2 } from 'lucide-react'
import GlassCard from '../../components/GlassCard'
import StepTimeline from '../../components/StepTimeline'
import GlowButton from '../../components/GlowButton'
import { useDiagnosisStore } from '../../stores/diagnosisStore'

export default function ChatPanel() {
  const {
    activeCase,
    activePrompts,
    activeQuestion,
    activeStageIndex,
    advancePlaybackPhase,
    advanceCaseStage,
    assistantStatus,
    finishPlayback,
    hasActiveConversation,
    isPlaybackRunning,
    playbackPhase,
    playbackRunId,
    resetCaseStage,
    stageOptions,
    submitQuestion,
    ui,
  } = useDiagnosisStore()
  const [draftQuestion, setDraftQuestion] = useState(activeCase.question)

  const playbackFeed = [
    {
      id: 'feed-1',
      title: '锁定案例与原始问题',
      detail: `已载入“${activeCase.title}”的客户原话与预设问题，等待开始预演。`,
    },
    {
      id: 'feed-2',
      title: '并行装载证据节点',
      detail: `正在串联 ${activeCase.evidence.slice(0, 3).map((item) => item.label).join('、')} 等关键证据。`,
    },
    {
      id: 'feed-3',
      title: '分析工具调用结果',
      detail: `联动 ${activeCase.toolCalls.map((item) => item.label).join('、')}，判断责任归属与异常位置。`,
    },
    {
      id: 'feed-4',
      title: '输出结论与工单草案',
      detail: '生成根因、处置建议、责任人和工单字段，形成可展示的闭环结果。',
    },
  ]

  useEffect(() => {
    setDraftQuestion(activeCase.question)
  }, [activeCase.id, activeCase.question])

  useEffect(() => {
    if (!isPlaybackRunning) {
      return
    }

    const firstTimer = window.setTimeout(() => {
      advancePlaybackPhase()
    }, 600)

    const secondTimer = window.setTimeout(() => {
      advancePlaybackPhase()
      advanceCaseStage()
    }, 1800)

    const thirdTimer = window.setTimeout(() => {
      advancePlaybackPhase()
      advanceCaseStage()
      finishPlayback()
    }, 3600)

    return () => {
      window.clearTimeout(firstTimer)
      window.clearTimeout(secondTimer)
      window.clearTimeout(thirdTimer)
    }
  }, [advanceCaseStage, advancePlaybackPhase, finishPlayback, isPlaybackRunning, playbackRunId])

  const handleSubmit = () => {
    submitQuestion(draftQuestion, activeCase.id)
  }

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

        <div className="chat-scene-banner glass-card">
          <div className="chat-scene-banner__meta">
            <span className="section-kicker">{ui.chat.roleLabel}</span>
            <strong>{activeCase.roleLabel}</strong>
          </div>
          <div className="chat-scene-banner__body">
            <div>
              <span className="section-kicker">{ui.chat.sceneLabel}</span>
              <strong>{activeCase.sceneLabel}</strong>
            </div>
            <span className="chat-scene-banner__type">{activeCase.sceneType}</span>
          </div>
          <div className="chat-scene-banner__voice">
            <span>{ui.chat.customerVoiceLabel}</span>
            <p>{activeCase.customerVoice}</p>
          </div>
        </div>

        {hasActiveConversation ? (
          <div className="chat-thread__question-row">
            <div className="chat-thread__question glass-card">
              <p>{activeQuestion}</p>
              <span>{activeCase.questionTime}</span>
            </div>
            <div className="chat-thread__user-avatar">
              <UserRound size={18} />
            </div>
          </div>
        ) : null}

        {hasActiveConversation ? (
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

            <p className="assistant-card__intro">{assistantStatus || ui.chat.assistantIntro}</p>

            {playbackPhase >= 1 ? (
              <div className="chat-prompt-section">
                <div className="chat-prompt-section__head">
                  <Sparkles size={14} />
                  <span>{ui.chat.promptSectionLabel}</span>
                </div>
                <div className="chat-prompt-list">
                  {activePrompts.map((item) => (
                    <button className="chat-prompt-chip" key={item.id} onClick={() => submitQuestion(item.label, item.targetCaseId)} type="button">
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="assistant-thinking glass-card">
                <span className="assistant-thinking__label">AI 正在思考</span>
                <div className="assistant-thinking__dots">
                  <i />
                  <i />
                  <i />
                </div>
              </div>
            )}

            {playbackPhase >= 1 ? (
              <div className="chat-stage-section glass-card">
                <div className="chat-stage-section__head">
                  <div>
                    <span className="section-kicker">{ui.chat.stageSectionLabel}</span>
                    <strong>{stageOptions[activeStageIndex]?.label}</strong>
                  </div>
                  <p>{stageOptions[activeStageIndex]?.description}</p>
                </div>
                <div className="chat-stage-strip">
                  <div className="chat-stage-strip__list">
                    {stageOptions.map((item, index) => (
                      <span className={`chat-stage-pill${index === activeStageIndex ? ' is-active' : ''}`} key={item.id}>
                        {item.label}
                      </span>
                    ))}
                  </div>
                  <div className="chat-stage-strip__actions">
                    <button type="button" onClick={advanceCaseStage}>
                      <Wand2 size={14} />
                      <span>{ui.chat.advanceLabel}</span>
                    </button>
                    <button type="button" onClick={resetCaseStage}>
                      <RotateCcw size={14} />
                      <span>{ui.chat.resetLabel}</span>
                    </button>
                  </div>
                </div>

                <div className="playback-feed">
                  {playbackFeed.map((item, index) => {
                    let stateClass = 'is-queued'
                    let stateLabel = '待执行'

                    if (index < playbackPhase) {
                      stateClass = 'is-done'
                      stateLabel = '已完成'
                    } else if (index === playbackPhase && isPlaybackRunning) {
                      stateClass = 'is-running'
                      stateLabel = '处理中'
                    } else if (!isPlaybackRunning && activeStageIndex >= 2) {
                      stateClass = 'is-done'
                      stateLabel = '已完成'
                    }

                    return (
                      <div className={`playback-feed__item ${stateClass}`.trim()} key={item.id}>
                        <div className="playback-feed__meta">
                          <strong>{item.title}</strong>
                          <span>{stateLabel}</span>
                        </div>
                        <p>{item.detail}</p>
                      </div>
                    )
                  })}
                </div>
              </div>
            ) : null}

            {playbackPhase >= 2 ? <StepTimeline detailActionLabel={ui.chat.detailActionLabel} stateText={ui.chat.stateText} steps={activeCase.steps} /> : null}

            {playbackPhase >= 2 ? (
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
            ) : null}
          </div>
        ) : (
          <div className="assistant-idle glass-card">
            <div className="assistant-idle__title">
              <Bot size={16} />
              <strong>{ui.chat.assistantTitle}</strong>
            </div>
            <p>问题已按当前案例预填。点击发送后，系统将按 agent 方式逐步输出对话、证据链和结论结果。</p>
          </div>
        )}
      </GlassCard>

      <GlassCard className="chat-composer">
        <input
          className="chat-composer__input"
          onChange={(event) => setDraftQuestion(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              handleSubmit()
            }
          }}
          placeholder={ui.chat.composerPlaceholder}
          type="text"
          value={draftQuestion}
        />
        <div className="chat-composer__hint">发送后会自动按当前角色预演问题，并推动诊断链路、分析结论和工单信息联动变化。</div>
        <div className="chat-composer__toolbar">
          <div className="chat-composer__tools">
            <button type="button" aria-label={ui.chat.attachmentAriaLabel}>
              <Paperclip size={16} />
            </button>
            <button type="button" aria-label={ui.chat.imageAriaLabel}>
              <ImagePlus size={16} />
            </button>
          </div>
          <GlowButton disabled={!draftQuestion.trim()} icon={<SendHorizontal size={16} />} onClick={handleSubmit}>
            {isPlaybackRunning ? '预演中...' : ui.chat.sendLabel}
          </GlowButton>
        </div>
      </GlassCard>
    </div>
  )
}
