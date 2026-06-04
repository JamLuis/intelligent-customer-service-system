import json
from pathlib import Path

from app.main import build_blocks, embed_blocks, extract_candidates, parse_knowledge, answer_chat


SCORABLE_ENTITY_TYPES = {"ExtractedObject", "ExtractedIdentifier", "ExtractedQuantity"}
SCORABLE_RELATION_TYPES = {"HAS_COMPONENT", "HAS_IDENTIFIER", "HAS_QUANTITY", "HAS_VALUE", "MENTIONS"}


def default_taxonomy():
    return {
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
        ],
        "relationTypes": [
            {"relationType": "HAS_EVIDENCE", "fromEntityTypes": ["*"], "toEntityTypes": ["SourceBlock"]},
            {"relationType": "HAS_SECTION", "fromEntityTypes": ["Document"], "toEntityTypes": ["Section"]},
            {"relationType": "IN_SECTION", "fromEntityTypes": ["SourceBlock"], "toEntityTypes": ["Section"]},
        ],
    }


def snapshots():
    snapshot_path = Path(__file__).parent / "fixtures" / "extraction" / "api_snapshots.json"
    return json.loads(snapshot_path.read_text(encoding="utf-8"))


def entity_set(response):
    return {
        (entity["entityType"], entity["canonicalName"])
        for entity in response["candidateEntities"]
        if entity["entityType"] in SCORABLE_ENTITY_TYPES
    }


def relation_set(response):
    name_by_temp_id = {entity["tempId"]: entity["canonicalName"] for entity in response["candidateEntities"]}
    triples = set()
    for relation in response["candidateRelations"]:
        if relation["relationType"] not in SCORABLE_RELATION_TYPES:
            continue
        triples.add((name_by_temp_id[relation["sourceTempId"]], relation["relationType"], name_by_temp_id[relation["targetTempId"]]))
    return triples


def test_parse_markdown_snapshot():
    snapshot = snapshots()["parseMarkdown"]
    response = parse_knowledge(snapshot["request"])
    blocks = response["blocks"]

    assert response["sourceId"] == snapshot["expected"]["sourceId"]
    assert response["degraded"] is snapshot["expected"]["degraded"]
    assert len(blocks) == snapshot["expected"]["blockCount"]
    assert blocks[0]["blockType"] == snapshot["expected"]["firstBlockType"]
    assert blocks[0]["metadata"]["parser"] == snapshot["expected"]["firstBlockParser"]
    assert blocks[1]["sectionPath"] == snapshot["expected"]["secondBlockSectionPath"]


def test_extract_generic_snapshot():
    snapshot = snapshots()["extractGeneric"]
    request = snapshot["request"]
    blocks = build_blocks(request["rawText"], "text", request["sourceId"], request["graphCategoryId"])
    response = extract_candidates({
        "sourceId": request["sourceId"],
        "fileName": request["fileName"],
        "graphCategoryId": request["graphCategoryId"],
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": default_taxonomy(),
    })

    assert set(map(tuple, snapshot["expected"]["entities"])) == entity_set(response)
    assert set(map(tuple, snapshot["expected"]["relations"])) == relation_set(response)


def test_embed_empty_snapshot_avoids_external_model_call():
    snapshot = snapshots()["embedEmpty"]
    response = embed_blocks(snapshot["request"])

    assert response["embeddings"] == snapshot["expected"]["embeddings"]
    assert response["embeddingDim"] == snapshot["expected"]["embeddingDim"]
    assert response["embeddingVersion"] == snapshot["expected"]["embeddingVersion"]


def test_chat_no_evidence_snapshot_avoids_external_llm_call():
    snapshot = snapshots()["chatNoEvidence"]
    response = answer_chat(snapshot["request"])

    assert response["confidence"] == snapshot["expected"]["confidence"]
    assert response["cannotAnswerReason"] == snapshot["expected"]["cannotAnswerReason"]
    assert response["evidenceRefs"] == snapshot["expected"]["evidenceRefs"]
