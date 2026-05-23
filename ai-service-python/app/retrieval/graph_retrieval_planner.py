from __future__ import annotations

import re


def simple_tokenize(question_text: str | None) -> list[str]:
    text = (question_text or "").strip()
    if not text:
        return ["empty"]
    tokens = [t for t in re.split(r"[^\w\u4e00-\u9fff]+", text) if t]
    return tokens or ["empty"]


def build_retrieve_plan(question_text: str | None, requested_keywords: list[str] | None = None) -> dict:
    keywords = [k.strip() for k in (requested_keywords or []) if k and k.strip()]
    if not keywords:
        keywords = simple_tokenize(question_text)
    return {
        "plan": {"steps": [{"type": "hybrid_retrieval", "statusFilter": ["published", "frozen"]}]},
        "keywords": keywords,
        "vectorQueryText": (question_text or "").strip(),
    }