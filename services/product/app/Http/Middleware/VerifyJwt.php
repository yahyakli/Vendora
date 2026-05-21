<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use GuzzleHttp\Client;
use Symfony\Component\HttpFoundation\Response;

class VerifyJwt
{
    /**
     * Handle an incoming request.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure  $next
     * @return mixed
     */
    public function handle(Request $request, Closure $next)
    {
        $authHeader = $request->header('Authorization');
        if (!$authHeader || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
            return response()->json(['error' => 'Authorization token missing'], 401);
        }
        $token = $matches[1];

        // Call Auth service to validate token
        $client = new Client([
            'base_uri' => env('AUTH_SERVICE_URL', 'http://localhost:8081'),
            'timeout'  => 2.0,
        ]);
        try {
            $response = $client->post('/auth/validate', [
                'json' => ['token' => $token],
                // If Auth service expects Bearer header, could also send it directly
                // 'headers' => ['Authorization' => $authHeader]
            ]);
            $status = $response->getStatusCode();
            if ($status !== 200) {
                return response()->json(['error' => 'Invalid token'], 401);
            }
            $payload = json_decode($response->getBody()->getContents(), true);
            // Optionally attach user info to request
            $request->merge(['auth_user' => $payload['user'] ?? null]);
        } catch (\Exception $e) {
            return response()->json(['error' => 'Auth service unavailable', 'detail' => $e->getMessage()], 503);
        }
        return $next($request);
    }
}

?>
