"""Feature construction shared by training and online ranking."""

from __future__ import annotations

from typing import Mapping

import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    "user_id",
    "product_id",
    "product_bucket",
    "user_activity",
    "user_purchase_rate",
    "product_popularity",
    "product_purchase_rate",
]


def build_training_features(interactions: pd.DataFrame) -> pd.DataFrame:
    """Build numerical ranking features from historical interactions."""
    user_stats = interactions.groupby("user_id").agg(
        user_activity=("product_id", "size"),
        user_purchase_rate=("event", lambda values: (values == "purchase").mean()),
    )
    product_stats = interactions.groupby("product_id").agg(
        product_popularity=("user_id", "size"),
        product_purchase_rate=("event", lambda values: (values == "purchase").mean()),
    )

    features = interactions[["user_id", "product_id"]].copy()
    features["product_bucket"] = features["product_id"] % 20
    features = features.join(user_stats, on="user_id")
    features = features.join(product_stats, on="product_id")
    return features[FEATURE_COLUMNS].astype(float)


def build_online_features(
    user_id: int,
    product_ids: list[int],
    user_features: Mapping[str, float] | None = None,
) -> pd.DataFrame:
    """Build model features for candidate products and one user."""
    user_features = user_features or {}
    user_activity = float(user_features.get("user_activity", 0.0))
    user_purchase_rate = float(user_features.get("user_purchase_rate", 0.0))
    product_popularity = float(user_features.get("product_popularity", 0.0))
    product_purchase_rate = float(user_features.get("product_purchase_rate", 0.0))

    return pd.DataFrame(
        [
            {
                "user_id": user_id,
                "product_id": product_id,
                "product_bucket": product_id % 20,
                "user_activity": user_activity,
                "user_purchase_rate": user_purchase_rate,
                "product_popularity": product_popularity,
                "product_purchase_rate": product_purchase_rate,
            }
            for product_id in product_ids
        ],
        columns=FEATURE_COLUMNS,
    ).astype(float)
