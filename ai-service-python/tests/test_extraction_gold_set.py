import json
from pathlib import Path

from app.main import build_blocks, extract_candidates


SCORABLE_ENTITY_TYPES = {"ExtractedObject", "ExtractedIdentifier", "ExtractedQuantity"}
SCORABLE_RELATION_TYPES = {"HAS_COMPONENT", "HAS_IDENTIFIER", "HAS_QUANTITY", "HAS_VALUE", "MENTIONS"}
ENTITY_PRECISION_THRESHOLD = 0.90
RELATION_PRECISION_THRESHOLD = 0.85
EVIDENCE_SPAN_COVERAGE_THRESHOLD = 0.95
BAD_ENTITY_REJECT_THRESHOLD = 0.90


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


def load_cases():
    fixture_path = Path(__file__).parent / "fixtures" / "extraction" / "gold_cases.json"
    return json.loads(fixture_path.read_text(encoding="utf-8"))


def run_case(case):
    blocks = build_blocks(case["rawText"], case["sourceType"], case["sourceId"], case["graphCategoryId"])
    return extract_candidates({
        "sourceId": case["sourceId"],
        "fileName": case["fileName"],
        "graphCategoryId": case["graphCategoryId"],
        "blocks": blocks,
        "enableLlmExtract": False,
        "taxonomy": default_taxonomy(),
    })


def scorable_entities(response):
    return {
        (entity["entityType"], entity["canonicalName"])
        for entity in response["candidateEntities"]
        if entity["entityType"] in SCORABLE_ENTITY_TYPES
    }


def scorable_relation_triples(response):
    name_by_temp_id = {entity["tempId"]: entity["canonicalName"] for entity in response["candidateEntities"]}
    triples = set()
    for relation in response["candidateRelations"]:
        if relation["relationType"] not in SCORABLE_RELATION_TYPES:
            continue
        source = name_by_temp_id.get(relation["sourceTempId"])
        target = name_by_temp_id.get(relation["targetTempId"])
        if source and target:
            triples.add((source, relation["relationType"], target))
    return triples


def has_evidence(item):
    return bool(item.get("evidenceBlockIds")) or bool(item.get("evidence"))


def test_extraction_gold_set_quality_thresholds():
    cases = load_cases()
    entity_true_positive = 0
    entity_predicted = 0
    entity_expected = 0
    relation_true_positive = 0
    relation_predicted = 0
    relation_expected = 0
    bad_total = 0
    bad_rejected = 0
    evidence_total = 0
    evidence_covered = 0

    failures = []

    for case in cases:
        response = run_case(case)
        predicted_entities = scorable_entities(response)
        expected_entities = {tuple(item) for item in case["expectedEntities"]}
        predicted_relations = scorable_relation_triples(response)
        expected_relations = {tuple(item) for item in case["expectedRelations"]}

        missing_entities = expected_entities - predicted_entities
        extra_entities = predicted_entities - expected_entities
        missing_relations = expected_relations - predicted_relations
        extra_relations = predicted_relations - expected_relations
        if missing_entities:
            failures.append(f"{case['caseId']} missing entities: {sorted(missing_entities)}")
        if extra_entities:
            failures.append(f"{case['caseId']} extra entities: {sorted(extra_entities)}")
        if missing_relations:
            failures.append(f"{case['caseId']} missing relations: {sorted(missing_relations)}")
        if extra_relations:
            failures.append(f"{case['caseId']} extra relations: {sorted(extra_relations)}")

        entity_true_positive += len(predicted_entities & expected_entities)
        entity_predicted += len(predicted_entities)
        entity_expected += len(expected_entities)
        relation_true_positive += len(predicted_relations & expected_relations)
        relation_predicted += len(predicted_relations)
        relation_expected += len(expected_relations)

        predicted_names = {name for _, name in predicted_entities}
        for forbidden_name in case.get("forbiddenEntities", []):
            bad_total += 1
            if forbidden_name not in predicted_names:
                bad_rejected += 1
            else:
                failures.append(f"{case['caseId']} forbidden entity extracted: {forbidden_name}")

        for entity in response["candidateEntities"]:
            evidence_total += 1
            evidence_covered += 1 if has_evidence(entity) else 0
        for relation in response["candidateRelations"]:
            evidence_total += 1
            evidence_covered += 1 if has_evidence(relation) else 0

    entity_precision = entity_true_positive / entity_predicted if entity_predicted else 1.0
    entity_recall = entity_true_positive / entity_expected if entity_expected else 1.0
    relation_precision = relation_true_positive / relation_predicted if relation_predicted else 1.0
    relation_recall = relation_true_positive / relation_expected if relation_expected else 1.0
    bad_entity_reject_rate = bad_rejected / bad_total if bad_total else 1.0
    evidence_span_coverage = evidence_covered / evidence_total if evidence_total else 1.0

    assert entity_precision >= ENTITY_PRECISION_THRESHOLD, (entity_precision, failures)
    assert entity_recall == 1.0, (entity_recall, failures)
    assert relation_precision >= RELATION_PRECISION_THRESHOLD, (relation_precision, failures)
    assert relation_recall == 1.0, (relation_recall, failures)
    assert bad_entity_reject_rate >= BAD_ENTITY_REJECT_THRESHOLD, (bad_entity_reject_rate, failures)
    assert evidence_span_coverage >= EVIDENCE_SPAN_COVERAGE_THRESHOLD, (evidence_span_coverage, failures)
    assert not failures
