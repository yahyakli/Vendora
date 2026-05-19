from fastapi import FastAPI
from app.routes import storage

app = FastAPI(title="Vendora Storage Service")

app.include_router(storage.router)

@app.get("/")
def root():
    return {"message": "Vendora Storage Service running 🚀"}
