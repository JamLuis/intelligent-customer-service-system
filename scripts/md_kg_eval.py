#!/usr/bin/env python3
"""Evaluate Markdown KG extraction through direct AI and full system paths."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
AI_SERVICE_ROOT = REPO_ROOT / "ai-service-python"
sys.path.insert(0, str(AI_SERVICE_ROOT))

from app.main import build_blocks, extract_candidates  # noqa: E402


DEFAULT_BASE_URL = "http://localhost:8088/api"
DEFAULT_PROJECT_ID = "P001"
DEFAULT_TOKEN = "mock-token"


@dataclass(frozen=True)
class GoldCase:
    name: str
    file_name: str
    markdown: str
    expected_entities: set[str]
    expected_relations: set[tuple[str, str, str]]


GOLD_CASES: dict[str, GoldCase] = {
    "finance": GoldCase(
        name="finance",
        file_name="gold-finance-model.md",
        markdown="""# 金融大模型能力评测与可用性分析

## 评测对象
金融大模型需要覆盖知识问答、风险识别、合规审查和报告生成。

## 模型选择
Qwen 本地模型适合离线初筛，云模型适合高准确率复核。

## 运行策略
离线初筛用于批量资料预处理，高准确率复核用于最终报告确认。
""",
        expected_entities={"金融大模型", "知识问答", "风险识别", "合规审查", "报告生成", "Qwen 本地模型", "云模型", "离线初筛", "高准确率复核"},
        expected_relations={
            ("金融大模型", "HAS_COMPONENT", "知识问答"),
            ("金融大模型", "HAS_COMPONENT", "风险识别"),
            ("金融大模型", "HAS_COMPONENT", "合规审查"),
            ("金融大模型", "HAS_COMPONENT", "报告生成"),
            ("Qwen 本地模型", "HAS_VALUE", "离线初筛"),
            ("云模型", "HAS_VALUE", "高准确率复核"),
        },
    ),
    "ops": GoldCase(
        name="ops",
        file_name="gold-ops-runbook.md",
        markdown="""# 运维知识库导入流程

## 导入流程
运维知识库包含告警手册、巡检记录、处置脚本和回滚方案。

## 质量要求
系统需要保留源文件、章节、段落和证据块，所有候选关系必须能追溯原文。

## 性能要求
小文档应在三十秒内完成候选关系生成，大文档应先展示结构图再后台补充语义关系。
""",
        expected_entities={"运维知识库", "告警手册", "巡检记录", "处置脚本", "回滚方案", "源文件", "章节", "段落", "证据块", "结构图", "语义关系"},
        expected_relations={
            ("运维知识库", "HAS_COMPONENT", "告警手册"),
            ("运维知识库", "HAS_COMPONENT", "巡检记录"),
            ("运维知识库", "HAS_COMPONENT", "处置脚本"),
            ("运维知识库", "HAS_COMPONENT", "回滚方案"),
            ("候选关系", "HAS_EVIDENCE", "原文"),
        },
    ),
}


def fallback_taxonomy() -> dict[str, Any]:
    return {
        "categories": [{"categoryId": "uncategorized", "entityTypeScope": [], "relationTypeScope": []}],
        "entityTypes": [
            {"entityType": "Document"},
            {"entityType": "Section"},
            {"entityType": "SourceBlock"},
            {"entityType": "ExtractedObject"},
            {"entityType": "ExtractedIdentifier"},
            {"entityType": "ExtractedQuantity"},
        ],
        "relationTypes": [
            {"relationType": "HAS_SECTION"},
            {"relationType": "IN_SECTION"},
            {"relationType": "HAS_EVIDENCE"},
            {"relationType": "HAS_COMPONENT"},
            {"relationType": "HAS_IDENTIFIER"},
            {"relationType": "HAS_QUANTITY"},
            {"relationType": "HAS_VALUE"},
            {"relationType": "MENTIONS"},
        ],
    }


def request_json(method: str, url: str, body: dict[str, Any] | None = None, *, project_id: str, token: str) -> dict[str, Any]:
    headers = {
        "Authorization": f"Bearer {token}",
        "X-Project-Id": project_id,
        "X-Request-Id": f"md-eval-{int(time.time() * 1000)}",
    }
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
        headers["X-Idempotency-Key"] = f"md-eval-{int(time.time() * 1000)}"
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        error_body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code} {method} {url}: {error_body}") from error


def fetch_taxonomy(base_url: str, project_id: str, token: str) -> dict[str, Any]:
    try:
        payload = request_json("GET", f"{base_url}/v1/graphs/assets/categories", project_id=project_id, token=token)
        return payload.get("data") or fallback_taxonomy()
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        return fallback_taxonomy()


def run_direct(case: GoldCase, taxonomy: dict[str, Any], enable_llm: bool) -> dict[str, Any]:
    started = time.perf_counter()
    blocks = build_blocks(case.markdown, "md", f"direct-{case.name}", "uncategorized")
    parsed_ms = int((time.perf_counter() - started) * 1000)
    extract_started = time.perf_counter()
    response = extract_candidates({
        "sourceId": f"direct-{case.name}",
        "fileName": case.file_name,
        "graphCategoryId": "uncategorized",
        "blocks": blocks,
        "taxonomy": taxonomy,
        "enableLlmExtract": enable_llm,
    })
    extract_ms = int((time.perf_counter() - extract_started) * 1000)
    return build_result("direct", case, blocks, response.get("candidateEntities") or [], response.get("candidateRelations") or [], parsed_ms, extract_ms, response)


def run_system(case: GoldCase, base_url: str, project_id: str, token: str, cleanup: bool) -> dict[str, Any]:
    started = time.perf_counter()
    source_hash = f"md-eval-{case.name}-{int(time.time() * 1000)}"
    source_payload = {
        "sourceType": "md",
        "fileName": case.file_name,
        "rawText": case.markdown,
        "fileSizeBytes": len(case.markdown.encode("utf-8")),
        "sensitivityLevel": "internal",
        "overwriteDuplicate": True,
        "sourceHash": source_hash,
    }
    source = request_json("POST", f"{base_url}/v1/knowledge/sources", source_payload, project_id=project_id, token=token)["data"]
    source_id = source["sourceId"]
    total_ms = int((time.perf_counter() - started) * 1000)
    tasks = request_json("GET", f"{base_url}/v1/knowledge/sources/{source_id}/tasks", project_id=project_id, token=token)["data"]["items"]
    blocks = request_json("GET", f"{base_url}/v1/knowledge/sources/{source_id}/blocks?pageSize=100", project_id=project_id, token=token)["data"]["items"]
    candidates = request_json("GET", f"{base_url}/v1/knowledge/sources/{source_id}/candidates", project_id=project_id, token=token)["data"]
    graphs = request_json("GET", f"{base_url}/v1/graphs/assets?sourceId={source_id}&pageSize=20", project_id=project_id, token=token)["data"]["items"]
    task_payload = {item["taskType"]: item for item in tasks}
    result = build_result(
        "system",
        case,
        blocks,
        candidates.get("entities") or [],
        candidates.get("relations") or [],
        int((task_payload.get("parse", {}).get("resultPayload") or {}).get("durationMs") or 0),
        int((task_payload.get("extract", {}).get("resultPayload") or {}).get("durationMs") or 0),
        {"sourceId": source_id, "tasks": tasks, "graphs": graphs, "totalMs": total_ms},
    )
    result["sourceId"] = source_id
    result["graphCountForSource"] = len(graphs)
    result["graphIds"] = [item.get("graphId") for item in graphs]
    result["totalMs"] = total_ms
    if cleanup:
        for graph_id in result["graphIds"]:
            if graph_id:
                try:
                    request_json("DELETE", f"{base_url}/v1/graphs/assets/{graph_id}", {}, project_id=project_id, token=token)
                except Exception:
                    pass
    return result


def build_result(kind: str, case: GoldCase, blocks: list[dict[str, Any]], entities: list[dict[str, Any]], relations: list[dict[str, Any]], parse_ms: int, extract_ms: int, raw: dict[str, Any]) -> dict[str, Any]:
    semantic_types = {"ExtractedObject", "ExtractedIdentifier", "ExtractedQuantity"}
    names = {str(item.get("canonicalName") or item.get("rawName") or "") for item in entities}
    semantic_names = {str(item.get("canonicalName") or item.get("rawName") or "") for item in entities if str(item.get("entityType") or "") in semantic_types}
    source_by_id = {str(item.get("candidateId") or item.get("tempId") or ""): str(item.get("canonicalName") or item.get("rawName") or "") for item in entities}
    triples = set()
    for relation in relations:
        source_name = source_by_id.get(str(relation.get("sourceCandidateId") or relation.get("sourceTempId") or ""), str(relation.get("source") or ""))
        target_name = source_by_id.get(str(relation.get("targetCandidateId") or relation.get("targetTempId") or ""), str(relation.get("target") or ""))
        triples.add((source_name, str(relation.get("relationType") or ""), target_name))
    entity_hits = fuzzy_hits(case.expected_entities, semantic_names)
    relation_hits = fuzzy_relation_hits(case.expected_relations, triples)
    return {
        "kind": kind,
        "case": case.name,
        "blockCount": len(blocks),
        "entityCount": len(entities),
        "relationCount": len(relations),
        "expectedEntityCount": len(case.expected_entities),
        "expectedRelationCount": len(case.expected_relations),
        "entityRecall": round(len(entity_hits) / max(1, len(case.expected_entities)), 4),
        "relationRecall": round(len(relation_hits) / max(1, len(case.expected_relations)), 4),
        "entityHits": sorted(entity_hits),
        "relationHits": sorted(["|".join(item) for item in relation_hits]),
        "semanticEntityCount": len(semantic_names),
        "extraEntityCount": max(0, len(semantic_names) - len(entity_hits)),
        "parseMs": parse_ms,
        "extractMs": extract_ms,
        "rawMeta": raw,
    }


def fuzzy_hits(expected: set[str], actual: set[str]) -> set[str]:
    hits = set()
    for item in expected:
        if any(item == candidate or item in candidate or candidate in item for candidate in actual if candidate):
            hits.add(item)
    return hits


def fuzzy_relation_hits(expected: set[tuple[str, str, str]], actual: set[tuple[str, str, str]]) -> set[tuple[str, str, str]]:
    hits = set()
    for source, relation_type, target in expected:
        for actual_source, actual_relation_type, actual_target in actual:
            if relation_type != actual_relation_type:
                continue
            if fuzzy_text_match(source, actual_source) and fuzzy_text_match(target, actual_target):
                hits.add((source, relation_type, target))
                break
    return hits


def fuzzy_text_match(expected: str, actual: str) -> bool:
    return bool(expected and actual and (expected == actual or expected in actual or actual in expected))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", choices=[*GOLD_CASES.keys(), "all"], default="all")
    parser.add_argument("--mode", choices=["direct", "system", "both"], default="both")
    parser.add_argument("--base-url", default=os.environ.get("ICSS_API_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--project-id", default=os.environ.get("ICSS_PROJECT_ID", DEFAULT_PROJECT_ID))
    parser.add_argument("--token", default=os.environ.get("ICSS_TOKEN", DEFAULT_TOKEN))
    parser.add_argument("--llm", action="store_true", help="Enable LLM for direct AI extraction")
    parser.add_argument("--keep-graphs", action="store_true")
    parser.add_argument("--output", default="")
    args = parser.parse_args()

    cases = list(GOLD_CASES.values()) if args.case == "all" else [GOLD_CASES[args.case]]
    taxonomy = fetch_taxonomy(args.base_url, args.project_id, args.token)
    results: list[dict[str, Any]] = []
    for case in cases:
        if args.mode in {"direct", "both"}:
            results.append(run_direct(case, taxonomy, args.llm))
        if args.mode in {"system", "both"}:
            results.append(run_system(case, args.base_url, args.project_id, args.token, cleanup=not args.keep_graphs))
    payload = {"results": results}
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(text + "\n", encoding="utf-8")
    print(text)
    failed = []
    for item in results:
        if item["entityRecall"] < 0.5:
            failed.append(item)
            continue
        if item["kind"] == "system" and item.get("graphCountForSource", 0) == 0:
            failed.append(item)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())