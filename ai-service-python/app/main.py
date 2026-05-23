from typing import Any

from fastapi import FastAPI

app = FastAPI(title="Smart Support AI Service", version="0.1.0")


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
    degraded = request.get("mockScenario") == "mcp_degraded"
    return {
        "status": "partial" if degraded else "completed",
        "rootCause": "实时证据部分不可用，已基于图谱与静态知识生成降级诊断" if degraded else "设备告警规则阈值与现场状态不一致，导致告警未触发",
        "confidenceScore": 71.5 if degraded else 88.0,
        "graphPaths": [
            {
                "nodes": ["device:TC-003", "alarmRule:AR-17"],
                "relation": "BOUND_TO",
                "sourceRef": "graph-device-alarm",
                "confidence": 0.89,
            }
        ],
        "reasoningSteps": [
            {
                "stepType": "rag",
                "stepName": "检索告警规则知识",
                "status": "success",
                "outputSummary": "命中设备 TC-003 关联规则 AR-17",
                "durationMs": 38,
            },
            {
                "stepType": "agent",
                "stepName": "生成根因与建议动作",
                "status": "success",
                "outputSummary": "建议核对告警阈值与启用状态",
                "durationMs": 64,
            },
        ],
        "recommendedActions": [
            {
                "actionCode": "CHECK_ALARM_RULE",
                "riskLevel": "L1",
                "summary": "核对告警规则阈值与启用状态",
            }
        ],
    }


@app.post("/knowledge/ingest")
def ingest_knowledge(request: dict[str, Any]) -> dict[str, Any]:
    text = str(request.get("rawText") or "")
    return {
        "sourceId": request.get("sourceId"),
        "parserStatus": "success",
        "extractStatus": "success",
        "graphBuildStatus": "success",
        "tasks": [
            {"taskType": "parse", "status": "success", "progress": 100, "pages": max(1, len(text) // 200)},
            {"taskType": "extract", "status": "success", "progress": 100, "entities": 6},
            {"taskType": "graph_build", "status": "success", "progress": 100, "nodes": 8, "edges": 10},
        ],
    }


@app.post("/graphs/query")
def query_graph(request: dict[str, Any]) -> dict[str, Any]:
    return {
        "items": [
            {
                "graphId": "graph-device-alarm",
                "graphName": "设备告警关系子图",
                "nodes": [
                    {"id": "device:TC-003", "label": "设备 TC-003", "type": "device"},
                    {"id": "rule:AR-17", "label": "告警规则 AR-17", "type": "alarmRule"},
                ],
                "edges": [
                    {"source": "device:TC-003", "target": "rule:AR-17", "type": "BOUND_TO"}
                ],
                "sourceRefs": ["knowledge:alarm-rule-doc"],
                "confidence": 0.89,
                "activeRevisionId": "rev-3",
            }
        ]
    }