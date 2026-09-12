"""Generate synthetic user-product interactions for ranking experiments."""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd


EVENT_WEIGHTS = {
    "view": 1.0,
    "click": 3.0,
    "purchase": 10.0,
}


def generate_interactions(
    users: int,
    products: int,
    interactions: int,
    seed: int,
) -> pd.DataFrame:
    """Generate interactions with correlated user preference and product quality."""
    if users < 1 or products < 1 or interactions < 1:
        raise ValueError("users, products, and interactions must be positive")

    rng = np.random.default_rng(seed)
    categories = rng.integers(0, max(4, min(20, products)), size=products)
    product_quality = rng.beta(2.5, 2.0, size=products)
    user_preference = rng.normal(0.0, 1.0, size=(users, int(categories.max()) + 1))

    user_ids = rng.integers(1, users + 1, size=interactions)
    product_indices = rng.integers(0, products, size=interactions)
    category_ids = categories[product_indices]
    affinity = user_preference[user_ids - 1, category_ids]
    quality = product_quality[product_indices]

    engagement = 1 / (1 + np.exp(-(affinity + (quality - 0.5) * 3)))
    event_roll = rng.random(interactions)
    event = np.select(
        [event_roll < engagement * 0.58, event_roll < engagement * 0.82],
        ["purchase", "click"],
        default="view",
    )

    now = pd.Timestamp.utcnow().floor("s")
    timestamps = now - pd.to_timedelta(
        rng.integers(0, 90 * 24 * 60 * 60, size=interactions), unit="s"
    )
    frame = pd.DataFrame(
        {
            "user_id": user_ids,
            "product_id": product_indices + 1,
            "category_id": category_ids + 1,
            "event": event,
            "event_weight": [EVENT_WEIGHTS[item] for item in event],
            "timestamp": timestamps,
        }
    )
    return frame.sort_values("timestamp").reset_index(drop=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--users", type=int, default=1000)
    parser.add_argument("--products", type=int, default=500)
    parser.add_argument("--interactions", type=int, default=10000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output", type=Path, default=Path("data/interactions.csv"))
    args = parser.parse_args()

    interactions = generate_interactions(
        users=args.users,
        products=args.products,
        interactions=args.interactions,
        seed=args.seed,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    interactions.to_csv(args.output, index=False)
    print(f"Wrote {len(interactions)} interactions to {args.output}")
    print(interactions["event"].value_counts().sort_index().to_string())


if __name__ == "__main__":
    main()
