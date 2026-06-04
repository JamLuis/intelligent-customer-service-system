import { ArrowRight, Network, Sparkles } from 'lucide-react'
import { dashboardPages } from '../data/site'
import { TonePill } from '../components/ui'

export function HomePage() {
  return (
    <div className="home-shell">
      <section className="home-hero glass-panel">
        <div className="home-hero__copy">
          <p className="eyebrow">AI Business Cockpit</p>
          <h1>智能客服系统高保真 UE 导航</h1>
          <p>
            本次重绘已按文档 16.1 到 16.6 的业务主题拆成独立页面入口，保留统一视觉规范、共享组件和多页面构建方式，不再使用单页面切换设计。
          </p>
          <div className="home-tag-row">
            <TonePill tone="blue">深色科技风</TonePill>
            <TonePill tone="purple">Glassmorphism</TonePill>
            <TonePill tone="green">多页面入口</TonePill>
          </div>
        </div>
        <div className="home-hero__panel">
          <div className="hero-stat glass-card">
            <div className="glass-card__header">
              <div>
                <h3>交付结构</h3>
                <p>页面、组件、样式、入口均已拆分</p>
              </div>
            </div>
            <div className="glass-card__body hero-stat__body">
              <div>
                <strong>06</strong>
                <span>业务主题页面</span>
              </div>
              <div>
                <strong>MPA</strong>
                <span>非单页面应用</span>
              </div>
              <div>
                <strong>组件化</strong>
                <span>共享外壳与卡片原子</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="home-grid">
        {dashboardPages.map((page) => {
          const Icon = page.icon

          return (
            <a className="home-card glass-panel" href={`./${page.fileName}`} key={page.id}>
              <div className="home-card__icon">
                <Icon size={22} />
              </div>
              <div className="home-card__copy">
                <h2>{page.title}</h2>
                <p>{page.summary}</p>
                <div className="home-card__roles">
                  {page.roles.map((role, index) => (
                    <TonePill key={`${page.id}-${role}`} tone={(['blue', 'green', 'purple'] as const)[index % 3]}>
                      {role}
                    </TonePill>
                  ))}
                </div>
              </div>
              <div className="home-card__arrow">
                <span>打开页面</span>
                <ArrowRight size={16} />
              </div>
            </a>
          )
        })}
      </section>

      <section className="home-bottom glass-panel">
        <div className="home-bottom__item">
          <Network size={18} />
          <div>
            <strong>主题与文档对齐</strong>
            <p>页面内容直接对应文档中的用户角色、问题、布局和 MCP 动作。</p>
          </div>
        </div>
        <div className="home-bottom__item">
          <Sparkles size={18} />
          <div>
            <strong>前端拆分原则</strong>
            <p>共享组件只承载通用外壳和视觉原子，不把所有页面塞进一个入口组件。</p>
          </div>
        </div>
      </section>
    </div>
  )
}