-- 通用知识图谱平台 - 平台级保护本体 seed
-- 设计原则：
--   1. 本系统是通用知识图谱平台，不内置任何业务领域本体（设备/船舶/告警等示例仅供参考，绝不在 seed 中下发）。
--   2. 本文件仅注册平台运行必需的少量"保护类型"，承担证据载体与文档锚点职责，租户不可删除。
--   3. 业务实体类型、关系类型、业务分类必须由租户/项目通过 KG-ADMIN-* 管理 API 自行注册。
--   4. 若需查看一个示例本体配置（航运/设备/告警领域），可手工执行 examples/graph_taxonomy_example_marine.sql；该示例不会被 init 自动加载。
--   5. `uncategorized` 是平台级兜底分类，不表达任何业务本体；用于架构图、流程图、临时资料等尚未归类来源先落库、留证据、待后续治理。
-- 可重复执行：所有 INSERT 使用 ON CONFLICT。

INSERT INTO graph_entity_type (entity_type, label, description, unique_key_schema, property_schema, extractor_rules, sort_order)
VALUES
  ('SourceBlock', '证据块', '平台保护类型：知识源解析后的文本块/表格块/OCR 块/日志块，作为所有业务实体与关系的证据载体。租户不可删除。',
    '["sourceId","blockId"]'::jsonb,
    '{"sourceId":"string","blockId":"string","pageNo":"number","sectionPath":"string"}'::jsonb,
    '{}'::jsonb, 1),
  ('Document', '文档', '平台保护类型：知识源对应的文档对象，承担文件级元数据。租户不可删除。',
    '["sourceId"]'::jsonb,
    '{"sourceId":"string","name":"string","sourceType":"string"}'::jsonb,
    '{}'::jsonb, 2),
  ('Section', '段落/章节', '平台保护类型：文档内部章节锚点，用于跨块定位和路径检索。租户不可删除。',
    '["sourceId","sectionPath"]'::jsonb,
    '{"sourceId":"string","sectionPath":"string","title":"string"}'::jsonb,
    '{}'::jsonb, 3),
  ('ExtractedObject', '待归类对象', '平台中立候选类型：系统从未归类资料中抽出的对象/部件/事项，不代表任何业务本体，需后续自动归类或人工治理。',
    '["sourceId","name"]'::jsonb,
    '{"name":"string","nameText":"string","identifier":"string","mentionRole":"string"}'::jsonb,
    '{}'::jsonb, 4),
  ('ExtractedIdentifier', '待归类标识', '平台中立候选类型：系统从未归类资料中抽出的编号、编码、ID 等标识，不代表任何业务本体。',
    '["sourceId","identifier"]'::jsonb,
    '{"identifier":"string"}'::jsonb,
    '{}'::jsonb, 5),
  ('ExtractedQuantity', '待归类数量', '平台中立候选类型：系统从未归类资料中抽出的数量事实，不代表任何业务本体。',
    '["sourceId","value","unit"]'::jsonb,
    '{"value":"number","unit":"string"}'::jsonb,
    '{}'::jsonb, 6)
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
  ('HAS_EVIDENCE', '拥有证据', '平台保护关系：任意业务实体或关系绑定到 SourceBlock 证据。租户不可删除。',
    '["*"]'::jsonb,
    '["SourceBlock"]'::jsonb,
    '{"sourceId":"string","pageNo":"number","hash":"string"}'::jsonb,
    NULL, 1),
  ('HAS_SECTION', '包含章节', '平台保护关系：Document 包含 Section。',
    '["Document"]'::jsonb,
    '["Section"]'::jsonb,
    '{}'::jsonb,
    NULL, 2),
  ('IN_SECTION', '属于章节', '平台保护关系：SourceBlock 属于某 Section。',
    '["SourceBlock"]'::jsonb,
    '["Section"]'::jsonb,
    '{}'::jsonb,
    NULL, 3),
  ('HAS_COMPONENT', '包含组成', '平台中立候选关系：未归类对象包含另一个对象或组成部分，不代表任何业务本体。',
    '["ExtractedObject"]'::jsonb,
    '["ExtractedObject"]'::jsonb,
    '{"quantity":"number","unit":"string"}'::jsonb,
    NULL, 4),
  ('HAS_IDENTIFIER', '拥有标识', '平台中立候选关系：对象或组成部分拥有编号、编码、ID 等标识。',
    '["ExtractedObject"]'::jsonb,
    '["ExtractedIdentifier"]'::jsonb,
    '{}'::jsonb,
    NULL, 5),
  ('HAS_QUANTITY', '拥有数量', '平台中立候选关系：对象或组成部分关联一个数量事实。',
    '["ExtractedObject"]'::jsonb,
    '["ExtractedQuantity"]'::jsonb,
    '{}'::jsonb,
    NULL, 6),
  ('HAS_VALUE', '拥有取值', '平台中立候选关系：枚举项、字段、角色或键名拥有一个文本取值。',
    '["ExtractedObject"]'::jsonb,
    '["ExtractedObject","ExtractedIdentifier","ExtractedQuantity"]'::jsonb,
    '{}'::jsonb,
    NULL, 7),
  ('MENTIONS', '提及', '平台中立候选关系：证据块提及某个待归类对象或标识。',
    '["SourceBlock"]'::jsonb,
    '["ExtractedObject","ExtractedIdentifier","ExtractedQuantity"]'::jsonb,
    '{}'::jsonb,
    NULL, 8)
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
VALUES ('default', '*', 'uncategorized', '待归类资料', 'general', '平台兜底分类：用于系统架构图、流程图、临时文档等尚未建立业务分类的知识源；不代表任何业务本体。', '[]'::jsonb, '[]'::jsonb, 0)
ON CONFLICT (tenant_id, project_id, category_id) DO UPDATE SET
  category_name = EXCLUDED.category_name,
  domain = EXCLUDED.domain,
  description = EXCLUDED.description,
  entity_type_scope = EXCLUDED.entity_type_scope,
  relation_type_scope = EXCLUDED.relation_type_scope,
  sort_order = EXCLUDED.sort_order,
  status = 'enabled',
  updated_at = now();
