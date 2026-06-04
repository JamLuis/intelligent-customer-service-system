from typing import Any

from fastapi import APIRouter

from app.services import runtime

router = APIRouter()


@router.get("/llm/ping")
def llm_ping() -> dict[str, Any]:
    return runtime.llm_ping()


@router.post("/llm/check")
def llm_check(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.llm_check(request)
