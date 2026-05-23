export type ToolRiskLevel = 'L0' | 'L1' | 'L2' | 'L3' | 'L4' | 'L5';

export type ToolDefinition = {
  name: string;
  category: 'device' | 'alarm' | 'config' | 'log' | 'statistics';
  level: ToolRiskLevel;
  description: string;
};

export const tools: ToolDefinition[] = [
  {
    name: 'device.getStatus',
    category: 'device',
    level: 'L1',
    description: 'Query device online status and last heartbeat.'
  },
  {
    name: 'alarm.getRules',
    category: 'alarm',
    level: 'L1',
    description: 'Query alarm rules by project and device type.'
  },
  {
    name: 'config.getSnapshot',
    category: 'config',
    level: 'L1',
    description: 'Query a configuration snapshot for diagnosis evidence.'
  },
  {
    name: 'log.searchErrors',
    category: 'log',
    level: 'L1',
    description: 'Search related API, task, or device log errors.'
  },
  {
    name: 'statistics.rebuild',
    category: 'statistics',
    level: 'L3',
    description: 'Rebuild statistics after workflow approval.'
  }
];