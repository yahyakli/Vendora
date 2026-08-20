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

// Product Public Routes
Route::get('/products', [\App\Http\Controllers\ProductController::class, 'index']);
Route::get('/products/search', [\App\Http\Controllers\ProductController::class, 'search']);
Route::get('/products/feed', [\App\Http\Controllers\ProductController::class, 'feed']);
Route::get('/products/vendor/{vendorId}', [\App\Http\Controllers\ProductController::class, 'byVendor']);
Route::get('/products/{id}', [\App\Http\Controllers\ProductController::class, 'show']);

// Category Routes
Route::get('/categories', [\App\Http\Controllers\CategoryController::class, 'index']);

// Vendor Public Routes
Route::get('/vendors', [\App\Http\Controllers\VendorController::class, 'index']);
Route::get('/vendors/{id}', [\App\Http\Controllers\VendorController::class, 'show']);

// Review Public Routes
Route::get('/products/{id}/reviews', [\App\Http\Controllers\ReviewController::class, 'index']);

Route::middleware('service.auth')->group(function () {
    // Categories Management
    Route::post('/categories', [\App\Http\Controllers\CategoryController::class, 'store']);
    Route::put('/categories/{id}', [\App\Http\Controllers\CategoryController::class, 'update']);
    Route::delete('/categories/{id}', [\App\Http\Controllers\CategoryController::class, 'destroy']);
    
    // Products Management
    Route::post('/products', [\App\Http\Controllers\ProductController::class, 'store']);
    Route::put('/products/{id}', [\App\Http\Controllers\ProductController::class, 'update']);
    Route::delete('/products/{id}', [\App\Http\Controllers\ProductController::class, 'destroy']);
    Route::post('/products/{id}/images', [\App\Http\Controllers\ProductController::class, 'uploadImage']);

    // Review Actions
    Route::post('/products/{id}/reviews', [\App\Http\Controllers\ReviewController::class, 'store']);
    Route::put('/reviews/{id}', [\App\Http\Controllers\ReviewController::class, 'update']);
    Route::delete('/reviews/{id}', [\App\Http\Controllers\ReviewController::class, 'destroy']);

    // Wishlist Actions
    Route::get('/wishlist', [\App\Http\Controllers\WishlistController::class, 'index']);
    Route::post('/wishlist/{productId}', [\App\Http\Controllers\WishlistController::class, 'store']);
    Route::delete('/wishlist/{productId}', [\App\Http\Controllers\WishlistController::class, 'destroy']);

    // Vendor Authenticated Actions
    Route::post('/vendors/apply', [\App\Http\Controllers\VendorController::class, 'apply']);
    Route::get('/vendors/me/profile', [\App\Http\Controllers\VendorController::class, 'me']);

    // Admin Only Actions (Requires ADMIN role)
    Route::middleware('role:ADMIN')->group(function () {
        // Vendor Moderation
        Route::get('/vendors/admin/applications', [\App\Http\Controllers\VendorController::class, 'applications']);
        Route::put('/vendors/{id}/approve', [\App\Http\Controllers\VendorController::class, 'approve']);
        Route::put('/vendors/{id}/suspend', [\App\Http\Controllers\VendorController::class, 'suspend']);

        // Product Moderation
        Route::get('/products/admin/all', [\App\Http\Controllers\ProductController::class, 'adminAll']);
        Route::put('/products/admin/{id}/flag', [\App\Http\Controllers\ProductController::class, 'flag']);
        Route::put('/products/admin/{id}/approve', [\App\Http\Controllers\ProductController::class, 'approve']);
    });
});
