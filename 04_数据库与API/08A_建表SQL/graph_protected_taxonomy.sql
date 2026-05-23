-- 通用知识图谱平台 - 平台级保护本体 seed
-- 设计原则：
--   1. 本系统是通用知识图谱平台，不内置任何业务领域本体（设备/船舶/告警等示例仅供参考，绝不在 seed 中下发）。
--   2. 本文件仅注册平台运行必需的少量"保护类型"，承担证据载体与文档锚点职责，租户不可删除。
--   3. 业务实体类型、关系类型、分类必须由租户/项目通过 KG-ADMIN-* 管理 API 自行注册。
--   4. 若需查看一个示例本体配置（航运/设备/告警领域），可手工执行 examples/graph_taxonomy_example_marine.sql；该示例不会被 init 自动加载。
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
    '{}'::jsonb, 3)
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
    NULL, 3)
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

-- 故意不插入任何 graph_category：分类完全由租户/项目自行注册。
