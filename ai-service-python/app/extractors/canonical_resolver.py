from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class CanonicalSignals:
    unique_key: bool = False
    alias: bool = False
    regex: bool = False
    embedding: float = 0.0
    code_graph_ref: bool = False
    llm_verify: bool = False


def _hit(value: bool) -> float:
    return 1.0 if value else 0.0


def _clamp(value: float | int | None) -> float:
    if value is None:
        return 0.0
    try:
        return max(0.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return 0.0


def score_breakdown(signals: CanonicalSignals) -> dict:
    unique_key = 0.40 * _hit(signals.unique_key)
    alias = 0.20 * _hit(signals.alias)
    regex = 0.15 * _hit(signals.regex)
    embedding = 0.10 * _clamp(signals.embedding)
    code_graph_ref = 0.10 * _hit(signals.code_graph_ref)
    llm_verify = 0.05 * _hit(signals.llm_verify)
    total = unique_key + alias + regex + embedding + code_graph_ref + llm_verify
    deterministic = any([unique_key, alias, regex])
    merge_allowed = total >= 0.85 and deterministic
    embedding_only = embedding > 0 and not any([unique_key, alias, regex, code_graph_ref, llm_verify])
    return {
        "uniqueKey": unique_key,
        "alias": alias,
        "regex": regex,
        "embedding": embedding,
        "codeGraphRef": code_graph_ref,
        "llmVerify": llm_verify,
        "total": total,
        "mergeAllowed": merge_allowed,
        "crossLinkHint": embedding_only and _clamp(signals.embedding) >= 0.85,
    }