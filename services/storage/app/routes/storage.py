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
    product_id: str = None,
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
    
    user_id = user.get("user_id")
    
    # Organize by user/vendor ID and optionally product ID
    if product_id:
        object_name = f"{user_id}/{product_id}/{file.filename}"
    else:
        object_name = f"{user_id}/{file.filename}"
    
    # Quota check (5GB default)
    # Note: In a real app, you might want to sum usage across all buckets or specific ones
    current_usage = minio_service.get_bucket_usage(bucket_name, prefix=f"{user_id}/")
    
    # Need to get file size for quota check and MinIO put_object
    # We can't easily get size without reading or seeking if it's a stream
    # FastAPI's UploadFile uses a SpooledTemporaryFile, we can seek to end to get size
    file.file.seek(0, 2)
    file_size = file.file.tell()
    file.file.seek(0)
    
    quota_limit = 5 * 1024 * 1024 * 1024  # 5 GB
    if current_usage + file_size > quota_limit:
        raise HTTPException(status_code=413, detail="Storage quota exceeded")
    
    result = minio_service.upload_file(
        bucket_name,
        object_name,
        file.file,
        file_size,
        file.content_type
    )
    
    return {
        "file_key": object_name, 
        "bucket": bucket_name,
        "size": file_size,
        "content_type": file.content_type
    }

@router.get("/url/{file_key:path}")
async def get_url(
    file_key: str, 
    bucket_type: str = "products",
    expires: int = 3600,
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
        
    try:
        url = minio_service.get_presigned_url(bucket_name, file_key, expires=expires)
        return {"url": url}
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"File not found: {str(e)}")

@router.delete("/{file_key:path}")
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
        
    try:
        minio_service.delete_file(bucket_name, file_key)
        return {"message": "File deleted successfully"}
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"Error deleting file: {str(e)}")

@router.get("/quota/{vendor_id}")
async def get_quota(
    vendor_id: str,
    user: dict = Depends(get_current_user)
):
    # This is a simplified version, should ideally check usage across all buckets for this vendor
    usage = minio_service.get_bucket_usage(settings.MINIO_BUCKET_PRODUCTS, prefix=f"{vendor_id}/")
    return {"vendor_id": vendor_id, "usage_bytes": usage, "limit_bytes": 5 * 1024 * 1024 * 1024}
