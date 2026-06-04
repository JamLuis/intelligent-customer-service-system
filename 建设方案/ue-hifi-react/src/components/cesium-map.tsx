import { useEffect, useRef, useState } from 'react'
import {
  Cartesian2,
  Cartesian3,
  Color,
  defined,
  DistanceDisplayCondition,
  HeadingPitchRange,
  HeightReference,
  HorizontalOrigin,
  ImageryLayer,
  LabelStyle,
  Math as CesiumMath,
  NearFarScalar,
  OpenStreetMapImageryProvider,
  PolylineGlowMaterialProperty,
  PolylineOutlineMaterialProperty,
  Rectangle,
  SceneMode,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  VerticalOrigin,
  Viewer,
  type Entity,
} from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import type {
  BusRouteFeature,
  GeoPoi,
  GeoStation,
  GeoVehicle,
  HeatZone,
  RiskLevel,
} from '../data/group-safety-map'

interface CesiumMapProps {
  routes: BusRouteFeature[]
  vehicles: GeoVehicle[]
  stations: GeoStation[]
  pois: GeoPoi[]
  heatZones: HeatZone[]
  focusedVehicleId?: string | null
  isLoading?: boolean
  errorMessage?: string | null
  onVehicleSelect?: (vehicle: GeoVehicle) => void
  onRouteSelect?: (route: BusRouteFeature) => void
}

type SelectedOverlay =
  | { kind: 'route'; route: BusRouteFeature }
  | { kind: 'vehicle'; vehicle: GeoVehicle }
  | null

const riskStyles: Record<RiskLevel, { color: string; halo: string; width: number; glowWidth: number; opacity: number }> = {
  safe: { color: '#2ae7b8', halo: '#23bca7', width: 2.2, glowWidth: 8, opacity: 0.95 },
  warning: { color: '#ffb347', halo: '#ff8f1f', width: 2.8, glowWidth: 10, opacity: 0.98 },
  danger: { color: '#ff6858', halo: '#ff4d6d', width: 3.2, glowWidth: 12, opacity: 1 },
}

function createRouteBounds(routes: BusRouteFeature[]) {
  const coordinates = routes.flatMap((route) => route.geometry.coordinates)

  if (coordinates.length === 0) {
    return Rectangle.fromDegrees(119.36, 32.15, 119.53, 32.24)
  }

  const [firstLongitude, firstLatitude] = coordinates[0]
  let west = firstLongitude
  let east = firstLongitude
  let south = firstLatitude
  let north = firstLatitude

  coordinates.forEach(([longitude, latitude]) => {
    west = Math.min(west, longitude)
    east = Math.max(east, longitude)
    south = Math.min(south, latitude)
    north = Math.max(north, latitude)
  })

  const routeBounds = {
    west: west - 0.015,
    south: south - 0.01,
    east: east + 0.015,
    north: north + 0.01,
  }
  const isZhenjiangArea =
    routeBounds.west > 118.8 &&
    routeBounds.east < 120.1 &&
    routeBounds.south > 31.6 &&
    routeBounds.north < 32.8

  if (isZhenjiangArea) {
    return Rectangle.fromDegrees(119.36, 32.15, 119.53, 32.24)
  }

  return Rectangle.fromDegrees(routeBounds.west, routeBounds.south, routeBounds.east, routeBounds.north)
}

function midpoint(route: BusRouteFeature) {
  const middleIndex = Math.floor(route.geometry.coordinates.length / 2)
  return route.geometry.coordinates[middleIndex]
}

function pulseRadius(status: RiskLevel) {
  if (status === 'danger') return 220
  if (status === 'warning') return 160
  return 0
}

export function CesiumMap({
  routes,
  vehicles,
  stations,
  pois,
  heatZones,
  focusedVehicleId,
  isLoading = false,
  errorMessage = null,
  onVehicleSelect,
  onRouteSelect,
}: CesiumMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const viewerRef = useRef<Viewer | null>(null)
  const entityIndexRef = useRef(new Map<string, SelectedOverlay>)
  const vehicleEntityIdsRef = useRef(new Map<string, string>())
  const callbacksRef = useRef({ onVehicleSelect, onRouteSelect })
  const [selected, setSelected] = useState<SelectedOverlay>(null)

  useEffect(() => {
    callbacksRef.current = { onVehicleSelect, onRouteSelect }
  }, [onVehicleSelect, onRouteSelect])

  useEffect(() => {
    const mapContainer = containerRef.current

    if (!mapContainer || viewerRef.current) {
      return
    }

    const baseMapProvider = new OpenStreetMapImageryProvider({
      url: 'https://tile.openstreetmap.org/',
      maximumLevel: 18,
    })

    const viewer = new Viewer(mapContainer, {
      animation: false,
      baseLayerPicker: false,
      baseLayer: new ImageryLayer(baseMapProvider, {
        brightness: 0.42,
        contrast: 1.15,
        saturation: 0.15,
        gamma: 0.88,
      }),
      fullscreenButton: false,
      geocoder: false,
      homeButton: false,
      infoBox: false,
      navigationHelpButton: false,
      sceneMode: SceneMode.SCENE2D,
      sceneModePicker: false,
      selectionIndicator: false,
      timeline: false,
      shouldAnimate: false,
    })

    viewerRef.current = viewer
    viewer.resize()
    viewer.scene.backgroundColor = Color.fromCssColorString('#070c15')
    viewer.scene.globe.baseColor = Color.fromCssColorString('#0a1420')
    viewer.scene.globe.showGroundAtmosphere = false
    viewer.scene.globe.depthTestAgainstTerrain = false
    if (viewer.scene.skyBox) {
      viewer.scene.skyBox.show = false
    }
    if (viewer.scene.sun) {
      viewer.scene.sun.show = false
    }
    if (viewer.scene.moon) {
      viewer.scene.moon.show = false
    }
    viewer.scene.fog.enabled = false
    viewer.scene.screenSpaceCameraController.enableTilt = true
    viewer.scene.screenSpaceCameraController.maximumZoomDistance = 80000
    viewer.scene.screenSpaceCameraController.minimumZoomDistance = 900
    viewer.scene.screenSpaceCameraController.inertiaSpin = 0.75
    viewer.scene.screenSpaceCameraController.inertiaTranslate = 0.75
    viewer.scene.screenSpaceCameraController.inertiaZoom = 0.65
    ;(viewer.cesiumWidget.creditContainer as HTMLElement).style.opacity = '0.55'

    const handler = new ScreenSpaceEventHandler(viewer.scene.canvas)
    handler.setInputAction((movement: { position: Cartesian2 }) => {
      const picked = viewer.scene.pick(movement.position)

      if (!defined(picked) || !('id' in picked)) {
        setSelected(null)
        return
      }

      const entity = picked.id as Entity
      const nextSelection = entityIndexRef.current.get(entity.id)

      if (!nextSelection) {
        setSelected(null)
        return
      }

      setSelected(nextSelection)

      if (nextSelection.kind === 'vehicle') {
        callbacksRef.current.onVehicleSelect?.(nextSelection.vehicle)
        void viewer.flyTo(entity, {
          duration: 1.2,
          offset: new HeadingPitchRange(0, CesiumMath.toRadians(-55), 5200),
        })
        return
      }

      callbacksRef.current.onRouteSelect?.(nextSelection.route)
      void viewer.flyTo(entity, {
        duration: 1.2,
        offset: new HeadingPitchRange(0, CesiumMath.toRadians(-70), 18000),
      })
    }, ScreenSpaceEventType.LEFT_CLICK)

    return () => {
      handler.destroy()
      viewer.destroy()
      viewerRef.current = null
    }
  }, [])

  useEffect(() => {
    const viewer = viewerRef.current

    if (!viewer) {
      return
    }

    viewer.entities.removeAll()
    entityIndexRef.current.clear()
    vehicleEntityIdsRef.current.clear()

    heatZones.forEach((zone) => {
      const intensityColor =
        zone.intensity === 'high'
          ? Color.fromCssColorString('#f44336').withAlpha(0.22)
          : zone.intensity === 'medium'
            ? Color.fromCssColorString('#ff9800').withAlpha(0.18)
            : Color.fromCssColorString('#4caf50').withAlpha(0.16)

      viewer.entities.add({
        id: `heat-${zone.id}`,
        position: Cartesian3.fromDegrees(zone.longitude, zone.latitude),
        ellipse: {
          semiMajorAxis: zone.radius,
          semiMinorAxis: zone.radius,
          height: 0,
          material: intensityColor,
          outline: false,
        },
      })
    })

    routes.forEach((route) => {
      const style = riskStyles[route.properties.riskLevel]
      const flatCoordinates = route.geometry.coordinates.flatMap(([longitude, latitude]) => [longitude, latitude])
      const [labelLongitude, labelLatitude] = midpoint(route)
      const shouldLabelRoute =
        routes.length < 80
          ? route.properties.riskLevel !== 'safe' || route.properties.routeName.startsWith('24路')
          : route.properties.riskLevel === 'danger'

      const glowColor = Color.fromCssColorString(style.halo).withAlpha(0.24)
      viewer.entities.add({
        id: `route-glow-${route.properties.routeId}`,
        polyline: {
          positions: Cartesian3.fromDegreesArray(flatCoordinates),
          width: style.glowWidth,
          clampToGround: true,
          material: new PolylineGlowMaterialProperty({
            color: glowColor,
            glowPower: route.properties.riskLevel === 'danger' ? 0.3 : 0.24,
            taperPower: 0.65,
          }),
        },
      })

      const entity = viewer.entities.add({
        id: `route-${route.properties.routeId}`,
        position: Cartesian3.fromDegrees(labelLongitude, labelLatitude, 20),
        polyline: {
          positions: Cartesian3.fromDegreesArray(flatCoordinates),
          width: style.width,
          clampToGround: true,
          material: new PolylineOutlineMaterialProperty({
            color: Color.fromCssColorString(style.color).withAlpha(style.opacity),
            outlineColor: Color.fromCssColorString('#effffe').withAlpha(0.58),
            outlineWidth: 1,
          }),
        },
        label: shouldLabelRoute
          ? {
              text: route.properties.routeName,
              font: '600 12px sans-serif',
              style: LabelStyle.FILL_AND_OUTLINE,
              fillColor: Color.fromCssColorString(style.color),
              outlineColor: Color.fromCssColorString('#071018'),
              outlineWidth: 3,
              showBackground: true,
              backgroundColor: Color.fromCssColorString('#09111b').withAlpha(0.78),
              pixelOffset: new Cartesian2(0, -18),
              scaleByDistance: new NearFarScalar(3000, 1.1, 40000, 0.65),
              distanceDisplayCondition: new DistanceDisplayCondition(0, routes.length < 80 ? 65000 : 42000),
              disableDepthTestDistance: Number.POSITIVE_INFINITY,
            }
          : undefined,
      })

      entityIndexRef.current.set(entity.id, { kind: 'route', route })
    })

    stations.forEach((station) => {
      viewer.entities.add({
        id: `station-${station.id}`,
        position: Cartesian3.fromDegrees(station.longitude, station.latitude, 40),
        point: {
          pixelSize: 10,
          color: Color.fromCssColorString('#55b7ff'),
          outlineColor: Color.WHITE.withAlpha(0.75),
          outlineWidth: 1,
        },
        label: {
          text: station.name,
          font: '12px sans-serif',
          fillColor: Color.fromCssColorString('#b8ddff'),
          showBackground: true,
          backgroundColor: Color.fromCssColorString('#09111b').withAlpha(0.72),
          pixelOffset: new Cartesian2(0, 16),
          style: LabelStyle.FILL,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          distanceDisplayCondition: new DistanceDisplayCondition(0, 50000),
        },
      })
    })

    pois.forEach((poi) => {
      viewer.entities.add({
        id: `${poi.kind}-${poi.id}`,
        position: Cartesian3.fromDegrees(poi.longitude, poi.latitude, 40),
        point: {
          pixelSize: poi.kind === 'district' ? 0 : 8,
          color: poi.kind === 'district' ? Color.TRANSPARENT : Color.fromCssColorString('#5fd6ff'),
        },
        label: {
          text: poi.name,
          font: poi.kind === 'district' ? '600 14px sans-serif' : '11px sans-serif',
          fillColor:
            poi.kind === 'district'
              ? Color.WHITE.withAlpha(0.72)
              : Color.fromCssColorString('#8fd8ff'),
          showBackground: true,
          backgroundColor:
            poi.kind === 'district'
              ? Color.fromCssColorString('#0a1018').withAlpha(0.45)
              : Color.fromCssColorString('#08101a').withAlpha(0.72),
          outlineWidth: poi.kind === 'district' ? 0 : 2,
          style: poi.kind === 'district' ? LabelStyle.FILL : LabelStyle.FILL_AND_OUTLINE,
          outlineColor: Color.fromCssColorString('#071018'),
          pixelOffset: new Cartesian2(0, poi.kind === 'district' ? 0 : 16),
          horizontalOrigin: HorizontalOrigin.CENTER,
          verticalOrigin: VerticalOrigin.BOTTOM,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          distanceDisplayCondition: new DistanceDisplayCondition(0, 70000),
        },
      })
    })

    vehicles.forEach((vehicle) => {
      const pointColor =
        vehicle.status === 'danger'
          ? Color.fromCssColorString('#ff5f57')
          : vehicle.status === 'warning'
            ? Color.fromCssColorString('#ffb347')
            : Color.fromCssColorString('#67d58c')

      const pulse = pulseRadius(vehicle.status)
      const entity = viewer.entities.add({
        id: `vehicle-${vehicle.id}`,
        position: Cartesian3.fromDegrees(vehicle.longitude, vehicle.latitude, 65),
        point: {
          pixelSize: vehicle.status === 'danger' ? 13 : 11,
          color: pointColor,
          outlineColor: Color.WHITE.withAlpha(0.9),
          outlineWidth: 1.5,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          heightReference: HeightReference.NONE,
        },
        ellipse: pulse
          ? {
              semiMajorAxis: pulse,
              semiMinorAxis: pulse,
              height: 20,
              material: pointColor.withAlpha(vehicle.status === 'danger' ? 0.16 : 0.12),
              outline: true,
              outlineWidth: 1,
              outlineColor: pointColor.withAlpha(0.35),
            }
          : undefined,
        label:
          vehicle.status === 'danger'
            ? {
                text: `${vehicle.plate}\n${vehicle.route} · ${vehicle.driver}`,
                font: '600 11px sans-serif',
                fillColor: Color.WHITE,
                showBackground: true,
                backgroundColor: Color.fromCssColorString('#151b28').withAlpha(0.92),
                pixelOffset: new Cartesian2(0, -30),
                verticalOrigin: VerticalOrigin.BOTTOM,
                style: LabelStyle.FILL,
                disableDepthTestDistance: Number.POSITIVE_INFINITY,
              }
            : undefined,
      })

      vehicleEntityIdsRef.current.set(vehicle.id, entity.id)
      entityIndexRef.current.set(entity.id, { kind: 'vehicle', vehicle })
    })

    viewer.resize()
    viewer.camera.setView({ destination: createRouteBounds(routes) })
    viewer.scene.requestRender()
  }, [heatZones, pois, routes, stations, vehicles])

  useEffect(() => {
    const viewer = viewerRef.current

    if (!viewer || !focusedVehicleId) {
      return
    }

    const entityId = vehicleEntityIdsRef.current.get(focusedVehicleId)

    if (!entityId) {
      return
    }

    const selection = entityIndexRef.current.get(entityId)
    const entity = viewer.entities.getById(entityId)

    if (!entity || !selection || selection.kind !== 'vehicle') {
      return
    }

    setSelected(selection)
    callbacksRef.current.onVehicleSelect?.(selection.vehicle)
    void viewer.flyTo(entity, {
      duration: 1.15,
      offset: new HeadingPitchRange(0, CesiumMath.toRadians(-55), 5200),
    })
  }, [focusedVehicleId])

  return (
    <div className="twin-map__surface">
      <div ref={containerRef} className="twin-map__canvas" />
      <div className="twin-map__hud">
        <span className="twin-map__badge">Cesium GIS · 镇江主城区</span>
        <span className="twin-map__badge">{routes.length} 条公交线路已加载</span>
      </div>
      {(isLoading || errorMessage) && (
        <div className="twin-map__status" role="status">
          {errorMessage ?? '正在载入镇江公交线路数据...'}
        </div>
      )}
      {selected?.kind === 'route' && (
        <div className="twin-map__detail twin-map__detail--route">
          <span className="twin-map__detail-kicker">线路详情</span>
          <strong>{selected.route.properties.routeName}</strong>
          <p>风险评分 {selected.route.properties.riskScore} · 当前告警 {selected.route.properties.alertCount} 次</p>
        </div>
      )}
      {selected?.kind === 'vehicle' && (
        <div className="twin-map__detail twin-map__detail--vehicle">
          <span className="twin-map__detail-kicker">车辆详情</span>
          <strong>{selected.vehicle.plate}</strong>
          <p>{selected.vehicle.route} · {selected.vehicle.driver}</p>
          <p>{selected.vehicle.alert ?? '当前运行正常'}</p>
        </div>
      )}
    </div>
  )
}
