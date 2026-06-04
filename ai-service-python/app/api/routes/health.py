from fastapi import APIRouter

from app.services import runtime

router = APIRouter()


@router.get("/health")
def health() -> dict[str, str]:
    return runtime.health()


@router.get("/capabilities")
def capabilities() -> dict[str, list[str]]:
    return runtime.capabilities()
