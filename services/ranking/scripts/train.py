"""Train a LightGBM LambdaRank model from synthetic interactions."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

import lightgbm as lgb
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.features import build_training_features  # noqa: E402
from scripts.generate_synthetic_data import generate_interactions  # noqa: E402


def load_interactions(path: Path, seed: int) -> pd.DataFrame:
    if path.exists():
        return pd.read_csv(path)

    path.parent.mkdir(parents=True, exist_ok=True)
    interactions = generate_interactions(1000, 500, 20000, seed)
    interactions.to_csv(path, index=False)
    return interactions


def train_model(interactions: pd.DataFrame, output: Path, seed: int) -> None:
    features = build_training_features(interactions)
    labels = interactions["event_weight"].astype(int)
    groups = interactions.groupby("user_id", sort=False).size().to_list()

    model = lgb.LGBMRanker(
        objective="lambdarank",
        metric="ndcg",
        ndcg_at=[5, 10],
        n_estimators=120,
        learning_rate=0.08,
        num_leaves=31,
        random_state=seed,
        verbosity=-1,
    )
    model.fit(features, labels, group=groups, feature_name=list(features.columns))

    output.parent.mkdir(parents=True, exist_ok=True)
    model.booster_.save_model(str(output))
    print(f"Saved LambdaRank model to {output}")
    print(f"Training rows: {len(features)}; user groups: {len(groups)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("data/interactions.csv"))
    parser.add_argument("--output", type=Path, default=Path("models/model.lgb"))
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    interactions = load_interactions(args.input, args.seed)
    train_model(interactions, args.output, args.seed)


if __name__ == "__main__":
    main()
