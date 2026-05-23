-- Initial taxonomy seed for real engineering knowledge graph.
-- Re-runnable: all inserts use ON CONFLICT upsert semantics.

INSERT INTO graph_entity_type (entity_type, label, description, unique_key_schema, property_schema, extractor_rules, sort_order)
VALUES
  ('Region', '地区', '区域、片区或管理辖区', '["code"]'::jsonb, '{"code":"string","name":"string"}'::jsonb, '{"aliases":["片区","区域","地区"]}'::jsonb, 10),
  ('Port', '港口', '港口、码头或靠泊点', '["portCode"]'::jsonb, '{"portCode":"string","name":"string"}'::jsonb, '{}'::jsonb, 20),
  ('Vessel', '船舶', '船舶或船只资产', '["vesselId"]'::jsonb, '{"vesselId":"string","name":"string","imo":"string"}'::jsonb, '{"patterns":["船","船舶"]}'::jsonb, 30),
  ('Crew', '船员', '船员、值班人或责任人', '["crewId"]'::jsonb, '{"crewId":"string","name":"string","role":"string"}'::jsonb, '{}'::jsonb, 40),
  ('Role', '角色', '人员角色或岗位', '["roleCode"]'::jsonb, '{"roleCode":"string","name":"string"}'::jsonb, '{}'::jsonb, 50),
  ('Duty', '值班', '值班班次或职责记录', '["dutyId"]'::jsonb, '{"dutyId":"string","timeRange":"object"}'::jsonb, '{}'::jsonb, 60),
  ('Device', '设备', '船舶、工地或平台设备', '["deviceId"]'::jsonb, '{"deviceId":"string","deviceName":"string","deviceType":"string"}'::jsonb, '{"patterns":["[A-Z]{2,}-?\\d{2,}","TC-\\d+"]}'::jsonb, 70),
  ('DeviceType', '设备类型', '设备类型、型号或分类', '["typeCode"]'::jsonb, '{"typeCode":"string","name":"string"}'::jsonb, '{}'::jsonb, 80),
  ('Alarm', '告警', '告警类型、告警事件或告警定义', '["alarmCode"]'::jsonb, '{"alarmCode":"string","name":"string","level":"string"}'::jsonb, '{"patterns":["告警","报警"]}'::jsonb, 90),
  ('AlarmRule', '告警规则', '告警规则、阈值规则或启停规则', '["ruleId"]'::jsonb, '{"ruleId":"string","name":"string","threshold":"string","enabled":"boolean"}'::jsonb, '{"patterns":["AR-\\d+","规则"]}'::jsonb, 100),
  ('Threshold', '阈值', '阈值、边界值或告警条件', '["thresholdId"]'::jsonb, '{"thresholdId":"string","value":"string","unit":"string"}'::jsonb, '{}'::jsonb, 110),
  ('Protocol', '协议', '设备协议、数据协议或接口协议', '["protocolCode","version"]'::jsonb, '{"protocolCode":"string","name":"string","version":"string"}'::jsonb, '{"patterns":["MQTT","HTTP","TCP","JSON"]}'::jsonb, 120),
  ('Metric', '指标', '指标、测点或遥测项', '["metricCode"]'::jsonb, '{"metricCode":"string","name":"string","unit":"string"}'::jsonb, '{}'::jsonb, 130),
  ('ProtocolField', '协议字段', '协议字段、上报字段或数据点', '["protocolCode","fieldCode"]'::jsonb, '{"protocolCode":"string","fieldCode":"string","name":"string","unit":"string","dataType":"string"}'::jsonb, '{}'::jsonb, 140),
  ('API', '接口', 'HTTP/RPC API 或系统接口', '["method","path"]'::jsonb, '{"method":"string","path":"string","system":"string"}'::jsonb, '{"patterns":["GET ","POST ","/api/"]}'::jsonb, 150),
  ('Controller', '控制器', '后端控制器或入口类', '["className"]'::jsonb, '{"className":"string","filePath":"string"}'::jsonb, '{}'::jsonb, 160),
  ('Service', '服务', '后端服务类或业务服务', '["className"]'::jsonb, '{"className":"string","filePath":"string"}'::jsonb, '{}'::jsonb, 170),
  ('Mapper', '数据访问', 'Mapper、DAO 或 Repository', '["className"]'::jsonb, '{"className":"string","filePath":"string"}'::jsonb, '{}'::jsonb, 180),
  ('Table', '数据表', '数据库表', '["schema","tableName"]'::jsonb, '{"schema":"string","tableName":"string"}'::jsonb, '{}'::jsonb, 190),
  ('Column', '字段', '数据库字段或协议字段映射对象', '["schema","tableName","columnName"]'::jsonb, '{"schema":"string","tableName":"string","columnName":"string","dataType":"string"}'::jsonb, '{}'::jsonb, 200),
  ('Job', '任务', '定时任务、批处理任务或运维任务', '["jobCode"]'::jsonb, '{"jobCode":"string","name":"string"}'::jsonb, '{}'::jsonb, 210),
  ('LogPattern', '日志模式', '日志错误模式、关键字或异常栈摘要', '["patternCode"]'::jsonb, '{"patternCode":"string","pattern":"string","level":"string"}'::jsonb, '{}'::jsonb, 220),
  ('Alert', '监控告警', '运维监控告警或系统告警', '["alertCode"]'::jsonb, '{"alertCode":"string","name":"string","level":"string"}'::jsonb, '{}'::jsonb, 230),
  ('Incident', '故障事件', '故障、工单或问题案例', '["incidentId"]'::jsonb, '{"incidentId":"string","title":"string"}'::jsonb, '{}'::jsonb, 240),
  ('Symptom', '现象', '用户描述的问题现象', '["symptomCode"]'::jsonb, '{"symptomCode":"string","description":"string"}'::jsonb, '{}'::jsonb, 250),
  ('Cause', '原因', '根因、可能原因或故障原因', '["causeCode"]'::jsonb, '{"causeCode":"string","description":"string"}'::jsonb, '{}'::jsonb, 260),
  ('FixAction', '处理动作', '处理建议、修复动作或排查步骤', '["actionCode"]'::jsonb, '{"actionCode":"string","description":"string","riskLevel":"string"}'::jsonb, '{}'::jsonb, 270),
  ('SourceBlock', '证据块', '文件、段落、表格、OCR 或日志块证据', '["sourceId","blockId"]'::jsonb, '{"sourceId":"string","blockId":"string","pageNo":"number","sectionPath":"string"}'::jsonb, '{}'::jsonb, 280)
ON CONFLICT (entity_type) DO UPDATE SET
  label = EXCLUDED.label,
  description = EXCLUDED.description,
  unique_key_schema = EXCLUDED.unique_key_schema,
  property_schema = EXCLUDED.property_schema,
  extractor_rules = EXCLUDED.extractor_rules,
  sort_order = EXCLUDED.sort_order,
  status = 'enabled',
  updated_at = now();

INSERT INTO graph_relation_type (relation_type, label, description, from_entity_types, to_entity_types, property_schema, inverse_relation_type, sort_order)
VALUES
  ('LOCATED_IN', '位于', '船舶、港口或设备位于某地区', '["Vessel","Port","Device"]'::jsonb, '["Region","Port"]'::jsonb, '{"confidence":"number"}'::jsonb, null, 10),
  ('MANAGED_BY', '由其管理', '资产由地区、组织或角色管理', '["Vessel","Device","Port"]'::jsonb, '["Region","Role","Crew"]'::jsonb, '{}'::jsonb, null, 20),
  ('CREW_ON', '任职于', '船员在船舶上任职或当班', '["Crew"]'::jsonb, '["Vessel"]'::jsonb, '{"role":"string","timeRange":"object"}'::jsonb, null, 30),
  ('HAS_ROLE', '拥有角色', '人员拥有岗位或角色', '["Crew"]'::jsonb, '["Role"]'::jsonb, '{}'::jsonb, null, 40),
  ('ON_DUTY', '值班', '人员参与值班或职责安排', '["Crew"]'::jsonb, '["Duty"]'::jsonb, '{"timeRange":"object"}'::jsonb, null, 50),
  ('INSTALLED_ON', '安装在', '设备安装在船舶或区域资产上', '["Device"]'::jsonb, '["Vessel","Port","Region"]'::jsonb, '{"installedAt":"string"}'::jsonb, null, 60),
  ('HAS_DEVICE_TYPE', '属于设备类型', '设备归属设备类型', '["Device"]'::jsonb, '["DeviceType"]'::jsonb, '{}'::jsonb, null, 70),
  ('RAISED_BY', '由其产生', '告警由设备产生', '["Alarm"]'::jsonb, '["Device","DeviceType"]'::jsonb, '{}'::jsonb, null, 80),
  ('BOUND_TO', '绑定到', '设备或设备类型绑定告警规则', '["Device","DeviceType","Alarm"]'::jsonb, '["AlarmRule","Threshold"]'::jsonb, '{"threshold":"string","enabled":"boolean"}'::jsonb, null, 90),
  ('TRIGGERS', '触发', '规则、阈值或事件触发告警', '["AlarmRule","Threshold","Metric"]'::jsonb, '["Alarm","Alert"]'::jsonb, '{}'::jsonb, null, 100),
  ('USES_PROTOCOL', '使用协议', '设备或设备类型使用协议', '["Device","DeviceType"]'::jsonb, '["Protocol"]'::jsonb, '{"version":"string"}'::jsonb, null, 110),
  ('HAS_FIELD', '包含字段', '协议包含字段或表包含列', '["Protocol","Table"]'::jsonb, '["ProtocolField","Column"]'::jsonb, '{"required":"boolean","unit":"string"}'::jsonb, null, 120),
  ('MAPS_TO', '映射到', '跨源字段、接口、告警或数据库映射', '["ProtocolField","API","Column","Alarm","Metric"]'::jsonb, '["ProtocolField","API","Column","Alarm","Metric","Device"]'::jsonb, '{"mappingType":"string"}'::jsonb, null, 130),
  ('CALLS', '调用', '接口、控制器、服务或方法调用', '["API","Controller","Service"]'::jsonb, '["Controller","Service","Mapper","API"]'::jsonb, '{"repo":"string","file":"string","line":"number"}'::jsonb, null, 140),
  ('QUERIES', '查询', '服务或 Mapper 查询数据库表/字段', '["Service","Mapper","API"]'::jsonb, '["Table","Column"]'::jsonb, '{"sqlRef":"string"}'::jsonb, null, 150),
  ('OBSERVED_IN', '观测于', '事件、告警或故障出现在日志/指标中', '["Alert","Incident","Symptom","Alarm"]'::jsonb, '["LogPattern","Metric"]'::jsonb, '{"timeRange":"object"}'::jsonb, null, 160),
  ('EMITS', '产生', '任务、系统或设备产生日志/告警/指标', '["Job","Device","Service"]'::jsonb, '["LogPattern","Alert","Metric"]'::jsonb, '{}'::jsonb, null, 170),
  ('DEPENDS_ON', '依赖', '任务、接口或服务依赖另一个工程对象', '["Job","API","Service","Device"]'::jsonb, '["Job","API","Service","Device","Protocol"]'::jsonb, '{}'::jsonb, null, 180),
  ('POSSIBLE_CAUSE', '可能原因', '现象、告警或故障的可能原因', '["Symptom","Alarm","Incident","Alert"]'::jsonb, '["Cause"]'::jsonb, '{}'::jsonb, null, 190),
  ('FIXED_BY', '通过其修复', '原因或故障可通过某动作修复', '["Cause","Incident"]'::jsonb, '["FixAction"]'::jsonb, '{"successRate":"number"}'::jsonb, null, 200),
  ('VERIFIED_BY', '通过其验证', '动作或原因由证据验证', '["FixAction","Cause","Incident"]'::jsonb, '["SourceBlock","Metric","LogPattern"]'::jsonb, '{}'::jsonb, null, 210),
  ('HAS_EVIDENCE', '拥有证据', '实体或关系绑定证据块', '["Region","Port","Vessel","Crew","Device","Alarm","AlarmRule","Protocol","ProtocolField","API","Table","Column","Job","Incident","Symptom","Cause","FixAction"]'::jsonb, '["SourceBlock"]'::jsonb, '{"sourceId":"string","pageNo":"number","hash":"string"}'::jsonb, null, 220)
ON CONFLICT (relation_type) DO UPDATE SET
  label = EXCLUDED.label,
  description = EXCLUDED.description,
  from_entity_types = EXCLUDED.from_entity_types,
  to_entity_types = EXCLUDED.to_entity_types,
  property_schema = EXCLUDED.property_schema,
  inverse_relation_type = EXCLUDED.inverse_relation_type,
  sort_order = EXCLUDED.sort_order,
  status = 'enabled',
  updated_at = now();

INSERT INTO graph_category (tenant_id, project_id, category_id, category_name, domain, description, entity_type_scope, relation_type_scope, sort_order)
VALUES
  ('default', '*', 'geo-vessel', '地区与船舶', 'geo', '按地区、港口和管理辖区组织船舶归属关系', '["Region","Port","Vessel"]'::jsonb, '["LOCATED_IN","MANAGED_BY","HAS_EVIDENCE"]'::jsonb, 10),
  ('default', '*', 'vessel-crew', '船舶与船员', 'people', '维护船舶、船员、岗位和值班职责关系', '["Vessel","Crew","Role","Duty"]'::jsonb, '["CREW_ON","HAS_ROLE","ON_DUTY","HAS_EVIDENCE"]'::jsonb, 20),
  ('default', '*', 'vessel-device', '船舶与设备绑定', 'asset', '维护船舶与设备、设备类型的绑定关系', '["Vessel","Device","DeviceType"]'::jsonb, '["INSTALLED_ON","HAS_DEVICE_TYPE","HAS_EVIDENCE"]'::jsonb, 30),
  ('default', '*', 'device-alarm', '设备与告警', 'event', '维护设备、告警、规则和阈值关系', '["Device","DeviceType","Alarm","AlarmRule","Threshold"]'::jsonb, '["RAISED_BY","BOUND_TO","TRIGGERS","HAS_EVIDENCE"]'::jsonb, 40),
  ('default', '*', 'device-protocol', '设备与协议', 'integration', '维护设备、设备类型、协议、指标和协议字段映射', '["Device","DeviceType","Protocol","Metric","ProtocolField"]'::jsonb, '["USES_PROTOCOL","HAS_FIELD","MAPS_TO","HAS_EVIDENCE"]'::jsonb, 50),
  ('default', '*', 'api-db', '接口与数据库', 'db', '维护 API、控制器、服务、Mapper、表和字段关系', '["API","Controller","Service","Mapper","Table","Column"]'::jsonb, '["CALLS","QUERIES","MAPS_TO","HAS_EVIDENCE"]'::jsonb, 60),
  ('default', '*', 'ops-log', '运维日志与任务', 'ops', '维护任务、日志模式、指标、告警和依赖关系', '["Job","LogPattern","Metric","Alert"]'::jsonb, '["OBSERVED_IN","EMITS","DEPENDS_ON","HAS_EVIDENCE"]'::jsonb, 70),
  ('default', '*', 'case-fix', '工单与处理经验', 'case', '维护故障事件、现象、原因和处理动作关系', '["Incident","Symptom","Cause","FixAction"]'::jsonb, '["POSSIBLE_CAUSE","FIXED_BY","VERIFIED_BY","HAS_EVIDENCE"]'::jsonb, 80)
ON CONFLICT (tenant_id, project_id, category_id) DO UPDATE SET
  category_name = EXCLUDED.category_name,
  domain = EXCLUDED.domain,
  description = EXCLUDED.description,
  entity_type_scope = EXCLUDED.entity_type_scope,
  relation_type_scope = EXCLUDED.relation_type_scope,
  sort_order = EXCLUDED.sort_order,
  status = 'enabled',
  updated_at = now();
