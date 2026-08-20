from fastapi import Request, HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import httpx
from app.config.config import settings

security = HTTPBearer()

async def get_current_user(auth: HTTPAuthorizationCredentials = Security(security)):
    token = auth.credentials
    auth_base = settings.AUTH_SERVICE_URL.rstrip('/')
    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(
                f"{auth_base}/api/auth/validate",
                headers={"Authorization": f"Bearer {token}"}
            )
            if response.status_code == 404:
                response = await client.get(
                    f"{auth_base}/auth/validate",
                    headers={"Authorization": f"Bearer {token}"}
                )
            if response.status_code != 200:
                raise HTTPException(status_code=401, detail="Invalid or expired token")
            
            data = response.json()
            user_id = data.get("user_id") or data.get("userId")
            role = data.get("role")
            roles = data.get("roles") or ([role] if role else ["BUYER"])
            primary_role = role if isinstance(role, str) else (roles[0] if roles else "BUYER")

            return {
                "user_id": str(user_id) if user_id is not None else "",
                "userId": str(user_id) if user_id is not None else "",
                "role": primary_role,
                "roles": roles,
                "email": data.get("email", ""),
                "valid": data.get("valid", True)
            }
        except httpx.RequestError:
            raise HTTPException(status_code=503, detail="Auth service unavailable")

async def admin_only(user: dict = Security(get_current_user)):
    user_roles = user.get("roles", [])
    user_role = user.get("role", "")
    if "ADMIN" not in user_roles and "ROLE_ADMIN" not in user_roles and user_role not in ["ADMIN", "ROLE_ADMIN"]:
        raise HTTPException(status_code=403, detail="Admin access required")
    return user
