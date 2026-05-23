from typing import Any

from fastapi import FastAPI

app = FastAPI(title="Smart Support AI Service", version="0.1.0")

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
    """Embedding 真实实现见 KG-AI-002。当前返回空向量。"""
    return {
        "embeddings": [],
        "implHint": "KG-AI-002 待实现",
    }


@app.post("/knowledge/extract")
def extract_candidates(request: dict[str, Any]) -> dict[str, Any]:
    """Rule + Dict + LLM 抽取真实实现见 KG-AI-003。
    入参必须携带 blocks + taxonomy + graphCategoryId，本接口禁止凭空生成名称。"""
    return {
        "candidateEntities": [],
        "candidateRelations": [],
        "implHint": "KG-AI-003 待实现",
    }


@app.post("/knowledge/normalize")
def normalize_candidates(request: dict[str, Any]) -> dict[str, Any]:
    """别名归一 / Cross link / Conflict detection 真实实现见 KG-AI-003。"""
    return {
        "normalized": {"entities": [], "relations": []},
        "conflicts": [],
        "implHint": "KG-AI-003 待实现",
    }


@app.post("/graphs/retrieve-plan")
def retrieve_plan(request: dict[str, Any]) -> dict[str, Any]:
    """检索规划真实实现见 KG-AI-004。"""
    return {
        "plan": {"steps": []},
        "vectorQueryText": str(request.get("questionText") or ""),
        "implHint": "KG-AI-004 待实现",
    }
