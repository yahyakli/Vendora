from pydantic import BaseModel, Field


class RankRequest(BaseModel):
    user_id: int = Field(gt=0)
    product_ids: list[int] = Field(min_length=1, max_length=1000)


class RankedProduct(BaseModel):
    product_id: int
    relevance_score: float


class RankResponse(BaseModel):
    products: list[RankedProduct]
