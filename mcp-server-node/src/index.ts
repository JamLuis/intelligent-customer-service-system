import http from 'node:http';

import { tools } from './tools/manifest.js';

const payload = {
  name: 'smart-support-mcp-server-node',
  description: 'MCP tool boundary for business, device, and log systems',
  tools
};

const port = Number(process.env.PORT ?? process.env.MCP_SERVER_PORT ?? 3202);

const headers = {
  'Content-Type': 'application/json; charset=utf-8'
};

function readBody(request: http.IncomingMessage): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    let body = '';
    request.on('data', chunk => {
      body += chunk;
    });
    request.on('end', () => {
      if (!body) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(body) as Record<string, unknown>);
      } catch (error) {
        reject(error);
      }
    });
    request.on('error', reject);
  });
}

function invokeTool(name: string, input: Record<string, unknown>) {
  const now = new Date().toISOString();
  switch (name) {
    case 'device.getStatus':
      return {
        capabilityCode: name,
        status: 'success',
        summary: '设备在线，最近心跳正常',
        data: { deviceId: input.deviceId ?? 'TC-003', online: true, lastHeartbeatAt: now }
      };
    case 'alarm.getRules':
      return {
        capabilityCode: name,
        status: 'success',
        summary: '命中告警规则 AR-17，规则已启用',
        data: { ruleId: 'AR-17', enabled: true, threshold: 90, level: 'L1' }
      };
    case 'config.getSnapshot':
      return {
        capabilityCode: name,
        status: 'success',
        summary: '配置快照读取成功',
        data: { projectId: input.projectId ?? 'P001', version: 'mock-config-v3' }
      };
    case 'log.searchErrors':
      return {
        capabilityCode: name,
        status: input.mockScenario === 'mcp_degraded' ? 'timeout' : 'success',
        summary: input.mockScenario === 'mcp_degraded' ? '日志查询超时' : '未发现关键错误日志',
        data: { count: input.mockScenario === 'mcp_degraded' ? 0 : 2 }
      };
    case 'statistics.rebuild':
      return {
        capabilityCode: name,
        status: 'blocked',
        summary: 'V0.1 禁止真实生产写操作，statistics.rebuild 仅返回占位结果',
        data: { requiresApproval: true }
      };
    default:
      return null;
  }
}

const server = http.createServer(async (request, response) => {
  if (request.method === 'GET' && request.url === '/health') {
    response.writeHead(200, headers);
    response.end(JSON.stringify({ service: payload.name, status: 'ok' }));
    return;
  }

  if (request.method === 'GET' && (request.url === '/manifest' || request.url === '/tools')) {
    response.writeHead(200, headers);
    response.end(JSON.stringify(payload));
    return;
  }

  const invokeMatch = request.url?.match(/^\/tools\/([^/]+(?:\.[^/]+)?)\/invoke$/);
  if (request.method === 'POST' && invokeMatch) {
    try {
      const result = invokeTool(decodeURIComponent(invokeMatch[1]), await readBody(request));
      if (!result) {
        response.writeHead(404, headers);
        response.end(JSON.stringify({ message: 'Tool Not Found' }));
        return;
      }
      response.writeHead(200, headers);
      response.end(JSON.stringify(result));
    } catch (error) {
      response.writeHead(400, headers);
      response.end(JSON.stringify({ message: error instanceof Error ? error.message : 'Bad Request' }));
    }
    return;
  }

  response.writeHead(404, headers);
  response.end(JSON.stringify({ message: 'Not Found' }));
});

server.listen(port, () => {
  process.stdout.write(`smart-support-mcp-server-node listening on http://localhost:${port}\n`);
});