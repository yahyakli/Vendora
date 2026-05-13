import os
from minio import Minio
from minio.error import S3Error

def init_buckets():
    endpoint = os.getenv("MINIO_ENDPOINT", "localhost:9000")
    access_key = os.getenv("MINIO_ROOT_USER", "vendora_admin")
    secret_key = os.getenv("MINIO_ROOT_PASSWORD", "your-minio-secret")
    
    # Initialize MinIO client
    client = Minio(
        endpoint,
        access_key=access_key,
        secret_key=secret_key,
        secure=False
    )

    buckets = [
        "vendora-products",
        "vendora-avatars",
        "vendora-digital",
        "vendora-invoices"
    ]

    for bucket in buckets:
        try:
            if not client.bucket_exists(bucket):
                client.make_bucket(bucket)
                print(f"Bucket '{bucket}' created successfully.")
            else:
                print(f"Bucket '{bucket}' already exists.")
        except S3Error as e:
            print(f"Error occurred: {e}")

if __name__ == "__main__":
    init_buckets()
