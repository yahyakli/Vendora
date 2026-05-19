from minio import Minio
from app.config.config import settings
import io

class MinioService:
    def __init__(self):
        self.client = Minio(
            settings.MINIO_ENDPOINT,
            access_key=settings.MINIO_ACCESS_KEY,
            secret_key=settings.MINIO_SECRET_KEY,
            secure=settings.MINIO_SECURE
        )

    def upload_file(self, bucket_name: str, object_name: str, data: io.BytesIO, length: int, content_type: str):
        if not self.client.bucket_exists(bucket_name):
            self.client.make_bucket(bucket_name)
        
        return self.client.put_object(
            bucket_name, object_name, data, length, content_type=content_type
        )

    def get_presigned_url(self, bucket_name: str, object_name: str, expires: int = 3600):
        return self.client.presigned_get_object(bucket_name, object_name, expires=expires)

    def delete_file(self, bucket_name: str, object_name: str):
        self.client.remove_object(bucket_name, object_name)

    def get_bucket_usage(self, bucket_name: str, prefix: str = ""):
        objects = self.client.list_objects(bucket_name, prefix=prefix, recursive=True)
        total_size = sum(obj.size for obj in objects)
        return total_size

minio_service = MinioService()
