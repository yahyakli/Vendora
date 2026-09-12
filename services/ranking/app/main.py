import json
import os
import secrets
from pathlib import Path

import pandas as pd
import redis
from fastapi import FastAPI, Header, HTTPException
from lightgbm import Booster
from app.features import build_online_features
from app.schemas import RankRequest, RankResponse
from scripts.train import load_interactions, train_model

app = FastAPI(title="Vendora AI Ranking Service", version="1.0.0")
BASE_DIR = Path(__file__).resolve().parents[1]
MODEL_PATH = Path(os.getenv("MODEL_PATH", BASE_DIR / "models" / "model.lgb"))
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
DATA_PATH = Path(os.getenv("DATA_PATH", BASE_DIR / "data" / "interactions.csv"))
RETRAIN_ADMIN_TOKEN = os.getenv("RANKING_ADMIN_TOKEN")
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


@app.get("/model/stats")
def model_stats():
    if not MODEL_PATH.exists():
        return {"model_ready": False, "model_path": str(MODEL_PATH)}

    model = get_model()
    return {
        "model_ready": True,
        "model_path": str(MODEL_PATH),
        "model_size_bytes": MODEL_PATH.stat().st_size,
        "last_modified": MODEL_PATH.stat().st_mtime,
        "num_iterations": model.current_iteration(),
        "num_features": model.num_feature(),
        "feature_names": model.feature_name(),
    }


@app.post("/retrain")
def retrain(x_admin_token: str | None = Header(default=None, alias="X-Admin-Token")):
    if not RETRAIN_ADMIN_TOKEN:
        raise HTTPException(status_code=503, detail="Retraining admin token is not configured")
    if not x_admin_token or not secrets.compare_digest(x_admin_token, RETRAIN_ADMIN_TOKEN):
        raise HTTPException(status_code=403, detail="Admin authorization required")

    global _model
    interactions = load_interactions(DATA_PATH, seed=42)
    training_stats = train_model(interactions, MODEL_PATH, seed=42)
    _model = Booster(model_file=str(MODEL_PATH))
    return {"status": "retrained", **training_stats, "model_path": str(MODEL_PATH)}


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