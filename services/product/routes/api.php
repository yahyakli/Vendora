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

// Product Routes
Route::get('/products', [\App\Http\Controllers\ProductController::class, 'index']);
Route::get('/products/{id}', [\App\Http\Controllers\ProductController::class, 'show']);

// Category Routes
Route::get('/categories', [\App\Http\Controllers\CategoryController::class, 'index']);

Route::middleware('service.auth')->group(function () {
    Route::post('/categories', [\App\Http\Controllers\CategoryController::class, 'store']);
    Route::put('/categories/{id}', [\App\Http\Controllers\CategoryController::class, 'update']);
    Route::delete('/categories/{id}', [\App\Http\Controllers\CategoryController::class, 'destroy']);
    
    Route::post('/products', [\App\Http\Controllers\ProductController::class, 'store']);
    Route::put('/products/{id}', [\App\Http\Controllers\ProductController::class, 'update']);
    Route::delete('/products/{id}', [\App\Http\Controllers\ProductController::class, 'destroy']);
    Route::post('/products/{id}/images', [\App\Http\Controllers\ProductController::class, 'uploadImage']);
});
