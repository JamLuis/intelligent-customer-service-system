import { useEffect, useState, type CSSProperties, type ReactNode } from 'react'
import {
  Activity,
  BarChart3,
  Bell,
  FileStack,
  HelpCircle,
  LayoutDashboard,
  MessageSquareMore,
  Search,
  Settings,
  ShieldCheck,
  UserRound,
} from 'lucide-react'
import { useDiagnosisStore } from '../stores/diagnosisStore'

type DiagnosisLayoutProps = {
  sidebar: ReactNode
  chat: ReactNode
  evidence: ReactNode
  bottom: ReactNode
}

const DESIGN_WIDTH = 1600
const DESIGN_HEIGHT = 900

function getViewportScale() {
  if (typeof window === 'undefined') {
    return 1
  }

  const availableWidth = window.innerWidth - 8
  const availableHeight = window.innerHeight - 8

  return Math.min(availableWidth / DESIGN_WIDTH, availableHeight / DESIGN_HEIGHT, 1)
}

export default function DiagnosisLayout({ sidebar, chat, evidence, bottom }: DiagnosisLayoutProps) {
  const railItems = [LayoutDashboard, MessageSquareMore, FileStack, BarChart3, ShieldCheck, Settings]
  const { ui } = useDiagnosisStore()
  const [scale, setScale] = useState(getViewportScale)

  useEffect(() => {
    const handleResize = () => {
      setScale(getViewportScale())
    }

    handleResize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  const frameStyle: CSSProperties = {
    width: DESIGN_WIDTH * scale,
    height: DESIGN_HEIGHT * scale,
  }

  const shellStyle: CSSProperties = {
    width: DESIGN_WIDTH,
    height: DESIGN_HEIGHT,
    transform: `scale(${scale})`,
    transformOrigin: 'top left',
  }

  return (
    <div className="diagnosis-viewport">
      <div className="diagnosis-scale-frame" style={frameStyle}>
        <div className="diagnosis-app-shell" style={shellStyle}>
          <aside className="app-rail glass-card">
            <div className="app-rail__brand">
              <Activity size={20} />
            </div>
            <div className="app-rail__items">
              {railItems.map((Icon, index) => (
                <button className={`app-rail__item${index === 1 ? ' is-active' : ''}`} key={index} type="button">
                  <Icon size={17} />
                </button>
              ))}
            </div>
            <button className="app-rail__item" type="button">
              <HelpCircle size={17} />
            </button>
          </aside>

          <div className="workspace-shell glass-card glow-border">
            <header className="diagnosis-topbar">
              <div className="diagnosis-brand">
                <div className="diagnosis-brand__mark">
                  <Activity size={20} />
                </div>
                <div>
                  <h1>{ui.layout.brandTitle}</h1>
                </div>
                <span className="brand-chip">{ui.layout.badgeText}</span>
              </div>

              <nav className="top-nav glass-card">
                {ui.layout.navItems.map((item) => (
                  <button className={`top-nav__item${item.active ? ' is-active' : ''}`} key={item.id} type="button">
                    {item.label}
                  </button>
                ))}
              </nav>

              <div className="diagnosis-topbar__meta">
                <div className="diagnosis-search glass-card">
                  <Search size={16} />
                  <span>{ui.layout.searchPlaceholder}</span>
                </div>
                <button className="topbar-icon" type="button" aria-label="通知">
                  <Bell size={16} />
                  <span className="topbar-badge">{ui.layout.notificationCount}</span>
                </button>
                <div className="user-chip">
                  <div className="user-chip__avatar">
                    <UserRound size={16} />
                  </div>
                  <div>
                    <strong>{ui.layout.userName}</strong>
                    <span>{ui.layout.userRole}</span>
                  </div>
                </div>
              </div>
            </header>

            <main className="diagnosis-main-grid">
              <aside className="diagnosis-column diagnosis-column--sidebar">{sidebar}</aside>
              <section className="diagnosis-column diagnosis-column--chat">{chat}</section>
              <section className="diagnosis-column diagnosis-column--evidence">{evidence}</section>
              <section className="diagnosis-bottom">{bottom}</section>
            </main>

            <footer className="diagnosis-footer">
              <span>{ui.layout.footerVersion}</span>
              <div className="diagnosis-footer__meta">
                {ui.layout.footerMeta.map((item) => (
                  <span key={item}>{item}</span>
                ))}
              </div>
            </footer>
          </div>
        </div>
      </div>
    </div>
  )
}
