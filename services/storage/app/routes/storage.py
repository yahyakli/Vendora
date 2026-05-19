from fastapi import APIRouter, UploadFile, File, HTTPException, Depends
from app.services.minio_service import minio_service
from app.config.config import settings
from app.dependencies import get_current_user
import io

router = APIRouter(prefix="/storage", tags=["storage"])

@router.post("/upload")
async def upload_file(
    file: UploadFile = File(...), 
    bucket_type: str = "products",
    user: dict = Depends(get_current_user)
):
    # Map bucket_type to actual bucket name
    bucket_map = {
        "products": settings.MINIO_BUCKET_PRODUCTS,
        "avatars": settings.MINIO_BUCKET_AVATARS,
        "digital": settings.MINIO_BUCKET_DIGITAL,
        "invoices": settings.MINIO_BUCKET_INVOICES
    }
    
    bucket_name = bucket_map.get(bucket_type)
    if not bucket_name:
        raise HTTPException(status_code=400, detail="Invalid bucket type")
    
    content = await file.read()
    file_size = len(content)
    
    # TODO: Implement vendor quota check
    
    result = minio_service.upload_file(
        bucket_name,
        file.filename,
        io.BytesIO(content),
        file_size,
        file.content_type
    )
    
    return {"file_key": file.filename, "bucket": bucket_name}

@router.get("/url/{file_key}")
async def get_url(
    file_key: str, 
    bucket_type: str = "products",
    user: dict = Depends(get_current_user)
):
    bucket_map = {
        "products": settings.MINIO_BUCKET_PRODUCTS,
        "avatars": settings.MINIO_BUCKET_AVATARS,
        "digital": settings.MINIO_BUCKET_DIGITAL,
        "invoices": settings.MINIO_BUCKET_INVOICES
    }
    
    bucket_name = bucket_map.get(bucket_type)
    if not bucket_name:
        raise HTTPException(status_code=400, detail="Invalid bucket type")
        
    url = minio_service.get_presigned_url(bucket_name, file_key)
    return {"url": url}

@router.delete("/{file_key}")
async def delete_file(
    file_key: str, 
    bucket_type: str = "products",
    user: dict = Depends(get_current_user)
):
    bucket_map = {
        "products": settings.MINIO_BUCKET_PRODUCTS,
        "avatars": settings.MINIO_BUCKET_AVATARS,
        "digital": settings.MINIO_BUCKET_DIGITAL,
        "invoices": settings.MINIO_BUCKET_INVOICES
    }
    
    bucket_name = bucket_map.get(bucket_type)
    if not bucket_name:
        raise HTTPException(status_code=400, detail="Invalid bucket type")
        
    minio_service.delete_file(bucket_name, file_key)
    return {"message": "File deleted successfully"}

@router.get("/quota/{vendor_id}")
async def get_quota(
    vendor_id: str,
    user: dict = Depends(get_current_user)
):
    # This is a simplified version, should ideally check usage across all buckets for this vendor
    usage = minio_service.get_bucket_usage(settings.MINIO_BUCKET_PRODUCTS, prefix=f"{vendor_id}/")
    return {"vendor_id": vendor_id, "usage_bytes": usage, "limit_bytes": 5 * 1024 * 1024 * 1024}
