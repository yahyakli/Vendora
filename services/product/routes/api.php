<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API Routes
|--------------------------------------------------------------------------
|
| Here is where you can register API routes for your application. These
| routes are loaded by the RouteServiceProvider and all of them will
| be assigned to the "api" middleware group. Make something great!
|
*/

Route::middleware('auth:sanctum')->get('/user', function (Request $request) {
    return $request->user();
});

Route::middleware('service.auth')->get('/test-auth', function (Request $request) {
    return response()->json([
        'message' => 'Successfully authenticated via Microservice Auth',
        'user_id' => $request->attributes->get('user_id'),
        'role' => $request->attributes->get('user_role'),
    ]);
});
