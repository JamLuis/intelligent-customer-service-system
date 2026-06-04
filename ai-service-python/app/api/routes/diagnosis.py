from typing import Any

from fastapi import APIRouter

from app.services import runtime

router = APIRouter()


@router.post("/diagnosis/run")
def run_diagnosis(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.run_diagnosis(request)
