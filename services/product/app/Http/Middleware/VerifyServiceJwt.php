<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Symfony\Component\HttpFoundation\Response;

class VerifyServiceJwt
{
    /**
     * Handle an incoming request.
     *
     * @param  \Closure(\Illuminate\Http\Request): (\Symfony\Component\HttpFoundation\Response)  $next
     */
    public function handle(Request $request, Closure $next): Response
    {
        $token = $request->bearerToken();

        if (!$token) {
            return response()->json(['message' => 'Unauthorized: No token provided'], 401);
        }

        try {
            $authBase = rtrim(config('services.auth.url', 'http://auth:8081'), '/');
            $authUrl = $authBase . '/api/auth/validate';
            
            $response = Http::withToken($token)
                ->acceptJson()
                ->get($authUrl);

            // Fallback if auth service is listening directly on /auth/validate
            if ($response->status() === 404) {
                $response = Http::withToken($token)
                    ->acceptJson()
                    ->get($authBase . '/auth/validate');
            }

            if ($response->failed()) {
                return response()->json([
                    'message' => 'Unauthorized: Invalid token',
                    'error' => $response->json('message', 'Auth service validation failed')
                ], 401);
            }

            $userData = $response->json();
            $userId = $userData['user_id'] ?? $userData['userId'] ?? null;
            
            // Extract roles safely
            $roles = $userData['roles'] ?? [];
            if (empty($roles) && isset($userData['role'])) {
                $roles = is_array($userData['role']) ? $userData['role'] : [$userData['role']];
            }
            
            $primaryRole = isset($userData['role']) && is_string($userData['role'])
                ? $userData['role']
                : (!empty($roles) ? $roles[0] : 'BUYER');

            // Inject user info into the request attributes for easy access in controllers
            $request->attributes->add([
                'user_id' => $userId,
                'userId' => $userId,
                'user_role' => $primaryRole,
                'user_roles' => $roles,
                'email' => $userData['email'] ?? null,
            ]);

            return $next($request);

        } catch (\Exception $e) {
            return response()->json([
                'message' => 'Service Unavailable: Auth service unreachable',
                'error' => $e->getMessage()
            ], 503);
        }
    }
}
