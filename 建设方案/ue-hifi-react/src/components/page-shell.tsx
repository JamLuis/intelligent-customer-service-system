import type { ReactNode } from 'react'
import { Bot, Sparkles } from 'lucide-react'
import { dashboardPages, getDashboardPage, type DashboardPageId, type Tone } from '../data/site'
import { ActionChip, MetricCard } from './ui'

type Metric = {
  label: string
  value: string
  detail: string
  tone: Tone
}

type Action = {
  label: string
  detail: string
  tone: Tone
}

type PageShellProps = {
  pageId: DashboardPageId
  eyebrow: string
  description: string
  metrics: Metric[]
  actions: Action[]
  children: ReactNode
  viewport?: 'desktop' | 'mobile'
  layout?: 'standard' | 'immersive'
}

export function PageShell({
  pageId,
  eyebrow,
  description,
  metrics,
  actions,
  children,
  viewport = 'desktop',
  layout = 'standard',
}: PageShellProps) {
  const currentPage = getDashboardPage(pageId)
  const immersive = layout === 'immersive'

  return (
    <div className={immersive ? 'page-shell page-shell--immersive' : 'page-shell'}>
      {!immersive && (
        <aside className="page-sidebar glass-panel">
          <a className="brand-link" href="./index.html">
            <div className="brand-orb">
              <Bot size={22} />
            </div>
            <div>
              <p className="eyebrow">Hi-Fi UE</p>
              <strong>智能客服系统</strong>
              <span>业务场景驾驶舱</span>
            </div>
          </a>

          <div className="sidebar-copy">
            <p>{currentPage.summary}</p>
          </div>

          <nav className="page-nav">
            {dashboardPages.map((page) => {
              const Icon = page.icon

              return (
                <a
                  className={page.id === pageId ? 'page-nav__item is-active' : 'page-nav__item'}
                  href={`./${page.fileName}`}
                  key={page.id}
                >
                  <span className="page-nav__icon">
                    <Icon size={16} />
                  </span>
                  <span className="page-nav__text">
                    <strong>{page.shortTitle}</strong>
                    <small>{page.title}</small>
                  </span>
                </a>
              )
            })}
          </nav>

          <div className="sidebar-note glass-card sidebar-note--compact">
            <div className="glass-card__header">
              <div>
                <h3>设计原则</h3>
                <p>遵循文档中的业务价值优先和分组件实现</p>
              </div>
            </div>
            <div className="glass-card__body sidebar-note__body">
              <span>
                <Sparkles size={14} /> 深色科技风 + Glassmorphism
              </span>
              <span>
                <Sparkles size={14} /> 24 栅格 + 独立页面入口
              </span>
              <span>
                <Sparkles size={14} /> 业务场景驱动而非抽象流程页
              </span>
            </div>
          </div>
        </aside>
      )}

      <main className={immersive ? 'page-content page-content--immersive' : 'page-content'}>
        {!immersive && (
          <>
            <header className="page-header glass-panel">
              <div>
                <p className="eyebrow">{eyebrow}</p>
                <h1>{currentPage.title}</h1>
                <p className="page-header__desc">{description}</p>
              </div>
              <div className="page-header__meta">
                <div>
                  <span>适用角色</span>
                  <strong>{currentPage.roles.join(' / ')}</strong>
                </div>
                <div>
                  <span>页面形态</span>
                  <strong>{viewport === 'mobile' ? '移动端 390×844' : '驾驶舱 1920×1080'}</strong>
                </div>
              </div>
            </header>

            <section className="metric-grid">
              {metrics.map((metric) => (
                <MetricCard key={metric.label} {...metric} />
              ))}
            </section>
          </>
        )}

        <section
          className={
            immersive
              ? 'stage-frame stage-frame--immersive'
              : viewport === 'mobile'
                ? 'stage-frame stage-frame--mobile glass-panel'
                : 'stage-frame glass-panel'
          }
        >
          {children}
        </section>

        {!immersive && (
          <section className="action-strip">
            {actions.map((action) => (
              <ActionChip key={action.label} {...action} />
            ))}
          </section>
        )}
      </main>
    </div>
  )
}
