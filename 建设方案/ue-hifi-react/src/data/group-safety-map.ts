export type RiskLevel = 'safe' | 'warning' | 'danger'

export type LngLat = [longitude: number, latitude: number]

export interface BusRouteProperties {
  routeId: string
  routeName: string
  riskLevel: RiskLevel
  alertCount: number
  riskScore: number
}

export interface BusRouteFeature {
  type: 'Feature'
  properties: BusRouteProperties
  geometry: {
    type: 'LineString'
    coordinates: LngLat[]
  }
}

export interface BusRouteCollection {
  type: 'FeatureCollection'
  features: BusRouteFeature[]
}

interface AMapLineStop {
  id: string
  name: string
  location: LngLat
  sequence: number | null
}

interface AMapLineRoute {
  id: string
  name: string
  type: string
  path: LngLat[]
  viaStops: AMapLineStop[]
}

interface AMapBusRoutePayload {
  city: string
  adcode: string
  source: string
  routeCount: number
  routes: AMapLineRoute[]
}

export interface GeoVehicle {
  id: string
  plate: string
  route: string
  driver: string
  status: RiskLevel
  alert?: string
  longitude: number
  latitude: number
}

export interface VehicleAlert {
  plate: string
  route: string
  type: string
  level: 'high' | 'medium'
  time: string
  duration: string
}

export interface GeoStation {
  id: string
  name: string
  longitude: number
  latitude: number
}

export interface GeoPoi {
  id: string
  name: string
  kind: 'district' | 'poi'
  longitude: number
  latitude: number
}

export interface HeatZone {
  id: string
  longitude: number
  latitude: number
  radius: number
  intensity: 'low' | 'medium' | 'high'
}

export const zhenjiangViewport = {
  longitude: 119.452,
  latitude: 32.195,
  height: 30000,
  heading: 0,
  pitch: -67,
  roll: 0,
}

export const groupSafetyVehicles: GeoVehicle[] = [
  { id: 'v1', plate: '苏L03215', route: '19路', driver: '张明', status: 'danger', alert: '紧急制动告警', longitude: 119.463858, latitude: 32.202006 },
  { id: 'v2', plate: '苏L04891', route: '24路', driver: '李强', status: 'warning', alert: '疲劳驾驶预警', longitude: 119.470009, latitude: 32.197102 },
  { id: 'v3', plate: '苏L05127', route: '29路', driver: '王伟', status: 'safe', longitude: 119.448756, latitude: 32.204428 },
  { id: 'v4', plate: '苏L06234', route: '10路', driver: '陈刚', status: 'warning', alert: '超速预警', longitude: 119.454788, latitude: 32.202461 },
  { id: 'v5', plate: '苏L07892', route: '81路', driver: '赵海', status: 'danger', alert: '设备离线告警', longitude: 119.507004, latitude: 32.20105 },
  { id: 'v6', plate: '苏L08456', route: '1路', driver: '刘军', status: 'safe', longitude: 119.43823, latitude: 32.200927 },
  { id: 'v7', plate: '苏L09123', route: '56路', driver: '周明', status: 'safe', longitude: 119.451062, latitude: 32.20479 },
  { id: 'v8', plate: '苏L10789', route: '24路', driver: '吴强', status: 'safe', longitude: 119.495995, latitude: 32.178777 },
]

export const groupSafetyAlerts: VehicleAlert[] = [
  { plate: '苏L03215', route: '19路', type: '紧急制动告警', level: 'high', time: '10:23', duration: '12分钟' },
  { plate: '苏L07892', route: '81路', type: '设备离线告警', level: 'high', time: '09:45', duration: '47分钟' },
  { plate: '苏L04891', route: '24路', type: '疲劳驾驶预警', level: 'medium', time: '10:15', duration: '20分钟' },
  { plate: '苏L06234', route: '10路', type: '超速预警', level: 'medium', time: '10:32', duration: '5分钟' },
]

export const groupSafetyStations: GeoStation[] = [
  { id: 's1', name: '公交一场', longitude: 119.382, latitude: 32.215 },
  { id: 's2', name: '公交二场', longitude: 119.419, latitude: 32.170 },
  { id: 's3', name: '公交三场', longitude: 119.496, latitude: 32.206 },
  { id: 's4', name: '南徐场站', longitude: 119.401, latitude: 32.188 },
]

export const groupSafetyPois: GeoPoi[] = [
  { id: 'd1', name: '京口区', kind: 'district', longitude: 119.487, latitude: 32.201 },
  { id: 'd2', name: '润州区', kind: 'district', longitude: 119.402, latitude: 32.205 },
  { id: 'p1', name: '镇江南站', kind: 'poi', longitude: 119.384, latitude: 32.157 },
  { id: 'p2', name: '大市口', kind: 'poi', longitude: 119.451, latitude: 32.205 },
  { id: 'p3', name: '丁卯桥', kind: 'poi', longitude: 119.497, latitude: 32.204 },
]

export const groupSafetyHeatZones: HeatZone[] = [
  { id: 'h1', longitude: 119.444, latitude: 32.190, radius: 1800, intensity: 'high' },
  { id: 'h2', longitude: 119.489, latitude: 32.205, radius: 1600, intensity: 'high' },
  { id: 'h3', longitude: 119.404, latitude: 32.204, radius: 1450, intensity: 'medium' },
  { id: 'h4', longitude: 119.380, latitude: 32.160, radius: 1200, intensity: 'medium' },
  { id: 'h5', longitude: 119.470, latitude: 32.192, radius: 1300, intensity: 'low' },
]

export async function loadZhenjiangBusRoutes() {
  const response = await fetch('/data/zhenjiang-amap-bus-lines.json')

  if (!response.ok) {
    throw new Error(`线路数据加载失败: ${response.status}`)
  }

  const payload = await response.json() as AMapBusRoutePayload
  const riskByKeyword: Record<string, { riskLevel: RiskLevel; alertCount: number; riskScore: number }> = {
    '19路': { riskLevel: 'danger', alertCount: 18, riskScore: 92 },
    '24路': { riskLevel: 'warning', alertCount: 9, riskScore: 76 },
    '10路': { riskLevel: 'warning', alertCount: 6, riskScore: 71 },
    '81路': { riskLevel: 'danger', alertCount: 12, riskScore: 88 },
  }

  return payload.routes.map<BusRouteFeature>((route) => {
    const shortName = route.name.split('(')[0]
    const risk = riskByKeyword[shortName] ?? { riskLevel: 'safe', alertCount: 2, riskScore: 34 }

    return {
      type: 'Feature',
      properties: {
        routeId: route.id,
        routeName: route.name,
        riskLevel: risk.riskLevel,
        alertCount: risk.alertCount,
        riskScore: risk.riskScore,
      },
      geometry: {
        type: 'LineString',
        coordinates: route.path,
      },
    }
  })
}
