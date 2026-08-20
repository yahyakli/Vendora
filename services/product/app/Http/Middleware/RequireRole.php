<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireRole
{
    /**
     * Handle an incoming request.
     *
     * @param  \Closure(\Illuminate\Http\Request): (\Symfony\Component\HttpFoundation\Response)  $next
     * @param  string  ...$roles
     */
    public function handle(Request $request, Closure $next, ...$roles): Response
    {
        $userRole = strtoupper((string)$request->attributes->get('user_role', ''));
        $userRoles = array_map('strtoupper', (array)$request->attributes->get('user_roles', []));

        if (!empty($userRole) && !in_array($userRole, $userRoles, true)) {
            $userRoles[] = $userRole;
        }

        // Normalize roles requested, e.g. ['ADMIN', 'SELLER']
        $allowedRoles = [];
        foreach ($roles as $roleGroup) {
            foreach (explode(',', $roleGroup) as $r) {
                $trimmed = strtoupper(trim($r));
                $allowedRoles[] = $trimmed;
                // Also allow matching with or without ROLE_ prefix
                if (str_starts_with($trimmed, 'ROLE_')) {
                    $allowedRoles[] = substr($trimmed, 5);
                } else {
                    $allowedRoles[] = 'ROLE_' . $trimmed;
                }
            }
        }

        $hasAccess = false;
        foreach ($userRoles as $uRole) {
            if (in_array($uRole, $allowedRoles, true)) {
                $hasAccess = true;
                break;
            }
        }

        if (!$hasAccess) {
            return response()->json([
                'message' => 'Forbidden: You do not have permission to access this resource',
                'required_roles' => $roles,
                'current_role' => $userRole
            ], 403);
        }

        return $next($request);
    }
}
