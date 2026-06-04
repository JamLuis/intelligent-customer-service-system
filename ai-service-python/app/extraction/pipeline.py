from typing import Any

from app.services import runtime


def extract_candidates(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.extract_candidates(request)


def normalize_candidates(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.normalize_candidates(request)
