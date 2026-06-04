from fastapi import FastAPI

from app.api.routes import chat, diagnosis, health, knowledge, llm


def create_app() -> FastAPI:
    app = FastAPI(title="Smart Support AI Service", version="0.1.0")
    app.include_router(llm.router)
    app.include_router(health.router)
    app.include_router(diagnosis.router)
    app.include_router(chat.router)
    app.include_router(knowledge.router)
    return app
