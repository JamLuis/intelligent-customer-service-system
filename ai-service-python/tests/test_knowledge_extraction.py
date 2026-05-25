from app.main import build_blocks, extract_candidates


def test_uncategorized_markdown_builds_document_structure_without_business_rule_false_positive():
    raw_text = """# 金融领域大模型能力评测与可用性分析

## 一、最新一代模型在金融场景的公开分数

- FinanceBench 65% 是当前业界默认门槛。
- 风险闸门：L4 一律禁止独立决策。
"""
    blocks = build_blocks(raw_text, "md", "source-1", "uncategorized")
    response = extract_candidates({
        "sourceId": "source-1",
        "fileName": "finance.md",
        "graphCategoryId": "uncategorized",
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": {
            "categories": [
                {
                    "categoryId": "uncategorized",
                    "entityTypeScope": [],
                    "relationTypeScope": [],
                }
            ],
            "entityTypes": [
                {"entityType": "Document"},
                {"entityType": "Section"},
                {"entityType": "SourceBlock"},
                {"entityType": "AlarmRule", "extractorRules": {"patterns": ["规则"]}},
            ],
            "relationTypes": [
                {"relationType": "HAS_EVIDENCE", "fromEntityTypes": ["*"], "toEntityTypes": ["SourceBlock"]},
                {"relationType": "HAS_SECTION", "fromEntityTypes": ["Document"], "toEntityTypes": ["Section"]},
                {"relationType": "IN_SECTION", "fromEntityTypes": ["SourceBlock"], "toEntityTypes": ["Section"]},
            ],
        },
    })

    entity_types = {item["entityType"] for item in response["candidateEntities"]}
    relation_types = {item["relationType"] for item in response["candidateRelations"]}

    assert "Document" in entity_types
    assert "Section" in entity_types
    assert "SourceBlock" in entity_types
    assert "AlarmRule" not in entity_types
    assert {"HAS_EVIDENCE", "HAS_SECTION", "IN_SECTION"}.issubset(relation_types)


def test_markdown_block_ids_are_source_scoped_for_repeat_imports():
    raw_text = "# 标题\n\n同一份文档可以重复导入。"
    first_blocks = build_blocks(raw_text, "md", "source-a", "uncategorized")
    second_blocks = build_blocks(raw_text, "md", "source-b", "uncategorized")

    assert first_blocks[0]["blockId"] != second_blocks[0]["blockId"]
    assert first_blocks[1]["blockId"] != second_blocks[1]["blockId"]


def test_uncategorized_text_extracts_generic_component_and_identifiers():
    raw_text = "塔吊A21212 有5个摄像头设备，编号分别为 C123123、C234234、C4324、C890890、C7879789"
    blocks = build_blocks(raw_text, "text", "source-2", "uncategorized")
    response = extract_candidates({
        "sourceId": "source-2",
        "fileName": "raw-text.txt",
        "graphCategoryId": "uncategorized",
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": {
            "categories": [
                {
                    "categoryId": "uncategorized",
                    "entityTypeScope": [],
                    "relationTypeScope": [],
                }
            ],
            "entityTypes": [
                {"entityType": "Document"},
                {"entityType": "SourceBlock"},
            ],
            "relationTypes": [
                {"relationType": "HAS_EVIDENCE", "fromEntityTypes": ["*"], "toEntityTypes": ["SourceBlock"]},
            ],
        },
    })

    entities = response["candidateEntities"]
    relations = response["candidateRelations"]
    names_by_type = {}
    for entity in entities:
        names_by_type.setdefault(entity["entityType"], set()).add(entity["canonicalName"])
    relation_types = {item["relationType"] for item in relations}

    assert "塔吊A21212" in names_by_type["ExtractedObject"]
    assert "摄像头设备" in names_by_type["ExtractedObject"]
    assert {"A21212", "C123123", "C234234", "C4324", "C890890", "C7879789"}.issubset(names_by_type["ExtractedIdentifier"])
    assert "5个" in names_by_type["ExtractedQuantity"]
    assert {"HAS_COMPONENT", "HAS_IDENTIFIER", "HAS_QUANTITY"}.issubset(relation_types)


def test_uncategorized_text_extracts_quantity_and_enumerated_assignments():
    raw_text = "小明妈妈有三个儿子，大儿子：李敏，二儿子：李佳航，小儿子：林鸣明"
    blocks = build_blocks(raw_text, "text", "source-3", "uncategorized")
    response = extract_candidates({
        "sourceId": "source-3",
        "fileName": "raw-text.txt",
        "graphCategoryId": "uncategorized",
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": {
            "categories": [
                {
                    "categoryId": "uncategorized",
                    "entityTypeScope": [],
                    "relationTypeScope": [],
                }
            ],
            "entityTypes": [
                {"entityType": "Document"},
                {"entityType": "SourceBlock"},
            ],
            "relationTypes": [
                {"relationType": "HAS_EVIDENCE", "fromEntityTypes": ["*"], "toEntityTypes": ["SourceBlock"]},
            ],
        },
    })

    names_by_type = {}
    for entity in response["candidateEntities"]:
        names_by_type.setdefault(entity["entityType"], set()).add(entity["canonicalName"])
    relation_types = {item["relationType"] for item in response["candidateRelations"]}

    assert {"小明妈妈", "儿子", "大儿子", "李敏", "二儿子", "李佳航", "小儿子", "林鸣明"}.issubset(names_by_type["ExtractedObject"])
    assert "3个" in names_by_type["ExtractedQuantity"]
    assert {"HAS_COMPONENT", "HAS_QUANTITY", "HAS_VALUE"}.issubset(relation_types)


def test_uncategorized_markdown_extracts_enumerated_components_and_usage_values():
    raw_text = """# 金融大模型能力评测

金融大模型需要覆盖知识问答、风险识别、合规审查和报告生成。
Qwen 本地模型适合离线初筛，云模型适合高准确率复核。
"""
    blocks = build_blocks(raw_text, "md", "source-4", "uncategorized")
    response = extract_candidates({
        "sourceId": "source-4",
        "fileName": "finance.md",
        "graphCategoryId": "uncategorized",
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": {
            "categories": [{"categoryId": "uncategorized", "entityTypeScope": [], "relationTypeScope": []}],
            "entityTypes": [{"entityType": "Document"}, {"entityType": "SourceBlock"}],
            "relationTypes": [{"relationType": "HAS_EVIDENCE", "fromEntityTypes": ["*"], "toEntityTypes": ["SourceBlock"]}],
        },
    })

    names_by_type = {}
    for entity in response["candidateEntities"]:
        names_by_type.setdefault(entity["entityType"], set()).add(entity["canonicalName"])
    relation_triples = set()
    entity_by_id = {entity["tempId"]: entity["canonicalName"] for entity in response["candidateEntities"]}
    for relation in response["candidateRelations"]:
        relation_triples.add((entity_by_id[relation["sourceTempId"]], relation["relationType"], entity_by_id[relation["targetTempId"]]))

    assert {"金融大模型", "知识问答", "风险识别", "合规审查", "报告生成", "Qwen 本地模型", "离线初筛", "云模型", "高准确率复核"}.issubset(names_by_type["ExtractedObject"])
    assert ("金融大模型", "HAS_COMPONENT", "知识问答") in relation_triples
    assert ("金融大模型", "HAS_COMPONENT", "报告生成") in relation_triples
    assert ("Qwen 本地模型", "HAS_VALUE", "离线初筛") in relation_triples
    assert ("云模型", "HAS_VALUE", "高准确率复核") in relation_triples
