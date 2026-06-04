from app.main import build_blocks, extract_candidates, parse_knowledge
from app.preprocess.routing import resolve_parser


def default_taxonomy():
    return {
        "categories": [{"categoryId": "uncategorized", "entityTypeScope": [], "relationTypeScope": []}],
        "entityTypes": [{"entityType": "Document"}, {"entityType": "Section"}, {"entityType": "SourceBlock"}],
        "relationTypes": [
            {"relationType": "HAS_EVIDENCE", "fromEntityTypes": ["*"], "toEntityTypes": ["SourceBlock"]},
            {"relationType": "HAS_SECTION", "fromEntityTypes": ["Document"], "toEntityTypes": ["Section"]},
            {"relationType": "IN_SECTION", "fromEntityTypes": ["SourceBlock"], "toEntityTypes": ["Section"]},
        ],
    }


def test_parser_routing_uses_file_suffix_for_text_sources():
    assert resolve_parser("text", "demo.md").parser_name == "md"
    assert resolve_parser("text", "schema.sql").parser_name == "sql"
    assert resolve_parser("text", "table.csv").parser_name == "csv"
    assert resolve_parser("unknown", "note.unknown").parser_name == "text"


def test_parse_response_contains_artifact_metadata_quality_and_block_enrichment():
    response = parse_knowledge({
        "sourceId": "phase2-source",
        "sourceType": "text",
        "fileName": "phase2.md",
        "graphCategoryId": "uncategorized",
        "rawText": "# Phase2\n\n系统需要覆盖解析、抽取和质检。",
    })

    assert response["artifact"]["parser"] == "markdown"
    assert response["artifact"]["metadata"]["documentHash"]
    assert response["artifact"]["quality"]["score"] > 0
    block = response["blocks"][1]
    assert block["summary"] == "系统需要覆盖解析、抽取和质检。"
    assert block["qualityScore"] == 1.0
    assert block["qualityFlags"] == []
    assert block["metadata"]["chunkIndex"] == 2
    assert block["metadata"]["span"]["lineNo"] == 3
    assert block["metadata"]["sourceType"] == "md"


def test_long_text_is_split_into_chunks_with_part_metadata():
    raw_text = "长文本" * 700
    blocks = build_blocks(raw_text, "text", "long-source", "uncategorized")

    assert len(blocks) > 1
    assert blocks[0]["metadata"]["chunkPart"] == 1
    assert blocks[1]["metadata"]["chunkPart"] == 2
    assert all(len(block["normalizedText"]) <= 1200 for block in blocks)


def test_candidate_entities_and_relations_include_phase2_governance_fields():
    blocks = build_blocks("塔吊A21212 有5个摄像头设备，编号分别为 C123123", "text", "phase2-extract", "uncategorized")
    response = extract_candidates({
        "sourceId": "phase2-extract",
        "fileName": "device.txt",
        "graphCategoryId": "uncategorized",
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": default_taxonomy(),
    })

    entity = next(item for item in response["candidateEntities"] if item["canonicalName"] == "塔吊A21212")
    relation = next(item for item in response["candidateRelations"] if item["relationType"] == "HAS_COMPONENT")

    assert entity["mentionSpans"][0]["blockId"] == blocks[0]["blockId"]
    assert entity["evidenceRefs"] == entity["evidence"]
    assert entity["confidenceBreakdown"]["total"] == entity["confidence"]
    assert entity["mergeKey"] == "ExtractedObject:塔吊a21212"
    assert entity["reviewStatus"] == entity["status"]
    assert isinstance(entity["qualityFlags"], list)

    assert relation["evidenceSpan"]["blockId"] == blocks[0]["blockId"]
    assert relation["confidenceBreakdown"]["total"] == relation["confidence"]
    assert relation["dedupKey"]
    assert relation["direction"] == "forward"
    assert relation["reviewStatus"] == relation["status"]
    assert isinstance(relation["validationFlags"], list)
