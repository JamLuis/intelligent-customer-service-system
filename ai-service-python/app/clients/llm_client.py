"""OpenAI-compatible LLM + embedding client.

Targets Aliyun Bailian (DashScope) compatible-mode endpoint by default, but any
OpenAI-compatible base URL works (DeepSeek, Moonshot, vLLM gateways, etc.).

Embedding model + dim are pinned via env (EMBEDDING_MODEL / EMBEDDING_DIM /
EMBEDDING_VERSION); KG-DB-001 pgvector(1536) is the source of truth.
"""
from __future__ import annotations

from typing import Iterable

import httpx

from app.settings import Settings, get_settings


class LLMClientError(RuntimeError):
    pass


class LLMClient:
    def __init__(self, settings: Settings | None = None) -> None:
        self.s = settings or get_settings()
        if not self.s.llm_api_key:
            raise LLMClientError("LLM_API_KEY is empty; refuse to start")
        headers = {
            "Authorization": f"Bearer {self.s.llm_api_key}",
            "Content-Type": "application/json",
        }
        if self.s.llm_workspace_id:
            headers["X-DashScope-WorkSpace"] = self.s.llm_workspace_id
        self._client = httpx.Client(
            base_url=self.s.llm_api_base_url.rstrip("/"),
            headers=headers,
            timeout=self.s.http_timeout_s,
        )

    def close(self) -> None:
        self._client.close()

    def embeddings(self, texts: Iterable[str]) -> list[list[float]]:
        payload = {
            "model": self.s.embedding_model,
            "input": list(texts),
            "dimensions": self.s.embedding_dim,
        }
        resp = self._client.post("/embeddings", json=payload)
        if resp.status_code >= 400:
            raise LLMClientError(f"embeddings HTTP {resp.status_code}: {resp.text[:300]}")
        data = resp.json().get("data") or []
        vectors = [item["embedding"] for item in data]
        for v in vectors:
            if len(v) != self.s.embedding_dim:
                raise LLMClientError(
                    f"embedding dim mismatch: got {len(v)} expected {self.s.embedding_dim}"
                )
        return vectors

    def chat(self, messages: list[dict], *, max_tokens: int = 512, temperature: float = 0.2) -> str:
        payload = {
            "model": self.s.llm_model,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        resp = self._client.post("/chat/completions", json=payload)
        if resp.status_code >= 400:
            raise LLMClientError(f"chat HTTP {resp.status_code}: {resp.text[:300]}")
        choices = resp.json().get("choices") or []
        if not choices:
            raise LLMClientError("chat returned empty choices")
        return choices[0]["message"]["content"] or ""


_singleton: LLMClient | None = None


def get_llm_client() -> LLMClient:
    global _singleton
    if _singleton is None:
        _singleton = LLMClient()
    return _singleton
