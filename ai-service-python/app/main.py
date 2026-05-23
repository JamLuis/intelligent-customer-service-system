from typing import Any

from fastapi import FastAPI, HTTPException

from app.clients.llm_client import LLMClientError, get_llm_client
from app.extractors.canonical_resolver import CanonicalSignals, score_breakdown
from app.extractors.evidence import build_evidence
from app.retrieval.graph_retrieval_planner import build_retrieve_plan
from app.settings import get_settings

app = FastAPI(title="Smart Support AI Service", version="0.1.0")


@app.get("/llm/ping")
def llm_ping() -> dict[str, Any]:
    """Smoke test: hit chat + embeddings with current LLM_* env."""
    s = get_settings()
    try:
        client = get_llm_client()
        reply = client.chat(
            [{"role": "user", "content": "ping"}], max_tokens=8, temperature=0
        )
        vecs = client.embeddings(["ping"])
    except LLMClientError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    return {
        "provider": s.llm_provider,
        "llmModel": s.llm_model,
        "chatReply": reply[:64],
        "embeddingModel": s.embedding_model,
        "embeddingDim": len(vecs[0]) if vecs else 0,
        "embeddingVersion": s.embedding_version,
    }

# 本服务为无状态计算服务：
#   - 解析、抽取、归一化、检索规划等真实逻辑见任务 KG-AI-001 ~ KG-AI-005
#   - 严禁内置任何业务本体（设备/船舶/告警/规则…）字符串
#   - 所有可用 entityType / relationType / categoryId 必须由调用方在请求 taxonomy 中显式传入


@app.get("/health")
def health() -> dict[str, str]:
    return {"service": "ai-service-python", "status": "ok"}


@app.get("/capabilities")
def capabilities() -> dict[str, list[str]]:
    return {
        "modules": [
            "document_parser",
            "entity_extractor",
            "graph_builder",
            "rag",
            "diagnosis_agent",
        ]
    }


@app.post("/diagnosis/run")
def run_diagnosis(request: dict[str, Any]) -> dict[str, Any]:
    """诊断真实实现见 KG-AI-005。当前返回占位结构，禁止携带任何业务实体名。"""
    degraded = request.get("mockScenario") == "mcp_degraded"
    return {
        "status": "partial" if degraded else "not_implemented",
        "rootCause": "",
        "confidenceScore": 0.0,
        "graphPaths": [],
        "reasoningSteps": [],
        "recommendedActions": [],
        "degraded": degraded,
        "implHint": "KG-AI-005 待实现",
    }


@app.post("/knowledge/parse")
def parse_knowledge(request: dict[str, Any]) -> dict[str, Any]:
    """解析真实实现见 KG-AI-001。当前返回空 blocks。"""
    return {
        "sourceId": request.get("sourceId"),
        "blocks": [],
        "implHint": "KG-AI-001 待实现",
    }


@app.post("/knowledge/embed")
def embed_blocks(request: dict[str, Any]) -> dict[str, Any]:
    """KG-AI-002 MVP: 调用 OpenAI 兼容 embeddings 端点，返回 (blockId, vector) 列表。

    入参:
      { "blocks": [ {"blockId": "...", "text": "..."}, ... ] }
    出参:
      { "embeddings": [ {"blockId": "...", "vector": [...]} ],
        "embeddingModel": str, "embeddingVersion": str, "embeddingDim": int }
    维度受 EMBEDDING_DIM 约束（默认 1536，对齐 KG-DB-001 pgvector schema）。
    """
    blocks = request.get("blocks") or []
    if not isinstance(blocks, list):
        raise HTTPException(status_code=422, detail="blocks must be an array")
    texts: list[str] = []
    block_ids: list[str] = []
    for i, b in enumerate(blocks):
        if not isinstance(b, dict):
            raise HTTPException(status_code=422, detail=f"blocks[{i}] must be object")
        text = (b.get("text") or "").strip()
        if not text:
            raise HTTPException(status_code=422, detail=f"blocks[{i}].text is empty")
        texts.append(text)
        block_ids.append(str(b.get("blockId") or i))
    s = get_settings()
    if not texts:
        return {
            "embeddings": [],
            "embeddingModel": s.embedding_model,
            "embeddingVersion": s.embedding_version,
            "embeddingDim": s.embedding_dim,
        }
    try:
        vectors = get_llm_client().embeddings(texts)
    except LLMClientError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    return {
        "embeddings": [
            {"blockId": bid, "vector": vec} for bid, vec in zip(block_ids, vectors)
        ],
        "embeddingModel": s.embedding_model,
        "embeddingVersion": s.embedding_version,
        "embeddingDim": s.embedding_dim,
    }


@app.post("/knowledge/extract")
def extract_candidates(request: dict[str, Any]) -> dict[str, Any]:
    """Rule + Dict + LLM 抽取真实实现见 KG-AI-003。
    入参必须携带 blocks + taxonomy + graphCategoryId，本接口禁止凭空生成名称。"""
    blocks = request.get("blocks") or []
    candidate_entities = []
    for block in blocks:
        if not isinstance(block, dict):
            continue
        text = (block.get("normalizedText") or block.get("rawText") or block.get("text") or "").strip()
        if not text:
            continue
        evidence = build_evidence(block)
        if not evidence["blockId"]:
            continue
        # KG-AI-003 的 LLM 抽取仍待细化；这里先输出可保存的 weighted evidence 空候选骨架。
        candidate_entities.append({
            "candidateId": None,
            "blockId": evidence["blockId"],
            "graphCategoryId": request.get("graphCategoryId"),
            "entityType": None,
            "rawName": None,
            "canonicalName": None,
            "uniqueKey": {},
            "properties": {},
            "evidence": [evidence],
            "evidenceBlockIds": [evidence["blockId"]],
            "confidence": 0.0,
            "extractor": "pending_llm",
            "status": "candidate",
        })
    return {
        "candidateEntities": candidate_entities,
        "candidateRelations": [],
        "implHint": "KG-AI-003 LLM 抽取待实现；KG-AI-006 weighted evidence 已接入",
    }


@app.post("/knowledge/normalize")
def normalize_candidates(request: dict[str, Any]) -> dict[str, Any]:
    """别名归一 / Cross link / Conflict detection 真实实现见 KG-AI-003。"""
    frozen_ids = {str(x) for x in (request.get("frozenCandidateIds") or [])}
    candidates = request.get("candidates") or []
    normalized = []
    for item in candidates:
        if not isinstance(item, dict):
            continue
        candidate_id = str(item.get("candidateId") or "")
        if candidate_id and candidate_id in frozen_ids:
            normalized.append({**item, "skipped": True, "reason": "frozen"})
            continue
        signals = item.get("signals") or {}
        breakdown = score_breakdown(CanonicalSignals(
            unique_key=bool(signals.get("uniqueKey")),
            alias=bool(signals.get("alias")),
            regex=bool(signals.get("regex")),
            embedding=float(signals.get("embedding") or 0),
            code_graph_ref=bool(signals.get("codeGraphRef")),
            llm_verify=bool(signals.get("llmVerify")),
        ))
        normalized.append({**item, "scoreBreakdown": breakdown, "crossLinkHint": breakdown["crossLinkHint"]})
    return {
        "normalized": {"entities": normalized, "relations": []},
        "conflicts": [],
        "implHint": "KG-AI-007 scoreBreakdown/frozen skip 已接入；完整归一策略待 Java 表决链联调",
    }


@app.post("/graphs/retrieve-plan")
def retrieve_plan(request: dict[str, Any]) -> dict[str, Any]:
    """检索规划真实实现见 KG-AI-004。"""
    s = get_settings()
    plan = build_retrieve_plan(
        str(request.get("questionText") or ""),
        request.get("keywords") if isinstance(request.get("keywords"), list) else None,
    )
    return {
        **plan,
        "embeddingModel": s.embedding_model,
        "embeddingVersion": s.embedding_version,
        "implHint": "KG-AI-004 planner 待细化；KG-AI-006 keywords/vectorQueryText fallback 已接入",
    }
