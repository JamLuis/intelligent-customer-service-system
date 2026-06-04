import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { CesiumMap } from '../components/cesium-map'
import { PageShell } from '../components/page-shell'
import { TonePill } from '../components/ui'
import { AlertTriangle, EyeOff, Grip, LayoutPanelTop, Map, Mic, PanelRightOpen, ShieldAlert, X } from 'lucide-react'
import {
  groupSafetyAlerts,
  groupSafetyHeatZones,
  groupSafetyPois,
  groupSafetyStations,
  groupSafetyVehicles,
  loadZhenjiangBusRoutes,
  type BusRouteFeature,
  type GeoVehicle,
} from '../data/group-safety-map'

type FloatingPanelKey = 'alerts' | 'ai' | 'legend'

type FloatingPanelState = {
  x: number
  y: number
  hidden: boolean
}

const defaultPanels: Record<FloatingPanelKey, FloatingPanelState> = {
  alerts: { x: 0, y: 0, hidden: false },
  ai: { x: 0, y: 332, hidden: false },
  legend: { x: 0, y: 712, hidden: false },
}

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(' ')
}

type FloatingPanelProps = {
  id: FloatingPanelKey
  title: string
  subtitle: string
  icon: typeof AlertTriangle
  badge?: string
  state: FloatingPanelState
  onToggleHidden: (id: FloatingPanelKey) => void
  onPointerStart: (id: FloatingPanelKey, event: ReactPointerEvent<HTMLElement>) => void
  children: ReactNode
}

function FloatingPanel({
  id,
  title,
  subtitle,
  icon: Icon,
  badge,
  state,
  onToggleHidden,
  onPointerStart,
  children,
}: FloatingPanelProps) {
  return (
    <section
      className={joinClasses('floating-panel glass-card', state.hidden && 'is-hidden')}
      style={{ transform: `translate3d(${state.x}px, ${state.y}px, 0)` }}
    >
      <header
        className="floating-panel__header"
        onPointerDown={(event) => onPointerStart(id, event)}
      >
        <div className="floating-panel__title">
          <span className="floating-panel__grip">
            <Grip size={14} />
          </span>
          <div>
            <h3>
              <Icon size={14} />
              {title}
            </h3>
            <p>{subtitle}</p>
          </div>
        </div>
        <div className="floating-panel__actions">
          {badge ? <span className="alert-count-badge">{badge}</span> : null}
          <button
            className="floating-panel__icon"
            type="button"
            aria-label={`隐藏${title}`}
            onClick={() => onToggleHidden(id)}
          >
            <X size={14} />
          </button>
        </div>
      </header>
      <div className="floating-panel__body">{children}</div>
    </section>
  )
}

export function GroupSafetyPage() {
  const [routes, setRoutes] = useState<BusRouteFeature[]>([])
  const [isLoadingRoutes, setIsLoadingRoutes] = useState(true)
  const [routeError, setRouteError] = useState<string | null>(null)
  const [focusedVehicleId, setFocusedVehicleId] = useState<string | null>(null)
  const [selectedVehicle, setSelectedVehicle] = useState<GeoVehicle | null>(null)
  const [topDockHidden, setTopDockHidden] = useState(false)
  const [floatingPanels, setFloatingPanels] = useState(defaultPanels)
  const dragStateRef = useRef<{
    id: FloatingPanelKey
    pointerId: number
    startX: number
    startY: number
    originX: number
    originY: number
  } | null>(null)

  useEffect(() => {
    let alive = true

    void loadZhenjiangBusRoutes()
      .then((features) => {
        if (!alive) {
          return
        }

        setRoutes(features)
        setRouteError(null)
      })
      .catch((error: unknown) => {
        if (!alive) {
          return
        }

        const message = error instanceof Error ? error.message : '线路数据加载失败'
        setRouteError(message)
      })
      .finally(() => {
        if (alive) {
          setIsLoadingRoutes(false)
        }
      })

    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    function handlePointerMove(event: PointerEvent) {
      const activeDrag = dragStateRef.current

      if (!activeDrag) {
        return
      }

      const nextX = activeDrag.originX + (event.clientX - activeDrag.startX)
      const nextY = activeDrag.originY + (event.clientY - activeDrag.startY)

      setFloatingPanels((current) => ({
        ...current,
        [activeDrag.id]: {
          ...current[activeDrag.id],
          x: nextX,
          y: nextY,
        },
      }))
    }

    function handlePointerUp(event: PointerEvent) {
      if (!dragStateRef.current || dragStateRef.current.pointerId !== event.pointerId) {
        return
      }

      dragStateRef.current = null
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)

    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [])

  const alertCount = groupSafetyAlerts.length
  const hiddenPanels = (Object.keys(floatingPanels) as FloatingPanelKey[]).filter((key) => floatingPanels[key].hidden)

  function handlePanelPointerStart(id: FloatingPanelKey, event: ReactPointerEvent<HTMLElement>) {
    if ((event.target as HTMLElement).closest('button')) {
      return
    }

    dragStateRef.current = {
      id,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: floatingPanels[id].x,
      originY: floatingPanels[id].y,
    }
  }

  function togglePanelHidden(id: FloatingPanelKey) {
    setFloatingPanels((current) => ({
      ...current,
      [id]: {
        ...current[id],
        hidden: !current[id].hidden,
      },
    }))
  }

  return (
    <PageShell
      pageId="group-safety"
      eyebrow="16.1 面向集团监管领导"
      description="基于 GraphRAG + 数字孪生构建集团安全运营全景，通过地图仿真、热力叠加、车辆状态可视化与 AI 语音交互，实现风险监控从图表到真实世界模型的升级。"
      metrics={[
        { label: '今日告警', value: '120', detail: '同比 +12%', tone: 'red' },
        { label: '在运车辆', value: '1,247', detail: '覆盖率 98.3%', tone: 'green' },
        { label: '高风险线路', value: '3 条', detail: '19路 / 24路 / 81路', tone: 'orange' },
        { label: '事故风险指数', value: '0.78', detail: '较昨日 ↓ 0.06', tone: 'green' },
      ]}
      actions={[
        { label: '创建专项检查工单', detail: 'POST /workorder/create', tone: 'blue' },
        { label: '调整排班计划', detail: '优化高风险线路运力', tone: 'orange' },
        { label: '生成维修建议', detail: '输出维修方案列表', tone: 'green' },
      ]}
      layout="immersive"
    >
      <div className="twin-canvas twin-canvas--immersive">
        <div className="twin-map twin-map--immersive">
          <CesiumMap
            routes={routes}
            vehicles={groupSafetyVehicles}
            stations={groupSafetyStations}
            pois={groupSafetyPois}
            heatZones={groupSafetyHeatZones}
            focusedVehicleId={focusedVehicleId}
            isLoading={isLoadingRoutes}
            errorMessage={routeError}
            onVehicleSelect={setSelectedVehicle}
          />

          <div className="immersive-controls">
            <a className="immersive-controls__back" href="./index.html">
              <Map size={14} />
              返回总览
            </a>
            <div className="immersive-controls__toggles">
              <button
                className="immersive-toggle"
                type="button"
                onClick={() => setTopDockHidden((current) => !current)}
              >
                {topDockHidden ? <LayoutPanelTop size={14} /> : <EyeOff size={14} />}
                {topDockHidden ? '显示顶部数据' : '隐藏顶部数据'}
              </button>
              {hiddenPanels.map((panelKey) => (
                <button
                  key={panelKey}
                  className="immersive-toggle"
                  type="button"
                  onClick={() => togglePanelHidden(panelKey)}
                >
                  <PanelRightOpen size={14} />
                  {panelKey === 'alerts' ? '显示告警面板' : panelKey === 'ai' ? '显示 AI 面板' : '显示图例'}
                </button>
              ))}
            </div>
          </div>

          {!topDockHidden && (
            <div className="twin-top-dock">
              {/* <section className="twin-top-dock__hero glass-card">
                <button
                  className="immersive-toggle immersive-toggle--ghost"
                  type="button"
                  onClick={() => setTopDockHidden(true)}
                >
                  <EyeOff size={14} />
                  收起顶部
                </button>
              </section> */}

              <section className="twin-top-dock__metrics">
                {[
                  { label: '今日告警', value: '120', detail: '同比 +12%', tone: 'red' },
                  { label: '在运车辆', value: '1,247', detail: '覆盖率 98.3%', tone: 'green' },
                  { label: '高风险线路', value: '3 条', detail: '19路 / 24路 / 81路', tone: 'orange' },
                  { label: '事故风险指数', value: '0.78', detail: '较昨日 ↓ 0.06', tone: 'green' },
                ].map((metric) => (
                  <article className="floating-metric glass-card" data-tone={metric.tone} key={metric.label}>
                    <span>{metric.label}</span>
                    <strong>{metric.value}</strong>
                    <small>{metric.detail}</small>
                  </article>
                ))}
              </section>
            </div>
          )}

          {!floatingPanels.alerts.hidden && (
            <FloatingPanel
              id="alerts"
              title="持续告警车辆"
              subtitle={`${alertCount} 辆正在告警`}
              icon={AlertTriangle}
              badge={String(alertCount)}
              state={floatingPanels.alerts}
              onToggleHidden={togglePanelHidden}
              onPointerStart={handlePanelPointerStart}
            >
              <div className="alert-scroll-list">
                {groupSafetyAlerts.map((alert) => {
                  const vehicle = groupSafetyVehicles.find((item) => item.plate === alert.plate)
                  const isActive = vehicle?.id === focusedVehicleId || alert.plate === selectedVehicle?.plate

                  return (
                    <button
                      key={alert.plate}
                      className="alert-item"
                      data-level={alert.level}
                      data-active={isActive ? 'true' : 'false'}
                      type="button"
                      onClick={() => {
                        if (!vehicle) {
                          return
                        }

                        setFocusedVehicleId(vehicle.id)
                        setSelectedVehicle(vehicle)
                      }}
                    >
                      <div className="alert-item__header">
                        <strong>{alert.plate}</strong>
                        <TonePill tone={alert.level === 'high' ? 'red' : 'orange'}>
                          {alert.level === 'high' ? 'P1' : 'P2'}
                        </TonePill>
                      </div>
                      <div className="alert-item__body">
                        <span>{alert.type}</span>
                        <span className="alert-item__meta">
                          <span>{alert.route}</span>
                          <span>{alert.time}</span>
                          <span>持续 {alert.duration}</span>
                        </span>
                      </div>
                      {alert.level === 'high' && <div className="alert-item__danger-bar" />}
                    </button>
                  )
                })}
              </div>
            </FloatingPanel>
          )}

          {!floatingPanels.ai.hidden && (
            <FloatingPanel
              id="ai"
              title="AI 语音助手"
              subtitle="基于 GraphRAG + MCP + 交通数据"
              icon={Mic}
              state={floatingPanels.ai}
              onToggleHidden={togglePanelHidden}
              onPointerStart={handlePanelPointerStart}
            >
              <div className="ai-chat-thread">
                <div className="ai-bubble ai-bubble--user">
                  <span>19路今天为什么告警偏高？</span>
                </div>
                <div className="ai-bubble ai-bubble--ai">
                  <p>
                    根据知识图谱分析，19路今天告警偏高的原因有三个：
                  </p>
                  <ol>
                    <li>梦溪广场至大市口路段早高峰拥挤，急减速事件明显增多</li>
                    <li>苏L03215 所在线路班次密度偏高，驾驶员连续驾驶时长接近阈值</li>
                    <li>车载终端在火车站南广场附近出现短时离线，触发了设备稳定性告警</li>
                  </ol>
                  <div className="ai-bubble__sources">
                    <span>数据来源：</span>
                    <TonePill tone="blue">GraphRAG</TonePill>
                    <TonePill tone="blue">高德路况</TonePill>
                    <TonePill tone="blue">MCP</TonePill>
                  </div>
                </div>
              </div>
              <div className="ai-input-bar">
                <button className="ai-voice-btn">
                  <Mic size={18} />
                </button>
                <input
                  className="ai-input"
                  placeholder="语音或文字提问，如：哪些车辆有告警？"
                />
              </div>
            </FloatingPanel>
          )}

          {!floatingPanels.legend.hidden && (
            <FloatingPanel
              id="legend"
              title="地图图例"
              subtitle="线路、车辆与热力的状态语义"
              icon={ShieldAlert}
              state={floatingPanels.legend}
              onToggleHidden={togglePanelHidden}
              onPointerStart={handlePanelPointerStart}
            >
              <div className="twin-legend__row">
                <strong>公交线路</strong>
                <span className="twin-legend__item">
                  <i style={{ background: '#2ae7b8' }} />
                  发光贴地线
                </span>
              </div>
              <div className="twin-legend__row">
                <strong>车辆状态</strong>
                <span className="twin-legend__item">
                  <i style={{ background: '#4caf50' }} />
                  正常
                </span>
                <span className="twin-legend__item">
                  <i style={{ background: '#ff9800' }} />
                  预警
                </span>
                <span className="twin-legend__item">
                  <i style={{ background: '#f44336' }} />
                  告警
                </span>
              </div>
              <div className="twin-legend__row">
                <strong>热力图</strong>
                <span className="twin-legend__item twin-legend__item--gradient">
                  <i className="twin-legend__heat" />
                  低 → 高 繁忙度
                </span>
              </div>
            </FloatingPanel>
          )}
        </div>
      </div>
    </PageShell>
  )
}
