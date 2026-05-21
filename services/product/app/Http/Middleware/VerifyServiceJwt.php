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
            $authUrl = config('services.auth.url') . '/auth/validate';
            
            $response = Http::withToken($token)
                ->acceptJson()
                ->get($authUrl);

            if ($response->failed()) {
                return response()->json([
                    'message' => 'Unauthorized: Invalid token',
                    'error' => $response->json('message', 'Auth service error')
                ], 401);
            }

            $userData = $response->json();

            // Inject user info into the request attributes for easy access in controllers
            $request->attributes->add([
                'user_id' => $userData['user_id'],
                'user_role' => $userData['role']
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
