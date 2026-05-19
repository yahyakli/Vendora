from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    MINIO_ENDPOINT: str = "minio:9000"
    MINIO_ACCESS_KEY: str = "vendora_admin"
    MINIO_SECRET_KEY: str = "your-minio-secret"
    MINIO_SECURE: bool = False
    
    MINIO_BUCKET_PRODUCTS: str = "vendora-products"
    MINIO_BUCKET_AVATARS: str = "vendora-avatars"
    MINIO_BUCKET_DIGITAL: str = "vendora-digital"
    MINIO_BUCKET_INVOICES: str = "vendora-invoices"
    
    JWT_SECRET: str = "your-jwt-secret"
    AUTH_SERVICE_URL: str = "http://auth:8081"
    
    model_config = SettingsConfigDict(env_file="../../.env", extra="ignore")

settings = Settings()
