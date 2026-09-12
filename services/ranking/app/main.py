import json
import os
from pathlib import Path

import pandas as pd
import redis
from fastapi import FastAPI, HTTPException
from lightgbm import Booster
from app.features import build_online_features
from app.schemas import RankRequest, RankResponse

app = FastAPI(title="Vendora AI Ranking Service", version="1.0.0")
BASE_DIR = Path(__file__).resolve().parents[1]
MODEL_PATH = Path(os.getenv("MODEL_PATH", BASE_DIR / "models" / "model.lgb"))
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
_model: Booster | None = None
_redis_client = None


def get_model() -> Booster:
    global _model
    if _model is None:
        if not MODEL_PATH.exists():
            raise HTTPException(status_code=503, detail="Ranking model is not trained")
        _model = Booster(model_file=str(MODEL_PATH))
    return _model


def get_redis_client():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis.from_url(REDIS_URL, decode_responses=True)
    return _redis_client


def load_user_features(user_id: int) -> dict[str, float]:
    """Load JSON or hash-based user features, returning empty defaults on cache misses."""
    try:
        client = get_redis_client()
        key = f"user_features:{user_id}"
        raw = client.get(key)
        if raw:
            parsed = json.loads(raw)
            return {name: float(value) for name, value in parsed.items()}

        hash_features = client.hgetall(key)
        return {name: float(value) for name, value in hash_features.items()}
    except (redis.RedisError, ValueError, TypeError):
        return {}

@app.get("/")
def root():
    return {"message": "AI ranking service running"}


@app.get("/health")
def health():
    return {"status": "ok", "model_ready": MODEL_PATH.exists()}


@app.post("/rank")
def rank(request: RankRequest) -> RankResponse:
    model = get_model()
    user_features = load_user_features(request.user_id)
    features = build_online_features(request.user_id, request.product_ids, user_features)
    scores = model.predict(features)
    ranked = sorted(
        (
            {"product_id": product_id, "relevance_score": float(score)}
            for product_id, score in zip(request.product_ids, scores)
        ),
        key=lambda product: product["relevance_score"],
        reverse=True,
    )
    return RankResponse(products=ranked)