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
        headers = {
            "Content-Type": "application/json",
        }
        if self.s.llm_api_key:
            headers["Authorization"] = f"Bearer {self.s.llm_api_key}"
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
        try:
            resp = self._client.post("/embeddings", json=payload)
        except httpx.HTTPError as ex:
            raise LLMClientError(f"embeddings request failed: {ex}") from ex
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

    def models(self) -> list[str]:
        try:
            resp = self._client.get("/models")
        except httpx.HTTPError as ex:
            raise LLMClientError(f"models request failed: {ex}") from ex
        if resp.status_code >= 400:
            raise LLMClientError(f"models HTTP {resp.status_code}: {resp.text[:300]}")
        data = resp.json().get("data") or []
        return [str(item.get("id")) for item in data if isinstance(item, dict) and item.get("id")]

    def chat(self, messages: list[dict], *, max_tokens: int = 512, temperature: float = 0.2, think: bool | None = None) -> str:
        # For thinking models on Ollama: use native /api/chat with think=false to avoid
        # reasoning consuming all tokens and leaving content empty
        if think is False and self._is_ollama_base():
            return self._chat_ollama_native(messages, max_tokens=max_tokens, temperature=temperature)
        payload = {
            "model": self.s.llm_model,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        try:
            resp = self._client.post("/chat/completions", json=payload)
        except httpx.HTTPError as ex:
            raise LLMClientError(f"chat request failed: {ex}") from ex
        if resp.status_code >= 400:
            raise LLMClientError(f"chat HTTP {resp.status_code}: {resp.text[:300]}")
        choices = resp.json().get("choices") or []
        if not choices:
            raise LLMClientError("chat returned empty choices")
        msg = choices[0]["message"]
        content = msg.get("content") or ""
        if not content.strip():
            reasoning = msg.get("reasoning") or ""
            if reasoning.strip():
                return reasoning.strip()
        return content

    def _is_ollama_base(self) -> bool:
        base = self.s.llm_api_base_url or ""
        return "11434" in base

    def _chat_ollama_native(self, messages: list[dict], *, max_tokens: int, temperature: float) -> str:
        """Call Ollama's native /api/chat with think=false to disable reasoning."""
        base = self.s.llm_api_base_url.rstrip("/")
        # Strip /v1 suffix to get Ollama root
        if base.endswith("/v1"):
            base = base[:-3]
        url = base.rstrip("/") + "/api/chat"
        payload = {
            "model": self.s.llm_model,
            "messages": messages,
            "think": False,
            "stream": False,
            "options": {
                "num_predict": max_tokens,
                "temperature": temperature,
            },
        }
        try:
            resp = httpx.post(url, json=payload, timeout=self.s.http_timeout_s,
                              headers={"Content-Type": "application/json"})
        except httpx.HTTPError as ex:
            raise LLMClientError(f"chat (ollama native) request failed: {ex}") from ex
        if resp.status_code >= 400:
            raise LLMClientError(f"chat (ollama native) HTTP {resp.status_code}: {resp.text[:300]}")
        data = resp.json()
        msg = data.get("message") or {}
        return msg.get("content") or ""


_singleton: LLMClient | None = None


def get_llm_client() -> LLMClient:
    global _singleton
    if _singleton is None:
        _singleton = LLMClient()
    return _singleton
